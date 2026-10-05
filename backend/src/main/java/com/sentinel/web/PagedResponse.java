package com.sentinel.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable pagination envelope. We do not serialize Spring {@code Page}
 * directly — its JSON shape is an implementation detail we do not want
 * clients depending on.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
