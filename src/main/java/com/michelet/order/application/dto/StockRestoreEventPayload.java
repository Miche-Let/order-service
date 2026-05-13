package com.michelet.order.application.dto;

import java.util.UUID;

// Outbox의 payload(json)로 직렬화될 객체
public record StockRestoreEventPayload(
    UUID eventId, // Consumer가 중복을 판별할 고유 식별자 (멱등성 키)
    UUID optionId,
    Integer quantity
) {
}
