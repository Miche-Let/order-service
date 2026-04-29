package com.michelet.order.presentation;

import com.michelet.common.response.ApiResponse;
import com.michelet.order.application.OrderCommandService;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.presentation.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
        @RequestHeader("X-User-Id") UUID userId,
        @RequestBody @Valid CreateOrderRequest request
    ) {
        /*
         * TODO: 보안 강화를 위해 헤더 대신 @AuthenticationPrincipal 사용 고려
         * 1. 유저 서비스 및 게이트웨이 JWT 설정 완료 후 변경
         * 2. 변경 시 OrderControllerTest 코드도 인증 객체를 주입하도록 수정 필요
         */
        OrderResult result = orderCommandService.createOrder(request.toCommand(userId));
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED) // 201 반환
            .body(ApiResponse.ok(result));
    }

//    @PostMapping
//    public ResponseEntity<ApiResponse<OrderResult>> createOrder(
//        @AuthenticationPrincipal String userId, // 인증 필터에서 저장한 Principal(userId)을 직접 사용
//        @RequestBody @Valid CreateOrderRequest request
//    ) {
//        // 헤더 값이 아닌 인증 컨텍스트의 userId를 전달
//        OrderResult result = orderCommandService.createOrder(request.toCommand(UUID.fromString(userId)));
//
//        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
//            .body(ApiResponse.ok(result));
//    }
}
