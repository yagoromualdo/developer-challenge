package com.kingspan.purchase.user.infra;

import com.kingspan.purchase.shared.domain.ApproverLevel;
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

/** O centro de custo é referenciado só pelo id: uma feature não importa a {@code infra} de outra. */
@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_level")
    private ApproverLevel approverLevel;

    @Column(name = "cost_center_id")
    private Long costCenterId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {}

    public UserEntity(
            String name,
            String email,
            String passwordHash,
            Role role,
            ApproverLevel approverLevel,
            Long costCenterId,
            Instant now) {
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.approverLevel = approverLevel;
        this.costCenterId = costCenterId;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void changeProfile(Role role, ApproverLevel approverLevel, Long costCenterId, Instant now) {
        this.role = Objects.requireNonNull(role, "role");
        this.approverLevel = approverLevel;
        this.costCenterId = costCenterId;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public ApproverLevel getApproverLevel() {
        return approverLevel;
    }

    public Long getCostCenterId() {
        return costCenterId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
