package com.kingspan.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.domain.StepStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaMigrationIntegrationTest {

    private static final List<String> TABLES = List.of(
            "cost_center",
            "app_user",
            "budget",
            "purchase_request",
            "request_version",
            "request_item",
            "approval_step",
            "request_event",
            "idempotency_key");

    @Autowired
    private JdbcTemplate jdbc;

    private long costCenterId;
    private long requesterId;
    private long approverId;
    private long otherApproverId;

    @BeforeEach
    void setUp() {
        truncateAll();
        costCenterId = insertId("INSERT INTO cost_center (code, name, created_at) VALUES ('CC-TI-01', 'TI', now())");
        requesterId = insertUser("solicitante@example.com", "SOLICITANTE", null);
        approverId = insertUser("aprovador1@example.com", "APROVADOR", "NIVEL_1");
        otherApproverId = insertUser("aprovador2@example.com", "APROVADOR", "NIVEL_2");
    }

    @AfterEach
    void tearDown() {
        truncateAll();
    }

    @Test
    void deveAplicarMigrationV1ComSucesso() {
        Boolean success =
                jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void deveCriarTodasAsTabelasDoModelo() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).containsAll(TABLES);
    }

    @Test
    void deveAceitarOrcamentoTotalmenteConsumido() {
        assertThatCode(() -> insertBudget("1000.00", "1000.00")).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarOrcamentoComConsumoAcimaDoValor() {
        assertThatThrownBy(() -> insertBudget("1000.00", "1000.01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_budget_consumed_within_amount");
    }

    @Test
    void deveRejeitarOrcamentoComConsumoNegativo() {
        assertThatThrownBy(() -> insertBudget("1000.00", "-0.01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_budget_consumed_within_amount");
    }

    @Test
    void deveRejeitarUpdateQueEstouraOrcamento() {
        insertBudget("1000.00", "900.00");

        assertThatThrownBy(() -> jdbc.update("UPDATE budget SET consumed = consumed + 100.01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_budget_consumed_within_amount");
    }

    @Test
    void deveRejeitarChaveDeIdempotenciaRepetidaParaOMesmoUsuario() {
        insertIdempotencyKey(requesterId, "chave-1");

        assertThatThrownBy(() -> insertIdempotencyKey(requesterId, "chave-1"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_idempotency_key_user_key");
    }

    @Test
    void devePermitirMesmaChaveDeIdempotenciaParaUsuariosDiferentes() {
        insertIdempotencyKey(requesterId, "chave-1");

        assertThatCode(() -> insertIdempotencyKey(approverId, "chave-1")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(StepStatus.class)
    void deveAceitarTodoStatusDeEtapaDoDominio(StepStatus status) {
        long versionId = insertVersion("OTHER");
        Long decidedBy = status == StepStatus.PENDING ? null : approverId;

        assertThatCode(() -> insertStep(versionId, 1, StepLevel.NIVEL_1.name(), status.name(), decidedBy))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(StepLevel.class)
    void deveAceitarTodoNivelDeEtapaDoDominio(StepLevel level) {
        long versionId = insertVersion("OTHER");

        assertThatCode(() -> insertStep(versionId, 1, level.name(), StepStatus.PENDING.name(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarStatusENivelDeEtapaForaDoDominio() {
        long versionId = insertVersion("OTHER");

        assertThatThrownBy(() -> insertStep(versionId, 1, "NIVEL_1", "SKIPPED", approverId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_approval_step_status");
        assertThatThrownBy(() -> insertStep(versionId, 1, "NIVEL_3", "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_approval_step_level");
    }

    @Test
    void deveRejeitarEtapaDecididaSemDecisor() {
        long versionId = insertVersion("OTHER");

        assertThatThrownBy(() -> insertStep(versionId, 1, "NIVEL_1", "APPROVED", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_approval_step_decision");
    }

    @Test
    void deveRejeitarNivelDuplicadoNaMesmaVersao() {
        long versionId = insertVersion("SOFTWARE");
        insertStep(versionId, 1, "ADMIN", "PENDING", null);

        assertThatThrownBy(() -> insertStep(versionId, 2, "ADMIN", "PENDING", null))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_approval_step_level");
    }

    @Test
    void deveRejeitarMesmoAprovadorEmDuasEtapasDaMesmaVersao() {
        long versionId = insertVersion("OTHER");
        insertStep(versionId, 1, "NIVEL_1", "APPROVED", otherApproverId);

        assertThatThrownBy(() -> insertStep(versionId, 2, "NIVEL_2", "APPROVED", otherApproverId))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_approval_step_approver");
    }

    @ParameterizedTest
    @EnumSource(RequestStatus.class)
    void deveAceitarTodoStatusDeSolicitacaoDoDominio(RequestStatus status) {
        assertThatCode(() -> insertRequest(status.name())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void deveAceitarTodaCategoriaDoDominio(Category category) {
        assertThatCode(() -> insertVersion(category.name())).doesNotThrowAnyException();
    }

    @Test
    void deveImpedirAlteracaoEExclusaoDeEventoDoHistorico() {
        insertEvent(insertRequest("DRAFT"));

        assertThatThrownBy(() -> jdbc.update("UPDATE request_event SET comment = 'alterado'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("request_event é append-only: UPDATE não é permitido");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM request_event"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("request_event é append-only: DELETE não é permitido");
        assertThat(countEvents()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT comment FROM request_event", String.class))
                .isNull();
    }

    @Test
    void deveLimparHistoricoComTruncateApesarDoTrigger() {
        insertEvent(insertRequest("DRAFT"));

        truncateAll();

        assertThat(countEvents()).isZero();
    }

    @Test
    void deveRejeitarNumeroDeLinhaRepetidoNaMesmaVersao() {
        long versionId = insertVersion("OTHER");
        insertItem(versionId, 0);

        assertThatThrownBy(() -> insertItem(versionId, 0))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_request_item_line_number");
    }

    @Test
    void deveRejeitarNumeroDeLinhaRepetidoNoCommitDaTransacao() {
        long versionId = insertVersion("OTHER");

        assertThatThrownBy(() -> inTransaction(() -> {
                    insertItem(versionId, 0);
                    insertItem(versionId, 0);
                }))
                .isInstanceOf(TransactionSystemException.class)
                .rootCause()
                .hasMessageContaining("uq_request_item_line_number");
        assertThat(countItems(versionId)).isZero();
    }

    @Test
    void devePermitirSubstituirItensNaMesmaTransacaoComInsertAntesDoDelete() {
        long versionId = insertVersion("OTHER");
        long oldItemId = insertItem(versionId, 0);

        // Mesma ordem de flush do Hibernate ao trocar a coleção: insere o novo item antes de apagar o antigo.
        inTransaction(() -> {
            insertItem(versionId, 0);
            jdbc.update("DELETE FROM request_item WHERE id = ?", oldItemId);
        });

        assertThat(countItems(versionId)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {"APROVADOR, NIVEL_1", "APROVADOR, NIVEL_2", "SOLICITANTE, null", "ADMIN, null"})
    void deveAceitarNivelCoerenteComOPapel(String role, String approverLevel) {
        assertThatCode(() -> insertUser("valido-" + role + "-" + approverLevel + "@example.com", role, approverLevel))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "APROVADOR, null",
                "SOLICITANTE, NIVEL_1",
                "SOLICITANTE, NIVEL_2",
                "ADMIN, NIVEL_1",
                "ADMIN, NIVEL_2"
            })
    void deveRejeitarNivelIncoerenteComOPapel(String role, String approverLevel) {
        assertThatThrownBy(() -> insertUser("invalido@example.com", role, approverLevel))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_user_level_only_for_approver");
    }

    @Test
    void deveRejeitarEmailRepetidoIgnorandoMaiusculas() {
        assertThatThrownBy(() -> insertUser("SOLICITANTE@example.com", "SOLICITANTE", null))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_app_user_email");
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()))
                .executeWithoutResult(status -> work.run());
    }

    private void insertEvent(long requestId) {
        jdbc.update("""
                INSERT INTO request_event (request_id, version_number, actor_id, actor_role, action, to_status, occurred_at)
                VALUES (?, 1, ?, 'SOLICITANTE', 'CREATE', 'DRAFT', now())
                """, requestId, requesterId);
    }

    private long countEvents() {
        return jdbc.queryForObject("SELECT count(*) FROM request_event", Long.class);
    }

    private long insertItem(long versionId, int lineNumber) {
        return insertId("""
                INSERT INTO request_item (version_id, line_number, description, quantity, unit_price)
                VALUES (?, ?, 'Notebook 16GB/512GB', 1, 4899.90)
                """, versionId, lineNumber);
    }

    private long countItems(long versionId) {
        return jdbc.queryForObject("SELECT count(*) FROM request_item WHERE version_id = ?", Long.class, versionId);
    }

    /** TRUNCATE e não DELETE: o trigger append-only de request_event bloqueia DELETE, mas não TRUNCATE. */
    private void truncateAll() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE");
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    private long insertUser(String email, String role, String approverLevel) {
        return insertId("""
                INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id, created_at, updated_at)
                VALUES ('Usuário', ?, '{bcrypt}hash', ?, ?, ?, now(), now())
                """, email, role, approverLevel, costCenterId);
    }

    private void insertBudget(String amount, String consumed) {
        jdbc.update(
                "INSERT INTO budget (cost_center_id, month, amount, consumed) VALUES (?, DATE '2026-11-01', ?::numeric, ?::numeric)",
                costCenterId,
                amount,
                consumed);
    }

    private void insertIdempotencyKey(long userId, String key) {
        jdbc.update("""
                INSERT INTO idempotency_key (user_id, key, request_hash, response_status, response_body, created_at)
                VALUES (?, ?, repeat('a', 64), 201, '{"id": 1}'::jsonb, now())
                """, userId, key);
    }

    private long insertRequest(String status) {
        return insertId("""
                INSERT INTO purchase_request (owner_id, cost_center_id, status, current_version, created_at, updated_at)
                VALUES (?, ?, ?, 1, now(), now())
                """, requesterId, costCenterId, status);
    }

    private long insertVersion(String category) {
        long requestId = insertRequest("DRAFT");
        return insertId("""
                INSERT INTO request_version (request_id, version_number, title, description, category, total, created_at)
                VALUES (?, 1, 'Notebooks', 'Substituição de equipamentos', ?, 0, now())
                """, requestId, category);
    }

    private void insertStep(long versionId, int order, String level, String status, Long decidedBy) {
        jdbc.update(
                """
                INSERT INTO approval_step (version_id, step_order, level, status, decided_by, decided_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                versionId,
                order,
                level,
                status,
                decidedBy,
                decidedBy == null ? null : Timestamp.from(Instant.now()));
    }
}
