package com.michelet.order.infrastructure.repository;

import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderOutboxRepositoryImpl implements OrderOutboxRepository {

    private final JpaOrderOutboxRepository jpaRepository;

    @Override
    public OrderOutbox save(OrderOutbox outbox) {
        return jpaRepository.save(outbox);
    }
}
