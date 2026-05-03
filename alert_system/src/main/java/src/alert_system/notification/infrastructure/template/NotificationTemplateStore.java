package src.alert_system.notification.infrastructure.template;

import org.springframework.stereotype.Component;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.NotificationType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class NotificationTemplateStore {

    private final Map<TemplateKey, NotificationTemplateEntry> templates;

    public NotificationTemplateStore() {
        templates = new HashMap<>();
        register();
    }

    // 타입 + 채널 조합으로 템플릿 조회 메서드
    public Optional<NotificationTemplateEntry> find(final NotificationType type, final Channel channel) {
        return Optional.ofNullable(templates.get(new TemplateKey(type, channel)));
    }

    private void register() {
        put(NotificationType.ENROLLMENT_CONFIRMED, Channel.EMAIL,
                "[{courseName}] 수강 신청이 완료되었습니다",
                "안녕하세요. '{courseName}' 강의 수강 신청이 완료되었습니다.\n수강 시작일: {startDate}");

        put(NotificationType.ENROLLMENT_CONFIRMED, Channel.IN_APP,
                "수강 신청 완료",
                "'{courseName}' 강의 수강 신청이 완료되었습니다.");

        put(NotificationType.PAYMENT_CONFIRMED, Channel.EMAIL,
                "[결제 완료] {courseName}",
                "결제가 성공적으로 처리되었습니다.\n결제 금액: {amount}원\n주문번호: {orderId}");

        put(NotificationType.PAYMENT_CONFIRMED, Channel.IN_APP,
                "결제 완료",
                "'{courseName}' 강의 결제 {amount}원이 완료되었습니다.");

        put(NotificationType.LECTURE_D1_REMINDER, Channel.EMAIL,
                "[내일 강의 시작] {courseName}",
                "내일 '{courseName}' 강의가 시작됩니다.\n강의 시작 시각: {startTime}");

        put(NotificationType.LECTURE_D1_REMINDER, Channel.IN_APP,
                "내일 강의 시작 알림",
                "'{courseName}' 강의가 내일 {startTime}에 시작됩니다.");

        put(NotificationType.LECTURE_CANCELED, Channel.EMAIL,
                "[강의 취소 안내] {courseName}",
                "'{courseName}' 강의가 취소되었습니다.\n사유: {reason}\n환불은 영업일 기준 3~5일 내 처리됩니다.");

        put(NotificationType.LECTURE_CANCELED, Channel.IN_APP,
                "강의 취소",
                "'{courseName}' 강의가 취소되었습니다. 환불이 진행됩니다.");

        put(NotificationType.EXAMPLE_NOTIFICATION, Channel.EMAIL,
                "[공지] {title}",
                "{body}");

        put(NotificationType.EXAMPLE_NOTIFICATION, Channel.IN_APP,
                "{title}",
                "{body}");
    }

    private void put(final NotificationType type, final Channel channel,
                     final String titleTemplate, final String bodyTemplate) {
        templates.put(new TemplateKey(type, channel), new NotificationTemplateEntry(titleTemplate, bodyTemplate));
    }

    private record TemplateKey(NotificationType type, Channel channel) {}
}
