package src.alert_system.notification.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import src.alert_system.notification.domain.enums.DeliveryErrorCode;

import java.time.Instant;

@Entity
@Table(name = "notification_delivery_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NotificationDeliveryFail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private NotificationDelivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code")
    private DeliveryErrorCode errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private NotificationDeliveryFail(DeliveryErrorCode errorCode,
                                     String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    // 양방향 연관관계 설정 메서드 (NotificationDelivery.recordFailure 에서만 호출)
    void assignTo(final NotificationDelivery delivery) {
        this.delivery = delivery;
    }
}
