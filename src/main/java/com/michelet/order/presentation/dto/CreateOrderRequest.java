package com.michelet.order.presentation.dto;

import com.michelet.order.application.dto.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
    @NotNull UUID reservationId,
    @NotNull UUID restaurantId,
    String orderName,
    @NotBlank String receivingMethod,
    @NotEmpty List<@Valid OrderItemRequest> items
) {
    public record OrderItemRequest(
        @NotNull UUID optionId,
        @NotNull @Min(1) Integer quantity
    ) {
    }

    public CreateOrderCommand toCommand(UUID userId) {
        return new CreateOrderCommand(
            userId,
            reservationId,
            restaurantId,
            orderName,
            receivingMethod,
            items.stream().map(it -> new CreateOrderCommand.OrderItemCommand(
                it.optionId(), it.quantity()
            )).toList()
        );
    }
}
