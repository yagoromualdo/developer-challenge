package com.kingspan.purchase.user.application;

import com.kingspan.purchase.costcenter.application.CostCenterService;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.kingspan.purchase.shared.security.IssuedToken;
import com.kingspan.purchase.shared.security.JwtService;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // Limite do bcrypt: bytes além do 72º seriam ignorados, e o encoder do Spring rejeita a senha.
    public static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final CostCenterService costCenters;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository users,
            CostCenterService costCenters,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock) {
        this.users = users;
        this.costCenters = costCenters;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public AuthenticatedSession login(String email, String password) {
        Optional<UserEntity> user = users.findByEmail(normalizeEmail(email));
        // Com e-mail inexistente o hash é conferido mesmo assim: o tempo de resposta não denuncia quem existe.
        String hash = user.map(UserEntity::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = fitsBcrypt(password) && passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !matches) {
            throw new BadCredentialsException("Credenciais inválidas");
        }
        UserAccount account = UserAccount.from(user.get());
        IssuedToken token = jwtService.issue(account.toActor());
        return new AuthenticatedSession(token.value(), token.expiresAt(), account);
    }

    /** Cadastro público: o papel é sempre SOLICITANTE, independentemente do que o cliente enviar. */
    @Transactional
    public UserAccount register(String name, String email, String password, Long costCenterId) {
        String normalizedEmail = normalizeEmail(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw emailAlreadyRegistered();
        }
        if (!costCenters.exists(costCenterId)) {
            throw new BusinessRuleException(
                    ErrorCode.COST_CENTER_NOT_FOUND,
                    "Não é possível cadastrar: o centro de custo " + costCenterId + " não existe.");
        }
        UserEntity user = new UserEntity(
                name.trim(),
                normalizedEmail,
                passwordEncoder.encode(password),
                Role.SOLICITANTE,
                null,
                costCenterId,
                clock.instant());
        try {
            return UserAccount.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            // Dois cadastros simultâneos com o mesmo e-mail: o índice único decide e o perdedor recebe o 422.
            throw emailAlreadyRegistered();
        }
    }

    @Transactional(readOnly = true)
    public UserAccount currentUser(Long userId) {
        return users.findById(userId)
                .map(UserAccount::from)
                .orElseThrow(() -> new InsufficientAuthenticationException("Usuário do token não existe mais"));
    }

    private static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessRuleException emailAlreadyRegistered() {
        return new BusinessRuleException(
                ErrorCode.EMAIL_ALREADY_REGISTERED, "Não é possível cadastrar: este e-mail já está em uso.");
    }
}
