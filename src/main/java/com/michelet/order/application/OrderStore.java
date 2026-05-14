package com.michelet.order.application;

import com.michelet.order.application.dto.StockRestoreEventPayload;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.repository.OrderRepository;
import com.michelet.order.infrastructure.client.InventoryClient;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
     * 에러 발생 시 보상 트랜잭션(아웃박스)을 DB에 기록함 기존 트랜잭션이 에러로 롤백 마킹되었을 수 있으므로 REQUIRES_NEW로 새 트랜잭션을 연다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCompensationOutbox(UUID reservationId, List<InventoryClient.RestoreStockRequest> reservedStocks) {
        for (InventoryClient.RestoreStockRequest restoreReq : reservedStocks) {
            try {
                orderOutboxHelper.appendCompensation(
                    "ORDER",
                    reservationId.toString(),  // Order가 생성되기 전이므로 예약 ID를 Aggregate ID로 사용
                    "STOCK_RESTORE",
                    // UUID.randomUUID()를 통해 고유한 이벤트 식별자(eventId) 발급
                    new StockRestoreEventPayload(UUID.randomUUID(), restoreReq.optionId(), restoreReq.quantity())
                );
                log.info("보상 Outbox 저장 완료: 옵션 {} 재고 복구 대기", restoreReq.optionId());
            } catch (Exception outboxEx) {
                log.error(
                    "[CRITICAL ALERT] 보상 트랜잭션 Outbox 저장 실패. 수동 복구 요망! reservationId: {}, optionId: {}, quantity: {}",
                    reservationId, restoreReq.optionId(), restoreReq.quantity(), outboxEx);
            }
        }
    }
}
