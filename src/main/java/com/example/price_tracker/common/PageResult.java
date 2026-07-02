package com.example.price_tracker.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Schema(description = "Pagination response wrapper")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    @Schema(description = "List of records for the current page")
    private List<T> records;

    @Schema(description = "Total number of records across all pages", example = "50")
    private Long total;

    @Schema(description = "Current page number (1-based)", example = "1")
    private Long current;

    @Schema(description = "Page size limit", example = "10")
    private Long size;

    @Schema(description = "Total number of pages", example = "5")
    private Long pages;

    public static <T> PageResult<T> of(List<T> records, Long total, Long current, Long size) {
        long safeSize = size == null || size <= 0 ? 1L : size;
        long safeTotal = total == null ? 0L : total;
        long calculatedPages = safeTotal == 0 ? 0L : (safeTotal + safeSize - 1) / safeSize;
        return PageResult.<T>builder()
                .records(records == null ? Collections.emptyList() : records)
                .total(safeTotal)
                .current(current == null ? 1L : current)
                .size(safeSize)
                .pages(calculatedPages)
                .build();
    }
}
