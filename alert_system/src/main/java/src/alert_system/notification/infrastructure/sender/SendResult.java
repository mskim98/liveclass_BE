package src.alert_system.notification.infrastructure.sender;

import src.alert_system.notification.domain.enums.DeliveryErrorCode;

public sealed interface SendResult {

    record Success(String externalMessageId) implements SendResult {
    }

    record RetryableFailure(DeliveryErrorCode errorCode, String reason) implements SendResult {
    }

    record NonRetryableFailure(DeliveryErrorCode errorCode, String reason) implements SendResult {
    }

    // 성공 결과 정적 팩토리 메서드
    static SendResult success(final String externalMessageId) {
        return new Success(externalMessageId);
    }

    // 재시도 가능 실패 결과 정적 팩토리 메서드
    static SendResult retryable(final DeliveryErrorCode errorCode, final String reason) {
        return new RetryableFailure(errorCode, reason);
    }

    // 재시도 불가 실패 결과 정적 팩토리 메서드
    static SendResult nonRetryable(final DeliveryErrorCode errorCode, final String reason) {
        return new NonRetryableFailure(errorCode, reason);
    }
}
