package org.openphc.cce.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private T data;
    private PaginationDto pagination;
    private ErrorDto error;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().data(data).build();
    }

    public static <T> ApiResponse<T> page(T data, PaginationDto pagination) {
        return ApiResponse.<T>builder().data(data).pagination(pagination).build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder().error(new ErrorDto(code, message)).build();
    }

    @Data
    @AllArgsConstructor
    public static class ErrorDto {
        private String code;
        private String message;
    }
}
