package com.michelet.order.infrastructure.client;

import com.michelet.common.response.ApiResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "reservation-service", url = "${client.reservation-service.url}")
public interface ReservationClient {
    @GetMapping("/internal/reservations/verify")
    ApiResponse<ReservationValidityResponse> verifyReservation(
        @RequestParam("userId") UUID userId,
        @RequestParam("restaurantId") UUID restaurantId
    );

    record ReservationValidityResponse(
        boolean exists,
        UUID reservationId,
        LocalDate reservationDate
    ) {
        public boolean isValid() {
            return exists;
        }
    }
}
