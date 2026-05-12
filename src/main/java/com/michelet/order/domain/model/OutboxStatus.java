package com.michelet.order.domain.model;

public enum OutboxStatus {
    INIT,      // 초기 생성 상태 (발행 대기)
    PUBLISHED  // Kafka 발행 완료 상태
}
