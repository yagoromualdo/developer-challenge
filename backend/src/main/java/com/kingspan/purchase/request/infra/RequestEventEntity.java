package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.HistoryAction;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.shared.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/** Evento append-only: {@code @Immutable} impede UPDATE pelo Hibernate, e o trigger da V1 barra no banco. */
@Entity
@Immutable
@Table(name = "request_event")
public class RequestEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false)
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HistoryAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private RequestStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_level")
    private StepLevel stepLevel;

    @Column
    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "http_request_id")
    private String httpRequestId;

    protected RequestEventEntity() {}

    public RequestEventEntity(
            Long requestId,
            int versionNumber,
            Long actorId,
            Role actorRole,
            HistoryAction action,
            RequestStatus fromStatus,
            RequestStatus toStatus,
            StepLevel stepLevel,
            String comment,
            Instant occurredAt,
            String httpRequestId) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.versionNumber = versionNumber;
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.actorRole = Objects.requireNonNull(actorRole, "actorRole");
        this.action = Objects.requireNonNull(action, "action");
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.stepLevel = stepLevel;
        this.comment = comment;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.httpRequestId = httpRequestId;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public Long getActorId() {
        return actorId;
    }

    public Role getActorRole() {
        return actorRole;
    }

    public HistoryAction getAction() {
        return action;
    }

    public RequestStatus getFromStatus() {
        return fromStatus;
    }

    public RequestStatus getToStatus() {
        return toStatus;
    }

    public StepLevel getStepLevel() {
        return stepLevel;
    }

    public String getComment() {
        return comment;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getHttpRequestId() {
        return httpRequestId;
    }
}
