package com.michelet.order.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeadLetterConsumer {

    @KafkaListener(
        topics = {
            "${order.kafka.topic.approved:order.approved}.DLT",
            "${order.kafka.topic.rejected:order.rejected}.DLT"
        },
        groupId = "${spring.kafka.consumer.group-id:order-service-consumer}-dlt",
        containerFactory = "dltListenerContainerFactory"
    )
    public void consumeOrderDLT(ConsumerRecord<String, String> record) {
        String originalTopic = extractHeaderAsString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        String exceptionMessage = extractHeaderAsString(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);

        log.error("[CRITICAL ALERT] 오더 DLT 에러 메시지 격리 수신\n원본 토픽: {}\n에러 원인: {}\n데이터: {}",
            originalTopic, exceptionMessage, record.value() != null ? record.value() : "데이터 없음");
    }

    private String extractHeaderAsString(ConsumerRecord<String, String> record, String headerKey) {
        Header header = record.headers().lastHeader(headerKey);
        return (header != null && header.value() != null) ? new String(header.value(), StandardCharsets.UTF_8)
            : "알 수 없음";
    }
}
