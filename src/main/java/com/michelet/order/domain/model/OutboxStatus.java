package com.michelet.order.domain.model;

public enum OutboxStatus {
    INIT,       // 초기 생성 상태 (발행 대기)
    PUBLISHED,  // Kafka 발행 완료 상태
    FAILED      // 최대 재시도 초과로 영구 실패 (격리)
}
