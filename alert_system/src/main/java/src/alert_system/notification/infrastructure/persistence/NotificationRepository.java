package src.alert_system.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import src.alert_system.notification.domain.entities.Notification;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT DISTINCT n FROM Notification n
            LEFT JOIN FETCH n.deliveries
            WHERE n.id = :id
            """)
    Optional<Notification> findByIdWithDeliveries(@Param("id") String id);

    @Query("""
            SELECT DISTINCT n FROM Notification n
            LEFT JOIN FETCH n.deliveries
            WHERE n.idempotencyKey = :key
            """)
    Optional<Notification> findByIdempotencyKeyWithDeliveries(@Param("key") String idempotencyKey);
}
