package com.michelet.order.application.dto;

import java.util.Objects;
import java.util.UUID;

// Outbox의 payload(json)로 직렬화될 객체
public record StockRestoreEventPayload(
    UUID eventId, // Consumer가 중복을 판별할 고유 식별자 (멱등성 키)
    UUID optionId,
    Integer quantity
) {
    public StockRestoreEventPayload {
        Objects.requireNonNull(eventId, "eventId는 null일 수 없습니다");
        Objects.requireNonNull(optionId, "optionId는 null일 수 없습니다");

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity는 1 이상의 양수여야 합니다.");
        }
    }
}
