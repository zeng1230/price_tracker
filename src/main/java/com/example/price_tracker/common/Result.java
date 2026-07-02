package com.example.price_tracker.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Unified API response wrapper")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    @Schema(description = "Business status code (200 for success, non-200 for error)", example = "200")
    private Integer code;

    @Schema(description = "Response status description message", example = "success")
    private String message;

    @Schema(description = "Response payload data")
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(ResultCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    public static <T> Result<T> failure(ResultCode resultCode) {
        return failure(resultCode, resultCode.getMessage());
    }

    public static <T> Result<T> failure(ResultCode resultCode, String message) {
        return Result.<T>builder()
                .code(resultCode.getCode())
                .message(message)
                .data(null)
                .build();
    }
}
