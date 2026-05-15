package com.michelet.order.application.dto;

import java.util.List;
import java.util.UUID;

public record OrderCreatedEventPayload(
    UUID eventId,
    UUID reservationId,
    List<OrderItemPayload> items
) {
    public OrderCreatedEventPayload {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId는 null일 수 없습니다.");
        }
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId는 null일 수 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 1개 이상이어야 합니다.");
        }
    }

    public record OrderItemPayload(UUID optionId, Integer quantity) {
        public OrderItemPayload {
            if (optionId == null) {
                throw new IllegalArgumentException("optionId는 null일 수 없습니다.");
            }
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
            }
        }
    }
}
