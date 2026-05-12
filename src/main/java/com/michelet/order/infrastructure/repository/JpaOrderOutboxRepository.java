package com.michelet.order.infrastructure.repository;

import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.model.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {
    // 발행 대기(INIT) 상태인 이벤트만 긁어오기
    List<OrderOutbox> findByStatus(OutboxStatus status);
}
