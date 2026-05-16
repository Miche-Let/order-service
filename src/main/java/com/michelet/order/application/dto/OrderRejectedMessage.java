package com.michelet.order.application.dto;

import java.util.UUID;

public record OrderRejectedMessage(UUID reservationId, String reason) {
    public OrderRejectedMessage {
        if (reservationId == null) {
            throw new IllegalArgumentException("메시지 파싱 오류: reservationId는 null일 수 없습니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("메시지 파싱 오류: 거절 사유는 필수입니다.");
        }
    }
}
