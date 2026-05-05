package com.michelet.order.presentation;

import com.michelet.common.auth.webmvc.context.UserContextHolder;
import com.michelet.common.response.ApiResponse;
import com.michelet.order.application.OrderCommandService;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.presentation.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCommandService orderCommandService;

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok(orderCommandService.checkHealth());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResult>> createOrder(
        @RequestBody @Valid CreateOrderRequest request
    ) {
        if (UserContextHolder.get() == null || UserContextHolder.get().userId() == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다.");
        }

        String userIdStr = UserContextHolder.get().userId();

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userIdStr); // 정상적인 UUID 포맷일 경우
        } catch (IllegalArgumentException e) {
            userUuid = UUID.nameUUIDFromBytes(userIdStr.getBytes(StandardCharsets.UTF_8)); // 일반 문자열일 경우의 폴백
        }

        OrderResult result = orderCommandService.createOrder(request.toCommand(userUuid));

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }
}
