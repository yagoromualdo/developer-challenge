package com.kingspan.purchase.shared.security;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;

/** Emite o JWT de acesso e reconstrói o {@link Actor} a partir das claims, nos dois sentidos com os mesmos nomes. */
@Service
public class JwtService {

    static final String ROLE_CLAIM = "role";
    static final String COST_CENTER_CLAIM = "costCenterId";
    static final String APPROVER_LEVEL_CLAIM = "approverLevel";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(Actor actor) {
        // Claims de data no JWT têm precisão de segundos; truncar faz o expiresAt da resposta bater com o "exp".
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(actor.userId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLE_CLAIM, actor.role().name());
        // Claims opcionais são omitidas (e não enviadas como null): ADMIN global não tem centro de custo.
        if (actor.costCenterId() != null) {
            claims.claim(COST_CENTER_CLAIM, actor.costCenterId());
        }
        if (actor.approverLevel() != null) {
            claims.claim(APPROVER_LEVEL_CLAIM, actor.approverLevel().name());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    /** Confia nas claims sem consultar o banco: mudanças de papel valem a partir do próximo login (TTL curto). */
    public Actor actorFrom(Jwt jwt) {
        try {
            Number costCenterId = jwt.getClaim(COST_CENTER_CLAIM);
            String approverLevel = jwt.getClaimAsString(APPROVER_LEVEL_CLAIM);
            return new Actor(
                    Long.valueOf(jwt.getSubject()),
                    Role.valueOf(jwt.getClaimAsString(ROLE_CLAIM)),
                    costCenterId == null ? null : costCenterId.longValue(),
                    approverLevel == null ? null : ApproverLevel.valueOf(approverLevel));
        } catch (RuntimeException ex) {
            // Assinatura válida com claims incoerentes: trata como token inválido (401), não como erro interno.
            throw new InvalidBearerTokenException("Claims do token de acesso inválidas", ex);
        }
    }
}
