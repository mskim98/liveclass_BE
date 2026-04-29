package src.alert_system.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import src.alert_system.notification.domain.enums.DeliveryState;
import src.alert_system.notification.domain.entities.NotificationDelivery;

import java.time.Instant;
import java.util.List;

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

    List<NotificationDelivery> findAllByNotification_Id(String notificationId);
}
