package src.alert_system.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import src.alert_system.common.response.ApiResponse;
import src.alert_system.notification.application.exception.IllegalDeliveryTransitionException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외 단일 진입점 처리 메서드
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(final BusinessException ex) {
        final ErrorCode errorCode = ex.getErrorCode();
        log.warn("BusinessException: code={}, message={}", errorCode.getCode(), ex.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), ex.getMessage()));
    }

    // 상태 전이 예외 처리 메서드 (도메인 예외 → 422 매핑)
    @ExceptionHandler(IllegalDeliveryTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransition(final IllegalDeliveryTransitionException ex) {
        final ErrorCode errorCode = ErrorCode.INVALID_DELIVERY_TRANSITION;
        log.warn("IllegalDeliveryTransition: {}", ex.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), ex.getMessage()));
    }

    // Bean Validation 실패 처리 메서드
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(final MethodArgumentNotValidException ex) {
        final Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        final ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), fieldErrors));
    }

    // 잘못된 JSON 본문 처리 메서드
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(final HttpMessageNotReadableException ex) {
        final ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    // DB 제약 위반 처리 메서드 (UNIQUE 등 — 서비스 레이어가 잡지 못한 경우 최후 방어)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(final DataIntegrityViolationException ex) {
        final ErrorCode errorCode = ErrorCode.CONFLICT;
        log.warn("DataIntegrityViolation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    // 미처리 예외 최종 처리 메서드
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(final Exception ex) {
        log.error("Unhandled exception", ex);
        final ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}
