package com.kingspan.purchase.user.application;

import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.security.IssuedToken;
import com.kingspan.purchase.shared.security.JwtService;
import com.kingspan.purchase.user.domain.UserProfile;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository users, UserService userService, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public AuthenticatedSession login(String email, String password) {
        Optional<UserEntity> user = users.findByEmail(UserService.normalizeEmail(email));
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
        return userService.create(name, email, password, new UserProfile(Role.SOLICITANTE, null, costCenterId));
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
}
