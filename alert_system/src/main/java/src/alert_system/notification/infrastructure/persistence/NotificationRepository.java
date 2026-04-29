package src.alert_system.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import src.alert_system.notification.domain.entities.Notification;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);
}
