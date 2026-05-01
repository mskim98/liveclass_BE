package src.alert_system.notification.api.dto;

import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryState;
import src.alert_system.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.Map;

/*
 * 사용자 관점 알림 응답 — 한 row = 한 (notification × receiver × channel) 조합
 * Notification 의 메타 + Delivery 의 receiver 컨텍스트를 함께 노출
 */
public record UserNotificationResponse(

        // delivery id (읽음 처리 등의 액션 대상)
        Long deliveryId,

        // notification id (단건 조회용)
        String notificationId,

        NotificationType type,
        String referenceType,
        String referenceId,

        Channel channel,
        DeliveryState state,
        Map<String, Object> payload,

        Instant sentAt,
        Instant readAt,
        Instant createdAt
) {
}
