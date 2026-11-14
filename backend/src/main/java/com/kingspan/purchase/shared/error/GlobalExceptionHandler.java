package com.kingspan.purchase.shared.error;

import com.kingspan.purchase.shared.web.RequestId;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte toda exceção da camada web em Problem Details (RFC 9457) com {@code code}, {@code requestId} e,
 * para 400 de validação, {@code errors[]}. Exceções padrão do Spring MVC são tratadas pela superclasse e
 * enriquecidas em {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);
    private static final URI BLANK_TYPE = URI.create("about:blank");

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<Object> handleBusinessRule(BusinessRuleException ex, WebRequest request) {
        ProblemDetail problem = problem(UNPROCESSABLE, "Regra de negócio violada", ex.getMessage(), ex.code());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), UNPROCESSABLE, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "Conflito de concorrência",
                "O recurso foi alterado por outra operação. Recarregue os dados e tente novamente.",
                ErrorCode.CONCURRENT_MODIFICATION);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Erro inesperado ao processar a requisição", ex);
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno",
                "Ocorreu um erro inesperado. Informe o requestId ao suporte.",
                ErrorCode.INTERNAL_ERROR);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> violations.add(toViolation(error)));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> violations.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage())));
        return validationProblem(ex, violations, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(error -> violations.add(toViolation(error)));
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(error -> violations.add(new FieldViolation(parameter, error.getDefaultMessage())));
            }
        }
        return validationProblem(ex, violations, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(
                status,
                "Requisição malformada",
                "O corpo da requisição não pôde ser lido. Verifique o JSON e os tipos dos campos.",
                ErrorCode.MALFORMED_REQUEST);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Ponto único de enriquecimento: todo Problem Details sai com code, type, instance e requestId. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // A superclasse cria o ProblemDetail quando body é null (ex.: 404, 405), então enriquecemos depois.
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            enrich(problem, request);
        }
        return response;
    }

    private ResponseEntity<Object> validationProblem(
            Exception ex,
            List<FieldViolation> violations,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        violations.sort(Comparator.comparing(FieldViolation::field));
        ProblemDetail problem = problem(
                status,
                "Dados inválidos",
                "A requisição contém campos inválidos. Veja errors[] para os detalhes.",
                ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", violations);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String title, String detail, ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        return problem;
    }

    private static void enrich(ProblemDetail problem, WebRequest request) {
        ErrorCode code = codeOf(problem);
        problem.setProperty("code", code.name());
        if (problem.getType() == null || BLANK_TYPE.equals(problem.getType())) {
            problem.setType(URI.create("urn:problem-type:" + code.typeSlug()));
        }
        if (problem.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
        problem.setProperty("requestId", RequestId.current());
    }

    private static ErrorCode codeOf(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        Object code = properties == null ? null : properties.get("code");
        return code == null ? ErrorCode.fromStatus(problem.getStatus()) : ErrorCode.valueOf(code.toString());
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }
}
