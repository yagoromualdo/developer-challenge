package com.kingspan.purchase.request.infra;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.StepLevel;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "request_version")
public class RequestVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, updatable = false)
    private PurchaseRequestEntity request;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    // 1º dia do mês (ck_request_version_competence_first_day).
    @Column(name = "competence_month")
    private LocalDate competenceMonth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<RequestItemEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder")
    private List<ApprovalStepEntity> steps = new ArrayList<>();

    protected RequestVersionEntity() {}

    public RequestVersionEntity(
            String title,
            String description,
            Category category,
            LocalDate neededBy,
            BigDecimal total,
            List<RequestItemEntity> items,
            Instant now) {
        updateContent(title, description, category, neededBy, total, items);
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    void attachTo(PurchaseRequestEntity request, int versionNumber) {
        this.request = request;
        this.versionNumber = versionNumber;
    }

    /** O {@code total} chega pronto: quem soma os itens é o domínio ({@code Money}), não a entidade. */
    public void updateContent(
            String title,
            String description,
            Category category,
            LocalDate neededBy,
            BigDecimal total,
            List<RequestItemEntity> items) {
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.category = Objects.requireNonNull(category, "category");
        this.neededBy = neededBy;
        this.total = Objects.requireNonNull(total, "total");
        replaceItems(items);
    }

    // Renumera de 0 a cada troca: a UNIQUE adiável (version_id, line_number) nunca vê números repetidos no commit.
    private void replaceItems(List<RequestItemEntity> newItems) {
        Objects.requireNonNull(newItems, "items");
        items.clear();
        for (int line = 0; line < newItems.size(); line++) {
            RequestItemEntity item = newItems.get(line);
            item.attachTo(this, line);
            items.add(item);
        }
    }

    public void markSubmitted(Instant submittedAt, YearMonth competenceMonth) {
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.competenceMonth =
                Objects.requireNonNull(competenceMonth, "competenceMonth").atDay(1);
    }

    /** Grava a cadeia montada pelo {@code ApprovalChainBuilder}, na ordem recebida, com todas as etapas pendentes. */
    public void defineChain(List<StepLevel> levels) {
        Objects.requireNonNull(levels, "levels");
        steps.clear();
        for (int index = 0; index < levels.size(); index++) {
            steps.add(new ApprovalStepEntity(this, index + 1, levels.get(index)));
        }
    }

    public Long getId() {
        return id;
    }

    public PurchaseRequestEntity getRequest() {
        return request;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDate getNeededBy() {
        return neededBy;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public YearMonth getCompetenceMonth() {
        return competenceMonth == null ? null : YearMonth.from(competenceMonth);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<RequestItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<ApprovalStepEntity> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}
