package com.michelet.order.application.port.out;

import java.time.LocalDate;
import java.util.UUID;

public interface ReservationValidationPort {
    /**
     * 예약을 검증하고 정상일 경우 방문 예정일(LocalDate)을 반환함
     */
    LocalDate validateAndGetDate(UUID reservationId, UUID userId, UUID restaurantId);
}
