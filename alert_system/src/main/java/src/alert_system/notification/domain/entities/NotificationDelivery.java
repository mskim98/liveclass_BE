package src.alert_system.notification.domain.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import src.alert_system.common.entities.BaseMutableEntity;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "notification_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_notification_receiver_channel",
                columnNames = {"notification_id", "receiver_id", "channel"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends BaseMutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    // 수신자 (UUID)
    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    // 발송 채널 (단일) — 멀티 채널은 채널마다 row 분리
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    // Sender 멱등성 토큰 — 한 row 의 모든 재시도가 동일 키로 외부 시스템에 dedup 신호 전달
    @Column(name = "dispatch_key", nullable = false, unique = true, updatable = false)
    private UUID dispatchKey;

    // 외부 시스템(이메일 provider 등) 이 반환한 메시지 ID — 운영 감사 / 재발송 추적용 : 가정
    @Column(name = "external_message_id")
    private String externalMessageId;

    // 발송 상태 (상태 머신 관리 대상)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryState state;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "last_failure_reason", length = 500)
    private String lastFailureReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    // 실패 이력 (append-only) — 재시도마다 1건씩 추가
    @OneToMany(mappedBy = "delivery",
               cascade = CascadeType.ALL,
               fetch = FetchType.LAZY,
               orphanRemoval = true)
    private List<NotificationDeliveryFail> failures = new ArrayList<>();

    @Builder
    private NotificationDelivery(UUID receiverId,
                                 Channel channel,
                                 int maxRetries,
                                 Instant nextAttemptAt) {
        this.receiverId = receiverId;
        this.channel = channel;
        this.state = DeliveryState.PENDING;
        this.retryCount = 0;
        this.maxRetries = maxRetries;
        this.nextAttemptAt = nextAttemptAt;
        this.dispatchKey = UUID.randomUUID();
    }

    // 양방향 연관관계 설정 메서드 (Notification.addDelivery 에서만 호출)
    void assignTo(Notification notification) {
        this.notification = notification;
    }

    // 상태 변경 반영 메서드 (DeliveryStateTransitionService 에서만 호출, 외부 직접 호출 금지)
    public void transitionTo(DeliveryState newState,
                             Instant now,
                             String reason,
                             Instant nextAttemptAt,
                             String externalMessageId) {
        this.state = newState;

        switch (newState) {
            case PROCESSING -> this.processingStartedAt = now;
            case SENT -> {
                this.sentAt = now;
                this.processingStartedAt = null;
                this.lastFailureReason = null;
                if (externalMessageId != null) {
                    this.externalMessageId = externalMessageId;
                }
            }
            case FAILED -> {
                this.retryCount += 1;
                this.lastFailureReason = reason;
                this.processingStartedAt = null;
                this.nextAttemptAt = nextAttemptAt;
            }
            case DEAD -> {
                this.lastFailureReason = reason;
                this.processingStartedAt = null;
            }
            case PENDING -> {
                this.processingStartedAt = null;
                this.nextAttemptAt = nextAttemptAt;
            }
        }
    }

    // 인앱 알림 읽음 처리 메서드
    public void markRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }

    // 수동 재시도 진입 전 retry 카운터 초기화 메서드 (DEAD → PENDING 전이 직전 호출)
    public void resetForManualRetry() {
        this.retryCount = 0;
        this.lastFailureReason = null;
    }

    // 재시도 가능 여부 판단 기능
    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }

    // 실패 이력 추가 메서드 (양방향 연관관계 안전 설정)
    public void recordFailure(NotificationDeliveryFail failure) {
        this.failures.add(failure);
        failure.assignTo(this);
    }
}
