package com.michelet.order.application.dto;

import java.util.UUID;

public record OrderApprovedMessage(UUID reservationId) {
    public OrderApprovedMessage {
        if (reservationId == null) {
            throw new IllegalArgumentException("메시지 파싱 오류: reservationId는 null일 수 없습니다.");
        }
    }
}
