package com.michelet.order.infrastructure.repository;

import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.model.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

    // OOM 방지 및 순서 보장을 위해 Top N개 생성순 조회로 변경
    List<OrderOutbox> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
