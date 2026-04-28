package com.michelet.order.presentation.dto;

import com.michelet.order.application.dto.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
    @NotNull UUID reservationId,
    @NotNull UUID restaurantId,
    @NotBlank String orderName,
    @NotNull LocalDate reservedDate,
    @NotBlank String receivingMethod,
    @NotNull LocalDateTime expiredAt,
    @NotEmpty List<@Valid OrderItemRequest> items
) {
    public record OrderItemRequest(
        @NotNull UUID optionId,
        @NotBlank String productName,
        @NotNull @PositiveOrZero BigDecimal orderPrice,
        @NotNull @Min(1) Integer quantity
    ) {
    }

    public CreateOrderCommand toCommand(UUID userId) {
        return new CreateOrderCommand(
            userId,
            reservationId,
            restaurantId,
            orderName,
            reservedDate,
            receivingMethod,
            expiredAt,
            items.stream().map(it -> new CreateOrderCommand.OrderItemCommand(
                it.optionId(), it.productName(), it.orderPrice(), it.quantity()
            )).toList()
        );
    }
}
