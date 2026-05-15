package com.michelet.order.infrastructure.messaging;

import com.michelet.order.application.OrderCommandService;
import com.michelet.order.application.dto.OrderApprovedMessage;
import com.michelet.order.application.dto.OrderRejectedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final OrderCommandService orderCommandService;

    @KafkaListener(
        topics = "${order.kafka.topic.approved:order.approved}",
        groupId = "${spring.kafka.consumer.group-id:order-service-consumer}"
    )
    public void consumeOrderApproved(OrderApprovedMessage msg) {
        log.info("[Kafka Consumer] 인벤토리 승인 메시지 수신: reservationId={}", msg.reservationId());
        try {
            orderCommandService.approveOrder(msg.reservationId());
        } catch (IllegalArgumentException e) {
            log.error("[Kafka Consumer] 비즈니스/검증 룰 위반 에러 (DLT 직행). reservationId: {}", msg.reservationId(), e);
            throw e;
        } catch (Exception e) {
            log.error("[Kafka Consumer] 상태 업데이트 중 일시적 오류 (재시도 대상). reservationId: {}", msg.reservationId(), e);
            throw new RuntimeException("주문 승인 처리 실패", e);
        }
    }

    @KafkaListener(
        topics = "${order.kafka.topic.rejected:order.rejected}",
        groupId = "${spring.kafka.consumer.group-id:order-service-consumer}"
    )
    public void consumeOrderRejected(OrderRejectedMessage msg) {
        log.warn("[Kafka Consumer] 인벤토리 거절 메시지 수신 (재고 부족 등): reservationId={}, 사유={}", msg.reservationId(),
            msg.reason());
        try {
            orderCommandService.rejectOrder(msg.reservationId(), msg.reason());
        } catch (IllegalArgumentException e) {
            log.error("[Kafka Consumer] 비즈니스/검증 룰 위반 에러 (DLT 직행). reservationId: {}", msg.reservationId(), e);
            throw e;
        } catch (Exception e) {
            log.error("[Kafka Consumer] 상태 업데이트 중 일시적 오류 (재시도 대상). reservationId: {}", msg.reservationId(), e);
            throw new RuntimeException("주문 거절 처리 실패", e);
        }
    }
}
