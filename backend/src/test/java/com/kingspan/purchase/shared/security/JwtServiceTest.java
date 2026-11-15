package com.kingspan.purchase.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class JwtServiceTest {

    private static final String SECRET = "segredo-de-teste-com-pelo-menos-32-bytes!";
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private final JwtService service = new JwtService(
            new SecurityConfig().jwtEncoder(new JwtProperties(SECRET, Duration.ofMinutes(15))),
            new JwtProperties(SECRET, Duration.ofMinutes(15)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void deveEmitirTokenComExpiracaoPeloTtlConfigurado() {
        IssuedToken token = service.issue(new Actor(7L, Role.APROVADOR, 3L, ApproverLevel.NIVEL_1));

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(token.value().split("\\.")).hasSize(3);
    }

    @Test
    void deveReconstruirOActorAPartirDasClaims() {
        Jwt jwt = jwt("7", "APROVADOR", 3L, "NIVEL_2");

        assertThat(service.actorFrom(jwt)).isEqualTo(new Actor(7L, Role.APROVADOR, 3L, ApproverLevel.NIVEL_2));
    }

    @Test
    void deveAceitarAdminGlobalSemCentroDeCustoNemNivel() {
        assertThat(service.actorFrom(jwt("1", "ADMIN", null, null))).isEqualTo(new Actor(1L, Role.ADMIN, null, null));
    }

    @Test
    void deveTratarClaimsIncoerentesComoTokenInvalido() {
        assertThatThrownBy(() -> service.actorFrom(jwt("7", "SUPERUSUARIO", 3L, null)))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> service.actorFrom(jwt("7", "APROVADOR", 3L, null)))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> service.actorFrom(jwt("abc", "SOLICITANTE", 3L, null)))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void deveRecusarSegredoComMenosDe32Bytes() {
        assertThatThrownBy(() -> new JwtProperties("curto", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void deveRecusarTtlNaoPositivo() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Jwt jwt(String subject, String role, Long costCenterId, String approverLevel) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("role", role)
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900));
        if (costCenterId != null) {
            builder.claim("costCenterId", costCenterId);
        }
        if (approverLevel != null) {
            builder.claim("approverLevel", approverLevel);
        }
        return builder.build();
    }
}
