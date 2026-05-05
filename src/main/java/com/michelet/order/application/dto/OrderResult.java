package com.michelet.order.application.dto;

import java.util.UUID;

public record OrderResult(
    UUID orderId,
    String status,
    String orderName
) {
}
