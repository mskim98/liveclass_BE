package src.alert_system.notification.application.command;

import src.alert_system.notification.domain.enums.DeliveryEvent;

import java.time.Instant;

public record DeliveryTransitionCommand(
        DeliveryEvent event,
        Instant now,
        String reason,
        Instant nextAttemptAt,
        String externalMessageId
) {

    // 발송 시작 명령 정적 팩토리 메서드
    public static DeliveryTransitionCommand dispatch(final Instant now) {
        return new DeliveryTransitionCommand(DeliveryEvent.DISPATCH, now, null, null, null);
    }

    // 발송 성공 명령 정적 팩토리 메서드 (외부 시스템 반환 메시지 ID 동봉)
    public static DeliveryTransitionCommand sendSuccess(final Instant now,
                                                        final String externalMessageId) {
        return new DeliveryTransitionCommand(DeliveryEvent.SEND_SUCCESS, now, null, null, externalMessageId);
    }

    // 발송 실패 명령 정적 팩토리 메서드 (백오프된 nextAttemptAt 동봉)
    public static DeliveryTransitionCommand sendFailure(final Instant now,
                                                        final String reason,
                                                        final Instant nextAttemptAt) {
        return new DeliveryTransitionCommand(DeliveryEvent.SEND_FAILURE, now, reason, nextAttemptAt, null);
    }

    // 재시도 한계 도달 또는 비재시도성 오류 명령 정적 팩토리 메서드
    public static DeliveryTransitionCommand exhaust(final Instant now, final String reason) {
        return new DeliveryTransitionCommand(DeliveryEvent.EXHAUST, now, reason, null, null);
    }

    // 재시도 진입 명령 정적 팩토리 메서드
    public static DeliveryTransitionCommand retry(final Instant now) {
        return new DeliveryTransitionCommand(DeliveryEvent.RETRY, now, null, null, null);
    }

    // 좀비 PROCESSING 복구 명령 정적 팩토리 메서드
    public static DeliveryTransitionCommand recover(final Instant now, final Instant nextAttemptAt) {
        return new DeliveryTransitionCommand(DeliveryEvent.RECOVER, now, null, nextAttemptAt, null);
    }

    // 운영자 수동 재시도 명령 정적 팩토리 메서드
    public static DeliveryTransitionCommand manualRetry(final Instant now, final Instant nextAttemptAt) {
        return new DeliveryTransitionCommand(DeliveryEvent.MANUAL_RETRY, now, null, nextAttemptAt, null);
    }
}
