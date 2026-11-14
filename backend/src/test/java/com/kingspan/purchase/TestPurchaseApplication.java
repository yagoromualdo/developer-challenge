package com.kingspan.purchase;

import org.springframework.boot.SpringApplication;

public class TestPurchaseApplication {

    public static void main(String[] args) {
        SpringApplication.from(PurchaseApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
