package com.michelet.order.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateOrderCommand(
    UUID userId,
    UUID reservationId,
    UUID restaurantId,
    String orderName,
    String receivingMethod,
    List<OrderItemCommand> items
) {
    public CreateOrderCommand {
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(reservationId, "reservationId는 필수입니다.");
        Objects.requireNonNull(restaurantId, "restaurantId는 필수입니다.");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 최소 1개 이상이어야 합니다.");
        }
        items = List.copyOf(items); // 방어적 복사(불변 리스트)
    }

    public record OrderItemCommand(UUID optionId, Integer quantity) {
        public OrderItemCommand {
            Objects.requireNonNull(optionId, "optionId는 필수입니다.");
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("quantity는 1 이상이어야 합니다.");
            }
        }
    }
}
