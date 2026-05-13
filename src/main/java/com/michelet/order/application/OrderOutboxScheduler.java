package com.michelet.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michelet.order.application.dto.StockRestoreEventPayload;
import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.model.OutboxStatus;
import com.michelet.order.domain.repository.OrderOutboxRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    @Value("${order.kafka.topic.stock-restore:stock.restored}")
    private String stockRestoreTopic;

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

                // Kafka 전송 및 동기식 대기
                // 비동기로 쏘고 바로 넘어가면, 카프카 서버가 터져서 못 받았는데도 DB는 PUBLISHED로 바뀌는 사고 발생 가능
                //   => .get(3, TimeUnit.SECONDS)를 통해 브로커의 확실한 수신 응답(ACK)을 최대 3초간 기다림
                // 복원된 객체를 보내야 JsonSerializer가 __TypeId__를 정확히 세팅함
                kafkaTemplate.send(topic, event.getAggregateId(), originalEventObject)
                    .get(3, TimeUnit.SECONDS);

                // 4. 전송에 완벽히 성공했을 때만 상태를 PUBLISHED로 변경 (JPA 더티 체킹으로 자동 UPDATE)
                // Helper를 호출하여 새로운 독립 트랜잭션 내에서 상태 변경 수행
                orderOutboxHelper.markAsPublished(event.getId());
                log.info("[Order Outbox Scheduler] 이벤트 발행 성공! Outbox ID: {}", event.getId());

            } catch (ObjectOptimisticLockingFailureException oole) {
                // 다중 스케줄러 환경에서 동시 접근 시 발생. 한쪽 서버가 먼저 처리했으므로 안전하게 무시.
                log.info("[Order Outbox Scheduler] 이미 처리된 이벤트입니다 (낙관적 락 충돌). Outbox ID: {}", event.getId());
            } catch (Exception e) {
                // 5. 카프카가 죽어있거나 네트워크 에러가 나면 여기서 잡힘
                // 예외를 밖으로 던지지 않고 여기서 먹어버림으로써, 다음 루프의 이벤트는 계속 처리하도록 보호함
                // (이번에 실패한 이벤트는 상태가 여전히 INIT이므로, 5초 뒤 스케줄러가 다시 긁어와서 재시도함 (At-Least-Once 보장))
                log.error("[Order Outbox Scheduler] 이벤트 발행 실패. 다음 주기에 재시도합니다. Outbox ID: {}", event.getId(), e);
            }
        }
    }

    // JSON 문자열을 원래 DTO 클래스로 변환
    private Object deserializePayload(String eventType, String jsonPayload) throws Exception {
        return switch (eventType) {
            case "STOCK_RESTORE", "STOCK_RESTORED" ->
                objectMapper.readValue(jsonPayload, StockRestoreEventPayload.class);
            // 취소나 생성 등 다른 이벤트가 추가되면 여기에 case를 늘려가면 됨
            default -> jsonPayload;
        };
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "STOCK_RESTORE", "STOCK_RESTORED" -> stockRestoreTopic;
            default -> "order.unknown.event";
        };
    }
}
