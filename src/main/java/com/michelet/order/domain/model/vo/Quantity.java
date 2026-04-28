package com.michelet.order.domain.model.vo;

public record Quantity(Integer value) {
    public Quantity {
        if (value == null || value <= 0) { // 0 이하일 경우 예외 발생
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
        }
    }
}
