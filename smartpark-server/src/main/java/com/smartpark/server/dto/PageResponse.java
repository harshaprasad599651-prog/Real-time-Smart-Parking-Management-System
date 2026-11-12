package com.smartpark.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** One page of results. {@code page} is zero-based. */
@Schema(description = "A page of results (page numbers start at 0)")
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
