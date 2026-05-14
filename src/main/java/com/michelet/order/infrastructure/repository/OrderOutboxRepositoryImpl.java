package com.michelet.order.infrastructure.repository;

import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.model.OutboxStatus;
import com.michelet.order.domain.repository.OrderOutboxRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    @Override
    public Optional<OrderOutbox> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<OrderOutbox> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status) {
        return jpaRepository.findTop50ByStatusOrderByCreatedAtAsc(status);
    }
}
