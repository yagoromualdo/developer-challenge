package com.kingspan.purchase.seed;

import com.kingspan.purchase.shared.web.RequestId;
import com.kingspan.purchase.user.application.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Popula um banco vazio com os dados fictícios do README 5.4 quando {@code SEED_ENABLED=true}; com qualquer usuário
 * já cadastrado não faz nada, então subir de novo não duplica dados.
 */
@Component
@ConditionalOnBooleanProperty("app.seed.enabled")
class SeedRunner implements ApplicationRunner {

    // Vai para o http_request_id dos eventos: no histórico, distingue o que nasceu do seed do que veio da API.
    static final String SEED_REQUEST_ID = "seed";

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final UserService users;
    private final DemoDataSeeder seeder;
    private final TransactionTemplate transaction;

    SeedRunner(UserService users, DemoDataSeeder seeder, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.seeder = seeder;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Uma transação só: se qualquer passo falhar, nada fica gravado e a próxima subida tenta de novo do zero. */
    @Override
    public void run(ApplicationArguments args) {
        try (MDC.MDCCloseable requestId = MDC.putCloseable(RequestId.MDC_KEY, SEED_REQUEST_ID)) {
            transaction.executeWithoutResult(status -> {
                if (users.hasAnyUser()) {
                    log.info("Seed ignorado: o banco já tem usuários cadastrados");
                    return;
                }
                seeder.seed();
            });
        }
    }
}
