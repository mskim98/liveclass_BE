package src.alert_system.notification.domain.enums;

public enum DeliveryErrorCode {

    //네트워크 장애 (일시적)
    NETWORK_ERROR(true),

    // 알림 발송 외부 서비스 내부 오류
    EXTERNAL_SERVICE_ERROR(true),

    // 요청 제한 초과
    RATE_LIMITED(true),

    // 알림 요청 타임아웃
    NOTIFICATION_TIMEOUT(true),

    // 잘못된 수신자
    INVALID_RECIPIENT(false),

    // 잘못된 payload
    INVALID_PAYLOAD(false),

    //알 수 없는 오류
    UNKNOWN(true);

    private final boolean retryable;

    DeliveryErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return this.retryable;
    }
}
