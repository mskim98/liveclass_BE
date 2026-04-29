package src.alert_system.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "C001", "요청 형식이 올바르지 않음"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "C002", "필드 검증 실패"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "리소스를 찾을 수 없음"),
    CONFLICT(HttpStatus.CONFLICT, "C004", "리소스 충돌"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C500", "서버 내부 오류"),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없음"),
    NOTIFICATION_DUPLICATE(HttpStatus.CONFLICT, "N002", "동일 멱등키로 이미 등록된 알림 존재"),
    INVALID_DELIVERY_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "N003", "허용되지 않은 발송 상태 전이");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(final HttpStatus status, final String code, final String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
