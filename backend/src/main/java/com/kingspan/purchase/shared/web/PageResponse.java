package com.kingspan.purchase.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Contrato de paginação do README 3.2; o {@code Page} do Spring não sai pela API. */
public record PageResponse<T>(List<T> data, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        data = List.copyOf(data);
    }

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
