package com.michelet.order.application.dto;

import java.util.UUID;

// Outbox의 payload(json)로 직렬화될 객체
public record StockRestoreEventPayload(
    UUID optionId,
    Integer quantity
) {
}
