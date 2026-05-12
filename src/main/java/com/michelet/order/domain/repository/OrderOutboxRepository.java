package com.michelet.order.domain.repository;

import com.michelet.order.domain.model.OrderOutbox;

public interface OrderOutboxRepository {
    OrderOutbox save(OrderOutbox outbox);
}
