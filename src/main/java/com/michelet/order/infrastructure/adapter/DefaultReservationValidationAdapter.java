package com.michelet.order.infrastructure.adapter;

import com.michelet.order.application.port.out.ReservationValidationPort;
import com.michelet.order.domain.repository.OrderRepository;
import com.michelet.order.infrastructure.client.ReservationClient;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!perf") // perf 환경이 아닐 때 빈으로 등록
@RequiredArgsConstructor
public class DefaultReservationValidationAdapter implements ReservationValidationPort {

    private final OrderRepository orderRepository;
    private final ReservationClient reservationClient;

    @Override
    public LocalDate validateAndGetDate(UUID reservationId, UUID userId, UUID restaurantId) {
        // 1. 로컬 DB 중복 주문 방지 (UK)
        if (orderRepository.existsByReservationId(reservationId)) {
            throw new IllegalStateException("해당 예약으로 이미 생성된 주문이 존재합니다.");
        }

        // 2. 예약 서비스 검증 (Feign)
        var resResponse = reservationClient.verifyReservation(userId, restaurantId);
        if (resResponse == null || resResponse.data() == null || !resResponse.data().isValid()) {
            throw new IllegalArgumentException("유효한 예약 내역을 찾을 수 없거나 이미 처리된 예약입니다.");
        }

        if (resResponse.data().reservationId() == null
            || !resResponse.data().reservationId().equals(reservationId)) {
            throw new IllegalArgumentException("요청된 예약 ID가 유효한 예약 정보와 일치하지 않습니다.");
        }
        if (resResponse.data().reservationDate() == null) {
            throw new IllegalArgumentException("유효한 예약 날짜가 존재하지 않습니다.");
        }

        return resResponse.data().reservationDate();
    }
}
