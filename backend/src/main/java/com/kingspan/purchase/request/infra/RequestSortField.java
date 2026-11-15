package com.kingspan.purchase.request.infra;

import java.util.Arrays;
import java.util.Optional;

/** Whitelist dos campos de ordenação da listagem, com o nome usado no parâmetro {@code sort} da API. */
public enum RequestSortField {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    STATUS("status"),
    TOTAL("total"),
    TITLE("title");

    private final String apiName;

    RequestSortField(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    public static Optional<RequestSortField> fromApiName(String name) {
        return Arrays.stream(values())
                .filter(field -> field.apiName.equals(name))
                .findFirst();
    }
}
