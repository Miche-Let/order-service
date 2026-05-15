package com.michelet.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michelet.order.application.dto.StockRestoreEventPayload;
import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.model.OutboxStatus;
import com.michelet.order.domain.repository.OrderOutboxRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxScheduler {

    private final OrderOutboxRepository outboxRepository;
    private final OrderOutboxHelper orderOutboxHelper; // 트랜잭션 분리를 위한 Helper 주입
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // JSON 문자열을 객체로 복원하기 위한 매퍼 주입
    private final ObjectMapper objectMapper;

    @Value("${order.kafka.topic.stock-restore:order.stock-restore.requested}")
    private String stockRestoreTopic;

    @Value("${order.kafka.topic.created:order.created}")
    private String orderCreatedTopic;

    // 5초마다 주기적으로 실행
    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        // OOM 방지 및 순서 보장을 위해 Top N 배치 조회 사용
        // 1. DB에서 INIT 상태인 이벤트 긁어오기
        List<OrderOutbox> pendingEvents = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT);
        if (pendingEvents.isEmpty()) {
            return; // 처리할 게 없으면 조용히 턴 종료
        }

        log.info("[Order Outbox Scheduler] {}개의 미발행 이벤트를 찾아 Kafka 전송을 시도합니다.", pendingEvents.size());

        for (OrderOutbox event : pendingEvents) {
            try {
                // 2. 이벤트 타입에 따라 카프카 토픽명 결정
                String topic = resolveTopic(event.getEventType());

                // String(JSON)을 다시 원본 Event 객체로 복원
                Object originalEventObject = deserializePayload(event.getEventType(), event.getPayload());

                // 동기 대기(.get) 제거 후 비동기 콜백 적용
                // 복원된 객체를 보내야 JsonSerializer가 __TypeId__를 정확히 세팅함
                kafkaTemplate.send(topic, event.getAggregateId(), originalEventObject)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            try {
                                orderOutboxHelper.markAsPublished(event.getId());
                                log.info("[Order Outbox Scheduler] 이벤트 발행 성공! Outbox ID: {}", event.getId());
                            } catch (ObjectOptimisticLockingFailureException oole) {
                                log.info("[Order Outbox Scheduler] 낙관적 락 방어 (동시성 경합). Outbox ID: {}", event.getId());
                            } catch (Exception updateEx) {
                                log.error("[Order Outbox Scheduler] DB 상태 업데이트 실패. Outbox ID: {}", event.getId(),
                                    updateEx);
                            }
                        } else {
                            log.error("[Order Outbox Scheduler] 카프카 이벤트 발행 실패. Outbox ID: {}", event.getId(), ex);
                            safeHandleFailure(event.getId()); // 실패 카운트 로직 실행
                        }
                    });

            } catch (Exception e) {
                log.error("[Order Outbox Scheduler] 전송 준비 중 오류 발생. Outbox ID: {}", event.getId(), e);
                safeHandleFailure(event.getId());
            }
        }
    }

    private void safeHandleFailure(UUID eventId) {
        try {
            orderOutboxHelper.handleFailure(eventId);
        } catch (ObjectOptimisticLockingFailureException oole) {
            log.info("[Order Outbox Scheduler] 실패 마킹 중 낙관적 락 방어. Outbox ID: {}", eventId);
        } catch (Exception e) {
            log.error("[Order Outbox Scheduler] 실패 상태 업데이트 중 예외 발생. Outbox ID: {}", eventId, e);
        }
    }

    // JSON 문자열을 원래 DTO 클래스로 변환
    private Object deserializePayload(String eventType, String jsonPayload) throws Exception {
        return switch (eventType) {
            case "STOCK_RESTORE", "STOCK_RESTORED" ->
                objectMapper.readValue(jsonPayload, StockRestoreEventPayload.class);
            case "ORDER_CREATED" ->
                objectMapper.readValue(jsonPayload, com.michelet.order.application.dto.OrderCreatedEventPayload.class);
            // 취소나 생성 등 다른 이벤트가 추가되면 여기에 case를 늘려가면 됨
            default -> {
                log.warn("등록되지 않은 알 수 없는 이벤트 타입입니다: {}", eventType);
                throw new IllegalArgumentException("Unknown event type: " + eventType);
            }
        };
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "STOCK_RESTORE", "STOCK_RESTORED" -> stockRestoreTopic;
            case "ORDER_CREATED" -> orderCreatedTopic;
            // 취소나 생성 등 다른 이벤트가 추가되면 여기에 case를 늘려가면 됨
            default -> "order.unknown.event";
        };
    }
}
