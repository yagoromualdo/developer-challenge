package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.domain.StepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "approval_step")
public class ApprovalStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, updatable = false)
    private RequestVersionEntity version;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private StepLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status;

    // Id do usuário, sem @ManyToOne: a feature request não importa a infra de user.
    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column
    private String comment;

    protected ApprovalStepEntity() {}

    ApprovalStepEntity(RequestVersionEntity version, int stepOrder, StepLevel level) {
        this.version = Objects.requireNonNull(version, "version");
        this.stepOrder = stepOrder;
        this.level = Objects.requireNonNull(level, "level");
        this.status = StepStatus.PENDING;
    }

    /** Grava a decisão já validada pela {@code ActionPolicy}. */
    public void decide(StepStatus outcome, Long decidedBy, Instant decidedAt, String comment) {
        this.status = Objects.requireNonNull(outcome, "outcome");
        this.decidedBy = Objects.requireNonNull(decidedBy, "decidedBy");
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public RequestVersionEntity getVersion() {
        return version;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public StepLevel getLevel() {
        return level;
    }

    public StepStatus getStatus() {
        return status;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getComment() {
        return comment;
    }
}
