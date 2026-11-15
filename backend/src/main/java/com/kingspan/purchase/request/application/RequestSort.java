package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.infra.RequestSortField;
import com.kingspan.purchase.shared.error.InvalidInputException;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;

/** Ordenação da listagem no formato {@code campo[,asc|desc]} (padrão {@code asc}, como no Spring Data). */
public record RequestSort(RequestSortField field, Sort.Direction direction) {

    public static final String DEFAULT = "createdAt,desc";
    private static final String PARAMETER = "sort";

    public RequestSort {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(direction, "direction");
    }

    /** Campo fora da whitelist ou direção desconhecida → 400 com o parâmetro {@code sort} em {@code errors[]}. */
    public static RequestSort parse(String value) {
        String[] parts = Objects.requireNonNull(value, "value").split(",", -1);
        if (parts.length > 2) {
            throw new InvalidInputException(PARAMETER, "use o formato campo[,asc|desc], ex.: " + DEFAULT);
        }
        String fieldName = parts[0].trim();
        RequestSortField field = RequestSortField.fromApiName(fieldName)
                .orElseThrow(() -> new InvalidInputException(
                        PARAMETER,
                        "campo de ordenação não permitido: '" + fieldName + "'. Use um destes: " + allowedFields()));
        return new RequestSort(field, parts.length == 2 ? direction(parts[1].trim()) : Sort.Direction.ASC);
    }

    private static Sort.Direction direction(String value) {
        return Sort.Direction.fromOptionalString(value)
                .orElseThrow(() -> new InvalidInputException(
                        PARAMETER, "direção de ordenação inválida: '" + value + "'. Use asc ou desc"));
    }

    private static String allowedFields() {
        return Arrays.stream(RequestSortField.values())
                .map(RequestSortField::apiName)
                .collect(Collectors.joining(", "));
    }
}
