package com.michelet.order.application;

import com.michelet.order.domain.model.OrderOutbox;
import com.michelet.order.domain.model.OutboxStatus;
import com.michelet.order.infrastructure.repository.JpaOrderOutboxRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxScheduler {

    private final JpaOrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${order.kafka.topic.stock-restore:stock.restored}")
    private String stockRestoreTopic;

    // 5초마다 주기적으로 실행
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutboxEvents() {
        // 1. DB에서 INIT 상태인 이벤트 긁어오기
        List<OrderOutbox> pendingEvents = outboxRepository.findByStatus(OutboxStatus.INIT);
        if (pendingEvents.isEmpty()) {
            return; // 처리할 게 없으면 조용히 턴 종료
        }

        log.info("[Order Outbox Scheduler] {}개의 미발행 이벤트를 찾아 Kafka 전송을 시도합니다.", pendingEvents.size());

        for (OrderOutbox event : pendingEvents) {
            try {
                // 2. 이벤트 타입에 따라 카프카 토픽명 결정
                String topic = resolveTopic(event.getEventType());

                // 3. Kafka 전송 및 동기식 대기
                // 비동기로 쏘고 바로 넘어가면, 카프카 서버가 터져서 못 받았는데도 DB는 PUBLISHED로 바뀌는 사고 발생 가능
                // => .get(3, TimeUnit.SECONDS)를 통해 브로커의 확실한 수신 응답(ACK)을 최대 3초간 기다림
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .get(3, TimeUnit.SECONDS);

                // 4. 전송에 완벽히 성공했을 때만 상태를 PUBLISHED로 변경 (JPA 더티 체킹으로 자동 UPDATE)
                event.markAsPublished();
                log.info("[Order Outbox Scheduler] 이벤트 발행 성공! Outbox ID: {}", event.getId());

            } catch (Exception e) {
                // 5. 카프카가 죽어있거나 네트워크 에러가 나면 여기서 잡힘
                // 예외를 밖으로 던지지 않고 여기서 먹어버림으로써, 다음 루프의 이벤트는 계속 처리하도록 보호함
                // (이번에 실패한 이벤트는 상태가 여전히 INIT이므로, 5초 뒤 스케줄러가 다시 긁어와서 재시도함 (At-Least-Once 보장))
                log.error("[Order Outbox Scheduler] 이벤트 발행 실패. 다음 주기에 재시도합니다. Outbox ID: {}", event.getId(), e);
            }
        }
    }

    private String resolveTopic(String eventType) {
        if ("STOCK_RESTORE".equals(eventType)) {
            return stockRestoreTopic; // 인벤토리 서버가 구독할 토픽명
        }
        return "order.unknown.event";
    }
}
