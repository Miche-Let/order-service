package com.michelet.order.domain.repository;

import com.michelet.order.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(UUID id);

    boolean existsByReservationId(UUID reservationId);
}
