package com.michelet.order.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    PENDING("결제 대기"),
    OCCUPIED("주문 생성(현장 결제 대기)"),
    COMPLETED("주문 완료"),
    CANCELED("주문 취소");

    private final String description;

    /**
     * 상태 전이 가능 여부 체크 PENDING || (현장 결제) OCCUPIED 상태에서 취소/완료가 가능해야 함
     */
    public boolean canTransitionTo(OrderStatus nextStatus) {
        if (this == PENDING || this == OCCUPIED) {
            return nextStatus == COMPLETED || nextStatus == CANCELED;
        }
        return false;
    }
}
