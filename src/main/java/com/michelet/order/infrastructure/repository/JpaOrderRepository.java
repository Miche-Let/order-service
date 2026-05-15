package com.michelet.order.infrastructure.repository;

import com.michelet.order.domain.model.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOrderRepository extends JpaRepository<Order, UUID> {
    boolean existsByReservationId(UUID reservationId);

    Optional<Order> findByReservationId(UUID reservationId);
}
