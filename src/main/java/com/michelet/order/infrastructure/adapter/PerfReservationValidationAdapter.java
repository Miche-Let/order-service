package com.michelet.order.infrastructure.adapter;

import com.michelet.order.application.port.out.ReservationValidationPort;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("perf") // perf 환경일 때만 빈으로 등록
public class PerfReservationValidationAdapter implements ReservationValidationPort {

    @Override
    public LocalDate validateAndGetDate(UUID reservationId, UUID userId, UUID restaurantId) {
        log.warn("[Perf Mode] 예약 검증을 우회하고 가짜 날짜를 반환합니다. ReservationId: {}", reservationId);
        // 부하테스트를 위해 예약을 검증하지 않고 고정 날짜 반환
        return LocalDate.of(2026, 12, 31);
    }
}
