package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.RequestStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Raiz do agregado da solicitação. Não valida transições nem regras: quem decide é o domínio, e a entidade só grava o
 * resultado.
 */
@Entity
@Table(name = "purchase_request")
public class PurchaseRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "cost_center_id", nullable = false, updatable = false)
    private Long costCenterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(name = "current_version", nullable = false)
    private int currentVersionNumber;

    // É o lockVersion da API (ADR 001); distinto do número da versão de negócio (v1, v2...).
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
    @OrderBy("versionNumber")
    private List<RequestVersionEntity> versions = new ArrayList<>();

    protected PurchaseRequestEntity() {}

    public PurchaseRequestEntity(
            Long ownerId, Long costCenterId, RequestStatus status, RequestVersionEntity firstVersion, Instant now) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.costCenterId = Objects.requireNonNull(costCenterId, "costCenterId");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(now, "now");
        addVersion(firstVersion, now);
    }

    /** Numera a versão como a próxima e a torna a atual; as anteriores ficam preservadas (ADR 002). */
    public void addVersion(RequestVersionEntity version, Instant now) {
        Objects.requireNonNull(version, "version");
        int number = currentVersionNumber + 1;
        version.attachTo(this, number);
        versions.add(version);
        currentVersionNumber = number;
        touch(now);
    }

    public void moveTo(RequestStatus status, Instant now) {
        this.status = Objects.requireNonNull(status, "status");
        touch(now);
    }

    /**
     * Toda alteração no agregado (itens, etapas) passa por aqui: mudar a linha da raiz é o que incrementa o
     * {@code @Version} e faz o 409 valer também para quem só alterou filhos.
     */
    public void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public RequestVersionEntity getCurrentVersion() {
        return versions.stream()
                .filter(version -> version.getVersionNumber() == currentVersionNumber)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Solicitação " + id + " sem a versão atual " + currentVersionNumber));
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getCostCenterId() {
        return costCenterId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public int getCurrentVersionNumber() {
        return currentVersionNumber;
    }

    public long getLockVersion() {
        return lockVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<RequestVersionEntity> getVersions() {
        return Collections.unmodifiableList(versions);
    }
}
