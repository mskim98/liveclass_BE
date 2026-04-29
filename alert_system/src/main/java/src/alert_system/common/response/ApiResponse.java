package src.alert_system.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorBody error,
        Instant timestamp
) {

    public record ErrorBody(
            String code,
            String message,
            Object details
    ) {
    }

    // 성공 응답 생성 메서드
    public static <T> ApiResponse<T> successResponse(final T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    // 데이터 없는 성공 응답 생성 메서드
    public static ApiResponse<Void> successResponse() {
        return new ApiResponse<>(true, null, null, Instant.now());
    }

    // 실패 응답 생성 메서드
    public static <T> ApiResponse<T> error(final String code, final String message, final Object details) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, details), Instant.now());
    }

    // 상세 없는 실패 응답 생성 메서드
    public static <T> ApiResponse<T> error(final String code, final String message) {
        return error(code, message, null);
    }
}
