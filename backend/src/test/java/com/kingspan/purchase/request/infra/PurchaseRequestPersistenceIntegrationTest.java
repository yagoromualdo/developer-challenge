package com.kingspan.purchase.request.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.request.domain.ApprovalStep;
import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestSnapshot;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.domain.StepStatus;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.support.DatabaseCleaner;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PurchaseRequestPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Autowired
    private PurchaseRequestRepository requests;

    @Autowired
    private UserRepository users;

    @Autowired
    private CostCenterRepository costCenters;

    @Autowired
    private JdbcTemplate jdbc;

    // Cada bloco é uma transação real com commit, como num caso de uso; o teste em si não é @Transactional.
    @Autowired
    private TransactionTemplate transaction;

    private Long costCenterId;
    private Long ownerId;
    private Long firstApproverId;
    private Long secondApproverId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        ownerId = saveUser("solicitante@example.com", Role.SOLICITANTE, null);
        firstApproverId = saveUser("aprovador1@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1);
        secondApproverId = saveUser("aprovador2@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveMapearParaOSnapshotSomenteAsEtapasDaVersaoAtual() {
        Long requestId = saveRequestResubmittedAfterChangesRequested();

        RequestSnapshot snapshot = transaction.execute(status ->
                RequestSnapshotMapper.toSnapshot(requests.findById(requestId).orElseThrow()));

        assertThat(snapshot.ownerId()).isEqualTo(ownerId);
        assertThat(snapshot.costCenterId()).isEqualTo(costCenterId);
        assertThat(snapshot.status()).isEqualTo(RequestStatus.PENDING_APPROVAL);
        assertThat(snapshot.steps())
                .containsExactly(ApprovalStep.pending(StepLevel.NIVEL_1), ApprovalStep.pending(StepLevel.NIVEL_2));
        assertThat(snapshot.currentStep()).contains(ApprovalStep.pending(StepLevel.NIVEL_1));
        // Na v1 ele aprovou a NIVEL_1; na v2 isso não conta para a segregação de funções.
        assertThat(snapshot.stepApprovedBy(firstApproverId)).isEmpty();
    }

    @Test
    void deveMapearEtapasDecididasComQuemDecidiuNaOrdemDaCadeia() {
        Long requestId = saveSubmittedRequest(List.of(StepLevel.NIVEL_2, StepLevel.NIVEL_1, StepLevel.ADMIN));
        transaction.executeWithoutResult(status -> {
            PurchaseRequestEntity request = requests.findById(requestId).orElseThrow();
            request.getCurrentVersion().getSteps().get(0).decide(StepStatus.APPROVED, secondApproverId, NOW, null);
            request.touch(NOW);
        });

        RequestSnapshot snapshot = transaction.execute(status ->
                RequestSnapshotMapper.toSnapshot(requests.findById(requestId).orElseThrow()));

        // A ordem é a da cadeia recebida (step_order), não a ordem natural do enum.
        assertThat(snapshot.steps())
                .containsExactly(
                        ApprovalStep.approvedBy(StepLevel.NIVEL_2, secondApproverId),
                        ApprovalStep.pending(StepLevel.NIVEL_1),
                        ApprovalStep.pending(StepLevel.ADMIN));
        assertThat(snapshot.stepApprovedBy(secondApproverId))
                .contains(ApprovalStep.approvedBy(StepLevel.NIVEL_2, secondApproverId));
    }

    @Test
    void devePreservarAVersaoAnteriorComSuasEtapasAoCriarNovaVersao() {
        Long requestId = saveRequestResubmittedAfterChangesRequested();

        List<RequestVersionEntity> versions = transaction.execute(status -> {
            List<RequestVersionEntity> loaded =
                    requests.findById(requestId).orElseThrow().getVersions();
            loaded.forEach(version -> version.getSteps().size());
            return loaded;
        });

        assertThat(versions).extracting(RequestVersionEntity::getVersionNumber).containsExactly(1, 2);
        assertThat(versions.get(0).getSteps())
                .extracting(ApprovalStepEntity::getStatus, ApprovalStepEntity::getDecidedBy)
                .containsExactly(
                        tuple(StepStatus.APPROVED, firstApproverId),
                        tuple(StepStatus.CHANGES_REQUESTED, secondApproverId));
    }

    @Test
    void deveRenumerarItensAoSubstituirAColecaoNaMesmaTransacao() {
        Long requestId = saveDraft(List.of(item("Notebook", 2, "4899.90"), item("Mouse", 2, "99.90")));

        transaction.executeWithoutResult(status -> {
            PurchaseRequestEntity request = requests.findById(requestId).orElseThrow();
            request.getCurrentVersion()
                    .updateContent(
                            "Notebooks",
                            "Substituição de equipamentos",
                            Category.EQUIPMENT,
                            LocalDate.of(2026, 12, 15),
                            new BigDecimal("10349.80"),
                            List.of(
                                    item("Monitor", 1, "500.00"),
                                    item("Notebook", 2, "4899.90"),
                                    item("Cabo HDMI", 1, "50.00")));
            request.touch(NOW);
        });

        List<Integer> lineNumbers = jdbc.queryForList(
                "SELECT i.line_number FROM request_item i JOIN request_version v ON v.id = i.version_id"
                        + " WHERE v.request_id = ? ORDER BY i.line_number",
                Integer.class,
                requestId);
        assertThat(lineNumbers).containsExactly(0, 1, 2);
    }

    @Test
    void deveRecusarAlterarVersaoJaSubmetida() {
        Long requestId = saveSubmittedRequest(List.of(StepLevel.NIVEL_1));

        transaction.executeWithoutResult(status -> {
            RequestVersionEntity submitted =
                    requests.findById(requestId).orElseThrow().getCurrentVersion();
            assertThatThrownBy(() -> submitted.updateContent(
                            "Outro título",
                            "Outra descrição",
                            Category.EQUIPMENT,
                            null,
                            new BigDecimal("99.90"),
                            List.of(item("Mouse", 1, "99.90"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("imutável");
            assertThatThrownBy(() -> submitted.markSubmitted(NOW, YearMonth.of(2026, 12)))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> submitted.defineChain(List.of(StepLevel.NIVEL_2)))
                    .isInstanceOf(IllegalStateException.class);
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM request_item i JOIN request_version v ON v.id = i.version_id"
                                + " WHERE v.request_id = ? AND i.description = 'Notebook'",
                        Long.class,
                        requestId))
                .isEqualTo(1);
    }

    @Test
    void deveIncrementarOLockVersionQuandoSoOsFilhosMudam() {
        Long requestId = saveDraft(List.of(item("Notebook", 1, "4899.90")));
        long before = lockVersionOf(requestId);

        transaction.executeWithoutResult(status -> {
            PurchaseRequestEntity request = requests.findById(requestId).orElseThrow();
            request.getCurrentVersion()
                    .updateContent(
                            "Notebook",
                            "Troca de equipamento",
                            Category.EQUIPMENT,
                            null,
                            new BigDecimal("99.90"),
                            List.of(item("Mouse", 1, "99.90")));
            request.touch(NOW.plusSeconds(60));
        });

        assertThat(lockVersionOf(requestId)).isEqualTo(before + 1);
    }

    @Test
    void deveLancarConflitoOtimistaAoGravarCopiaDesatualizada() {
        Long requestId = saveDraft(List.of(item("Notebook", 1, "4899.90")));
        PurchaseRequestEntity stale = requests.findById(requestId).orElseThrow();
        transaction.executeWithoutResult(status -> {
            PurchaseRequestEntity fresh = requests.findById(requestId).orElseThrow();
            fresh.moveTo(RequestStatus.CANCELLED, NOW);
        });

        stale.moveTo(RequestStatus.PENDING_APPROVAL, NOW);

        assertThatThrownBy(() -> requests.save(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    private Long saveRequestResubmittedAfterChangesRequested() {
        Long requestId = saveSubmittedRequest(List.of(StepLevel.NIVEL_1, StepLevel.NIVEL_2));
        transaction.executeWithoutResult(status -> {
            PurchaseRequestEntity request = requests.findById(requestId).orElseThrow();
            List<ApprovalStepEntity> steps = request.getCurrentVersion().getSteps();
            steps.get(0).decide(StepStatus.APPROVED, firstApproverId, NOW, null);
            steps.get(1).decide(StepStatus.CHANGES_REQUESTED, secondApproverId, NOW, "Detalhar a configuração.");
            request.moveTo(RequestStatus.CHANGES_REQUESTED, NOW);

            RequestVersionEntity v2 = version(List.of(item("Notebook", 2, "4799.90")));
            request.addVersion(v2, NOW);
            v2.markSubmitted(NOW, YearMonth.of(2026, 11));
            v2.defineChain(List.of(StepLevel.NIVEL_1, StepLevel.NIVEL_2));
            request.moveTo(RequestStatus.PENDING_APPROVAL, NOW);
        });
        return requestId;
    }

    private Long saveSubmittedRequest(List<StepLevel> chain) {
        RequestVersionEntity v1 = version(List.of(item("Notebook", 2, "4899.90")));
        v1.markSubmitted(NOW, YearMonth.of(2026, 11));
        v1.defineChain(chain);
        return requests.save(new PurchaseRequestEntity(ownerId, costCenterId, RequestStatus.PENDING_APPROVAL, v1, NOW))
                .getId();
    }

    private Long saveDraft(List<RequestItemEntity> items) {
        return requests.save(new PurchaseRequestEntity(ownerId, costCenterId, RequestStatus.DRAFT, version(items), NOW))
                .getId();
    }

    private static RequestVersionEntity version(List<RequestItemEntity> items) {
        return new RequestVersionEntity(
                "Notebooks",
                "Substituição de equipamentos",
                Category.EQUIPMENT,
                LocalDate.of(2026, 12, 15),
                new BigDecimal("9799.80"),
                items,
                NOW);
    }

    private static RequestItemEntity item(String description, int quantity, String unitPrice) {
        return new RequestItemEntity(description, quantity, new BigDecimal(unitPrice));
    }

    private long lockVersionOf(Long requestId) {
        return jdbc.queryForObject("SELECT lock_version FROM purchase_request WHERE id = ?", Long.class, requestId);
    }

    private Long saveUser(String email, Role role, ApproverLevel level) {
        return users.save(new UserEntity("Usuário", email, "{bcrypt}hash", role, level, costCenterId, NOW))
                .getId();
    }
}
