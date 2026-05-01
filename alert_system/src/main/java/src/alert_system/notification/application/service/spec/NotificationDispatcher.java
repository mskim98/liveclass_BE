package src.alert_system.notification.application.service.spec;

public interface NotificationDispatcher {

    // 단일 발송단위 디스패치
    void dispatch(Long deliveryId);
}
