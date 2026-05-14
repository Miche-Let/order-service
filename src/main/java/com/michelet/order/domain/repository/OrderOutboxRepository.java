package com.michelet.order.domain.repository;

import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.model.OutboxStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderOutboxRepository {
    OrderOutbox save(OrderOutbox outbox);

    Optional<OrderOutbox> findById(UUID id);
    
    List<OrderOutbox> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
