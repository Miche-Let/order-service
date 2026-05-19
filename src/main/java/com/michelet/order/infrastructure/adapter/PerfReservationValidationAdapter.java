package com.michelet.order.infrastructure.adapter;

import com.michelet.order.application.port.out.ReservationValidationPort;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"perf", "prod"}) // perf 환경일 때(+ 임시로 prod에서도)만 빈으로 등록
public class PerfReservationValidationAdapter implements ReservationValidationPort {

    private static final LocalDate FIXED_PERF_DATE = LocalDate.of(2026, 12, 31);

    // 서버가 켜질 때 딱 한 번만 로그를 찍어서 로그 오버헤드 제거
    @PostConstruct
    public void init() {
        log.warn("[Perf Mode] PerfReservationValidationAdapter 활성화됨 - 모든 예약 검증을 우회하고 고정 날짜 반환");
    }

    @Override
    public LocalDate validateAndGetDate(UUID reservationId, UUID userId, UUID restaurantId) {
        return FIXED_PERF_DATE;
    }
}
