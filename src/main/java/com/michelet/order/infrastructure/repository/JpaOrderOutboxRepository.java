package com.michelet.order.infrastructure.repository;

import com.michelet.order.domain.model.OrderOutbox;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {
}
