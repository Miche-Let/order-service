package com.michelet.order.application;

import com.michelet.order.application.dto.OrderCreatedEventPayload;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStore {

    private final OrderRepository orderRepository;
    private final OrderOutboxHelper orderOutboxHelper;

    /**
     * 주문을 DB에 저장함 (네트워크 통신 없이 순수 DB 작업만 수행)
     */
    @Transactional
    public Order saveOrder(Order order) {
        return orderRepository.save(order);
    }

    /**
     * 주문 저장과 Outbox 생성을 하나의 트랜잭션으로 묶음 - 비동기 Saga 패턴의 시작점: PENDING 상태의 주문과 카프카 발행을 위한 ORDER_CREATED 이벤트를 동시 적재
     */
    @Transactional
    public Order saveOrderAndOutbox(
        Order order,
        OrderCreatedEventPayload payload
    ) {
        Order savedOrder = orderRepository.save(order);

        orderOutboxHelper.append(
            "ORDER",
            order.getReservationId().toString(),
            "ORDER_CREATED",
            payload
        );

        return savedOrder;
    }
}
