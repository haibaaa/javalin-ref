package com.tccs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private T data;
    private String error;

    public ApiResponse() {}

    public ApiResponse(T data, String error) {
        this.data = data;
        this.error = error;
    }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message);
    }
}
