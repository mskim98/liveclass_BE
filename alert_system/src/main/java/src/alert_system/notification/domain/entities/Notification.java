package src.alert_system.notification.domain.entities;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import src.alert_system.common.entities.BaseEntity;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.NotificationType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id // ULID : 시간순 정렬 + 분산 환경 안전성 확보용
    @Column(length = 26, updatable = false, nullable = false)
    private String id;

    // 멱등키 — 동일 이벤트 중복 등록 방지용
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    // 알림 종류
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    // 자원의 유래 : 모호하여 nullable, String 타입 -> 구체화시 ENUM으로 변경
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    // 유래된 자원의 id : 모호하여 nullable, String 타입 -> 구체화시 타입변경
    @Column(name = "reference_id", length = 64)
    private String referenceId;

    // 알림의 내용 : json 타입
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    // 발송 예약시간, 즉시 발송시 null
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    // 수신자 × 채널 fan-out 결과 — cascade 로 함께 영속화, LAZY 로 불필요 fetch 차단
    @OneToMany(mappedBy = "notification",
               cascade = {CascadeType.PERSIST, CascadeType.MERGE},
               fetch = FetchType.LAZY,
               orphanRemoval = true)
    private List<NotificationDelivery> deliveries = new ArrayList<>();

    @Builder
    private Notification(String idempotencyKey,
                         NotificationType type,
                         String referenceType,
                         String referenceId,
                         Map<String, Object> payload,
                         Instant scheduledAt) {
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.payload = payload;
        this.scheduledAt = scheduledAt;
    }

    // ULID 자동 생성 메서드
    @PrePersist
    private void generateId() {
        if (this.id == null) {
            this.id = UlidCreator.getMonotonicUlid().toString();
        }
    }

    // 발송 단위 추가 메서드 (양방향 연관관계 안전 설정)
    public void addDelivery(NotificationDelivery delivery) {
        this.deliveries.add(delivery);
        delivery.assignTo(this);
    }

    // 알림 정체성 필드들로 멱등키 파생 메서드 (도메인이 자기 정체성 결정 — application 레이어 의존 없음)
    public static String deriveIdempotencyKey(final NotificationType type,
                                              final String referenceType,
                                              final String referenceId,
                                              final List<UUID> receiverIds,
                                              final List<Channel> channels) {
        final String sortedReceivers = receiverIds.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
        final String sortedChannels = channels.stream()
                .map(Channel::name)
                .sorted()
                .collect(Collectors.joining(","));

        final String raw = String.join("|",
                type.name(),
                nullToEmpty(referenceType),
                nullToEmpty(referenceId),
                sortedReceivers,
                sortedChannels);

        return sha256Hex(raw);
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    // SHA-256 hex 변환 메서드
    private static String sha256Hex(final String raw) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (final byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘 미지원", e);
        }
    }
}
