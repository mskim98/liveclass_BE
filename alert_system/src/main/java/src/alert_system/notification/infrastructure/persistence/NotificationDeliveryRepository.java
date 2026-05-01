package src.alert_system.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryState;
import src.alert_system.notification.domain.entities.NotificationDelivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    @Query(value = """
            SELECT * FROM notification_delivery
            WHERE state IN ('PENDING', 'FAILED')
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationDelivery> lockPendingForDispatch(@Param("now") Instant now,
                                                      @Param("limit") int limit);

    List<NotificationDelivery> findAllByStateAndProcessingStartedAtBefore(DeliveryState state,
                                                                         Instant threshold);

    @Query(value = """
            SELECT * FROM notification_delivery
            WHERE state = 'PROCESSING'
              AND processing_started_at <= :threshold
            ORDER BY processing_started_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationDelivery> lockStuckProcessing(@Param("threshold") Instant threshold,
                                                   @Param("limit") int limit);

    List<NotificationDelivery> findAllByNotification_Id(String notificationId);

    @Query("""
            SELECT d FROM NotificationDelivery d
              JOIN FETCH d.notification n
             WHERE d.receiverId = :userId
               AND d.state = 'SENT'
               AND (:channel IS NULL OR d.channel = :channel)
               AND (
                    :readFilter IS NULL
                    OR (:readFilter = TRUE  AND d.readAt IS NOT NULL)
                    OR (:readFilter = FALSE AND d.readAt IS NULL)
                   )
             ORDER BY d.sentAt DESC
            """)
    List<NotificationDelivery> findUserDeliveries(@Param("userId") UUID userId,
                                                  @Param("channel") Channel channel,
                                                  @Param("readFilter") Boolean readFilter);
}
