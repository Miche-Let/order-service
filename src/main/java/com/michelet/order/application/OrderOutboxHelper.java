package com.michelet.order.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.model.OutboxStatus;
import com.michelet.order.domain.repository.OrderOutboxRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxHelper {

    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${order.outbox.max-retries:3}")
    private int maxRetries;

    // 1. 주문 생성/취소 등 정상 흐름 (실패 시 트랜잭션 롤백 필요)
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payloadObj
    ) {
        saveOutbox(aggregateType, aggregateId, eventType, payloadObj, false);
    }

    // 2. 보상 트랜잭션 흐름 (무슨 일이 있어도 DB에 기록을 남겨야 함)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendCompensation(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payloadObj
    ) {
        saveOutbox(aggregateType, aggregateId, eventType, payloadObj, true);
    }

    // 3. 스케줄러가 카프카 전송 성공 후 상태를 바꿀 때 사용하는 개별 독립 트랜잭션
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPublished(UUID outboxId) {
        outboxRepository.findById(outboxId).ifPresentOrElse(
            outbox -> {
                // 비동기 처리 중 혹시 모를 중복 업데이트 방어- INIT -> PUBLISHED 전이만 허용
                if (outbox.getStatus() != OutboxStatus.INIT) {
                    return;
                }

                outbox.markAsPublished();
                outboxRepository.save(outbox);
            },
            () -> log.warn("[Order Outbox] 발행 성공 후 상태 변경 대상이 없습니다. outboxId={}", outboxId)
        );
    }

    // 비동기 카프카 전송 실패 시 재시도 횟수를 올리고 3회 초과 시 FAILED 처리하는 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(UUID outboxId) {
        outboxRepository.findById(outboxId).ifPresentOrElse(
            outbox -> {
                if (outbox.getStatus() != OutboxStatus.INIT) {
                    return;
                }

                outbox.incrementRetryCount();
                if (outbox.getRetryCount() >= maxRetries) {
                    outbox.markAsFailed();
                    log.error("[CRITICAL] Order Outbox 최대 재시도 횟수 초과. FAILED 상태로 마킹. 수동 조치 요망. outboxId={}", outboxId);
                }
                outboxRepository.save(outbox);
            },
            () -> log.warn("[Order Outbox] 실패 처리 대상이 없습니다. outboxId={}", outboxId)
        );
    }

    private void saveOutbox(
        String aggregateType,
        String aggregateId,
        String eventType,
        Object payloadObj,
        boolean isCompensation // 보상 트랜잭션 여부 플래그
    ) {
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType은 필수입니다.");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId는 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType은 필수입니다.");
        }
        if (payloadObj == null) {
            throw new IllegalArgumentException("payloadObj는 필수입니다.");
        }

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
            log.error("Outbox 페이로드 직렬화 실패. aggregateId={}, eventType={}, error={}",
                aggregateId, eventType, e.getMessage(), e);

            // 보상 트랜잭션인 경우 예외를 삼키고 에러 페이로드로 대체 저장하여 유실 방지
            if (isCompensation) {
                String errorPayload = String.format("{\"error\":\"serialization_failed\",\"class\":\"%s\"}",
                    payloadObj.getClass().getName());
                OrderOutbox errorOutbox = OrderOutbox.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType + "_SERIALIZATION_ERROR")
                    .payload(errorPayload)
                    .build();

                // 저장된 객체를 변수로 받아 ID를 로그에 함께 출력
                OrderOutbox savedErrorOutbox = outboxRepository.save(errorOutbox);
                log.error(
                    "[CRITICAL] 보상 트랜잭션 이벤트 직렬화 실패로 fallback 에러 이벤트를 적재했습니다. outboxId={}, aggregateId={}, eventType={}. 수동 확인이 필요합니다.",
                    savedErrorOutbox.getId(),
                    aggregateId,
                    eventType
                );
            } else {
                // 정상 흐름일 경우 예외를 던져서 트랜잭션 롤백 유도
                throw new RuntimeException("Outbox 이벤트 생성 중 오류가 발생했습니다.", e);
            }
        }
    }
}
