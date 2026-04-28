package src.alert_system.notification.domain.enums;

public enum DeliveryEvent {
    // 발송 시작
    DISPATCH,

    // 발송 완료
    SEND_SUCCESS,

    // 발송 실패
    SEND_FAILURE,

    // 재시도 진행후 진행불가
    EXHAUST,

    // 재시도
    RETRY,

    // 발송 중 대기상태 변경(추후 구현)
    RECOVER,

    // 수동 처리(추후 구현)
    MANUAL_RETRY
}
