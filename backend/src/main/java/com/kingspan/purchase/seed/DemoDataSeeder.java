package com.kingspan.purchase.seed;

import com.kingspan.purchase.budget.application.BudgetService;
import com.kingspan.purchase.costcenter.application.CostCenterService;
import com.kingspan.purchase.request.application.ApprovalService;
import com.kingspan.purchase.request.application.PurchaseRequestService;
import com.kingspan.purchase.request.application.RequestDetailView;
import com.kingspan.purchase.request.application.RequestDraft;
import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.Money;
import com.kingspan.purchase.request.domain.RequestItem;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.BusinessTime;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.user.application.UserService;
import com.kingspan.purchase.user.domain.UserProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cenário de demonstração montado só pelos application services, para que cada transição passe pelas regras reais
 * (cadeia, segregação, orçamento) e gere o histórico como numa chamada à API.
 */
@Component
class DemoDataSeeder {

    /** Senha fictícia, exclusivamente de desenvolvimento, de todos os usuários do seed (documentada no SOLUTION.md). */
    static final String PASSWORD = "Senha@123";

    static final String IT_CODE = "CC-TI-01";
    static final String MAINTENANCE_CODE = "CC-MNT-01";
    static final BigDecimal IT_BUDGET = new BigDecimal("50000.00");
    // Quase esgotado: 7.200,00 aprovados deixam 800,00, menos do que as pendentes do centro exigem.
    static final BigDecimal MAINTENANCE_BUDGET = new BigDecimal("8000.00");

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final CostCenterService costCenters;
    private final BudgetService budgets;
    private final UserService users;
    private final PurchaseRequestService requests;
    private final ApprovalService approvals;
    private final Clock clock;

    DemoDataSeeder(
            CostCenterService costCenters,
            BudgetService budgets,
            UserService users,
            PurchaseRequestService requests,
            ApprovalService approvals,
            Clock clock) {
        this.costCenters = costCenters;
        this.budgets = budgets;
        this.users = users;
        this.requests = requests;
        this.approvals = approvals;
        this.clock = clock;
    }

    void seed() {
        // Mesmo cálculo da competência no submit: o orçamento cai no mês em que as solicitações do seed são submetidas.
        YearMonth month = BusinessTime.competenceOf(clock.instant());
        Long it = costCenters.create(IT_CODE, "Tecnologia da Informação").id();
        Long maintenance =
                costCenters.create(MAINTENANCE_CODE, "Manutenção Industrial").id();
        budgets.define(it, month, IT_BUDGET);
        budgets.define(maintenance, month, MAINTENANCE_BUDGET);

        Actor admin = user("Alice Administradora", "admin@example.com", Role.ADMIN, null, null);
        Actor admin2 = user("Bruno Administrador", "admin2@example.com", Role.ADMIN, null, null);
        Team itTeam = team(it, "ti", "Carla", "Diego", "Elisa");
        Team maintenanceTeam = team(maintenance, "mnt", "Fábio", "Gabriela", "Heitor");

        seedItRequests(it, itTeam, admin, admin2);
        seedMaintenanceRequests(maintenanceTeam);
        log.info(
                "Seed concluído: 2 centros de custo, 8 usuários e solicitações em todos os estados (orçamentos de {})",
                month);
    }

    private void seedItRequests(Long it, Team team, Actor admin, Actor admin2) {
        draft(
                team.requester(),
                "Monitores para a sala de reuniões",
                Category.EQUIPMENT,
                item("Monitor 27\"", 2, "1250.00"));

        // SOFTWARE abaixo de 1.000,00: NIVEL_1 → ADMIN, aguardando o NIVEL_1.
        submitted(
                team.requester(),
                "Licenças de IDE para o time",
                Category.SOFTWARE,
                item("Licença anual de IDE", 10, "89.90"));

        // Acima de 10.000,00: NIVEL_1 → NIVEL_2 → ADMIN, aguardando o NIVEL_2.
        RequestDetailView notebooks = submitted(
                team.requester(),
                "Notebooks para novos desenvolvedores",
                Category.EQUIPMENT,
                item("Notebook 16GB/512GB", 3, "4899.90"));
        approve(team.level1(), notebooks, "De acordo, previsto no plano de contratações.");

        RequestDetailView cables = submitted(
                team.requester(),
                "Cabos e conectores de rede",
                Category.SUPPLIES,
                item("Cabo de rede Cat6 (3 m)", 40, "12.50"));
        approve(team.level1(), cables, null);

        RequestDetailView server = submitted(
                team.requester(),
                "Servidor de backup",
                Category.EQUIPMENT,
                item("Servidor 2U com 4 discos de 8 TB", 1, "18500.00"));
        server = approve(team.level1(), server, "Necessário para a política de backup.");
        server = approve(team.level2(), server, "Aprovado dentro do orçamento do trimestre.");
        approve(admin, server, null);

        // Pedido de um ADMIN: a etapa ADMIN fica com o outro ADMIN (segregação de funções, README 2.3 regra 7).
        RequestDetailView antivirus = requests.create(
                admin,
                it,
                draftOf("Antivírus corporativo", Category.SOFTWARE, item("Licença anual por estação", 35, "120.00")));
        antivirus = submit(admin, antivirus);
        antivirus = approve(team.level1(), antivirus, null);
        antivirus = approve(team.level2(), antivirus, null);
        approve(admin2, antivirus, "Licenciamento conferido pela segurança.");

        RequestDetailView fair = submitted(
                team.requester(),
                "Viagem para feira de tecnologia",
                Category.TRAVEL,
                item("Passagem e hospedagem por pessoa", 2, "3200.00"));
        approvals.reject(team.level1(), fair.id(), fair.lockVersion(), "Sem verba de viagens neste semestre.");

        RequestDetailView training = submitted(
                team.requester(),
                "Treinamento em segurança da informação",
                Category.SERVICES,
                item("Curso in company (turma)", 1, "2800.00"));
        approvals.requestChanges(
                team.level1(),
                training.id(),
                training.lockVersion(),
                "Inclua o conteúdo programático e a carga horária.");

        // Devolvida, editada e reenviada: v1 fica congelada e a v2 recomeça a cadeia do zero.
        RequestDetailView monitoring = submitted(
                team.requester(),
                "Ferramenta de monitoramento",
                Category.SERVICES,
                item("Assinatura mensal", 12, "350.00"));
        monitoring = approvals.requestChanges(
                team.level1(),
                monitoring.id(),
                monitoring.lockVersion(),
                "Avalie o plano anual, que costuma ser mais barato.");
        monitoring = requests.edit(
                team.requester(),
                monitoring.id(),
                monitoring.lockVersion(),
                draftOf(
                        "Ferramenta de monitoramento (plano anual)",
                        Category.SERVICES,
                        item("Assinatura anual", 1, "3600.00")));
        submit(team.requester(), monitoring);

        RequestDetailView coffee = draft(
                team.requester(),
                "Café para o escritório",
                Category.SUPPLIES,
                item("Pacote de café 500 g", 20, "24.90"));
        requests.cancel(team.requester(), coffee.id(), coffee.lockVersion(), "Compra feita pelo setor administrativo.");
    }

    private void seedMaintenanceRequests(Team team) {
        RequestDetailView bearings = submitted(
                team.requester(),
                "Rolamentos e correias da linha 2",
                Category.SUPPLIES,
                item("Kit de rolamentos e correias", 1, "7200.00"));
        bearings = approve(team.level1(), bearings, null);
        approve(team.level2(), bearings, "Manutenção preventiva programada.");

        // Última etapa pendente com 800,00 disponíveis: o aprovador NIVEL_2 recebe BUDGET_EXCEEDED ao aprovar.
        RequestDetailView tools = submitted(
                team.requester(),
                "Kit de ferramentas para a oficina",
                Category.EQUIPMENT,
                item("Kit de ferramentas profissional", 1, "1500.00"));
        approve(team.level1(), tools, "Ferramentas atuais estão gastas.");

        submitted(
                team.requester(),
                "EPIs para a equipe de manutenção",
                Category.SUPPLIES,
                item("Luva de segurança (par)", 30, "45.00"));
    }

    private Team team(Long costCenterId, String suffix, String requester, String level1, String level2) {
        return new Team(
                user(
                        requester + " Solicitante",
                        "solicitante." + suffix + "@example.com",
                        Role.SOLICITANTE,
                        null,
                        costCenterId),
                user(
                        level1 + " Aprovador N1",
                        "aprovador1." + suffix + "@example.com",
                        Role.APROVADOR,
                        ApproverLevel.NIVEL_1,
                        costCenterId),
                user(
                        level2 + " Aprovador N2",
                        "aprovador2." + suffix + "@example.com",
                        Role.APROVADOR,
                        ApproverLevel.NIVEL_2,
                        costCenterId));
    }

    private Actor user(String name, String email, Role role, ApproverLevel level, Long costCenterId) {
        return users.create(name, email, PASSWORD, new UserProfile(role, level, costCenterId))
                .toActor();
    }

    private RequestDetailView draft(Actor owner, String title, Category category, RequestItem... items) {
        return requests.create(owner, null, draftOf(title, category, items));
    }

    private RequestDetailView submitted(Actor owner, String title, Category category, RequestItem... items) {
        return submit(owner, draft(owner, title, category, items));
    }

    private RequestDetailView submit(Actor owner, RequestDetailView request) {
        return requests.submit(owner, request.id(), request.lockVersion());
    }

    private RequestDetailView approve(Actor approver, RequestDetailView request, String comment) {
        return approvals.approve(approver, request.id(), request.lockVersion(), comment);
    }

    private RequestDraft draftOf(String title, Category category, RequestItem... items) {
        LocalDate neededBy = LocalDate.now(clock.withZone(BusinessTime.ZONE)).plusDays(30);
        return new RequestDraft(title, "Solicitação de exemplo criada pelo seed.", category, neededBy, List.of(items));
    }

    private static RequestItem item(String description, int quantity, String unitPrice) {
        return new RequestItem(description, quantity, Money.of(unitPrice));
    }

    private record Team(Actor requester, Actor level1, Actor level2) {}
}
