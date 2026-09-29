package com.kbms.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Single public error envelope. Stack traces and entity internals never appear here. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        Instant timestamp,
        List<FieldIssue> fieldErrors) {

    public record FieldIssue(String field, String message) {}

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), null);
    }

    public static ApiError of(String code, String message, List<FieldIssue> fieldErrors) {
        return new ApiError(code, message, Instant.now(), fieldErrors.isEmpty() ? null : fieldErrors);
    }
}
