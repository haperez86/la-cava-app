package co.lacava.gestion.notifications.infrastructure;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.lacava.gestion.notifications.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtAscIdAsc(Long userId);

    /** Marca leídos los avisos pendientes de un pedido (entrega, CA3). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.orderId = :orderId and n.readAt is null")
    int markOrderRead(@Param("orderId") Long orderId, @Param("now") Instant now);
}
