package com.michelet.order.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
    UUID userId,
    UUID reservationId,
    UUID restaurantId,
    String orderName,
    LocalDate reservedDate,
    String receivingMethod,
    LocalDateTime expiredAt,
    List<OrderItemCommand> items
) {
    public record OrderItemCommand(UUID optionId, String productName, BigDecimal orderPrice, Integer quantity) {
    }
}
