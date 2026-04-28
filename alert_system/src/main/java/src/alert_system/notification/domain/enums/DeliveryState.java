package src.alert_system.notification.domain.enums;

public enum DeliveryState {
    // 발송 대기
    PENDING(false, false),

    // 발송 처리중
    PROCESSING(false, false),

    // 발송 완료
    SENT(true, false),

    // 발송 실패
    FAILED(false, true),

    // 발송 불가(재시도 불가,종료)
    DEAD(true, false);

    private final boolean terminal;
    private final boolean retryable;

    DeliveryState(boolean terminal, boolean retryable) {
        this.terminal = terminal;
        this.retryable = retryable;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
