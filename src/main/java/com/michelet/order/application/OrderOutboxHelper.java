package com.michelet.order.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxHelper {

    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // 1. 주문 취소 등 정상 흐름에서 사용 (현재 트랜잭션에 합류)
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payloadObj
    ) {
        saveOutbox(aggregateType, aggregateId, eventType, payloadObj);
    }

    // 2. 주문 생성 실패 시 보상 트랜잭션에서 사용 (기존 롤백 트랜잭션과 분리되어 무조건 커밋됨)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendCompensation(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payloadObj
    ) {
        saveOutbox(aggregateType, aggregateId, eventType, payloadObj);
    }

    private void saveOutbox(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payloadObj
    ) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payloadObj);
            OrderOutbox outbox = OrderOutbox.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .build();
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Outbox 페이로드 직렬화 실패. aggregateId={}, eventType={}", aggregateId, eventType, e);
            throw new RuntimeException("Outbox 이벤트 생성 중 오류가 발생했습니다.", e);
        }
    }
}
