package com.kingspan.purchase.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** TRUNCATE e não DELETE: o trigger append-only de request_event bloqueia DELETE, mas não TRUNCATE. */
public final class DatabaseCleaner {

    private static final String TABLES = String.join(
            ", ",
            "cost_center",
            "app_user",
            "budget",
            "purchase_request",
            "request_version",
            "request_item",
            "approval_step",
            "request_event",
            "idempotency_key");

    private DatabaseCleaner() {}

    public static void truncateAll(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE " + TABLES + " RESTART IDENTITY CASCADE");
    }
}
