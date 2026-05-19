package com.michelet.order.presentation;

import com.michelet.common.auth.core.annotation.RequireRole;
import com.michelet.common.auth.core.enums.UserRole;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @RequireRole({UserRole.USER, UserRole.OWNER, UserRole.MASTER})
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResult>> createOrder(
        @RequestBody @Valid CreateOrderRequest request
    ) {
        UUID userUuid = currentUserUuid();
        OrderResult result = orderCommandService.createOrder(request.toCommand(userUuid));

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    @RequireRole({UserRole.USER, UserRole.OWNER, UserRole.MASTER})
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable UUID orderId) {
        UUID userUuid = currentUserUuid();
        orderCommandService.cancelOrder(orderId, userUuid);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // NOTE: 나중에 실제 해당 레스토랑의 오너인지 검증하는 로직 추가 필요 (@CheckOwner 등 AOP 적용 예정)
    @RequireRole(UserRole.OWNER)
    @PatchMapping("/{orderId}/receive")
    public ResponseEntity<ApiResponse<Void>> receiveOrder(@PathVariable UUID orderId) {
        if (UserContextHolder.get() == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다.");
        }
        orderCommandService.receiveOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // NOTE: 나중에 테스트 완료 후 `@CheckOwner` 또는 소유자 검증 AOP 적용 필요 - 현재 인가 검증 미적용
    @RequireRole({UserRole.USER, UserRole.OWNER, UserRole.MASTER}) // 최소한의 인증(로그인 여부)만 요구하도록 추가
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResult>> getOrder(@PathVariable UUID orderId) {
        OrderResult result = orderCommandService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // NOTE: 나중에 결제 시스템 연동 완료 시 웹훅 등으로 변경 예정 + 해당 레스토랑 오너 검증 로직 추가 필요 (@CheckOwner AOP 도입)
    @RequireRole(UserRole.OWNER)
    @PatchMapping("/{orderId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeOrder(@PathVariable UUID orderId) {
        orderCommandService.completeOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * UserContextHolder에서 현재 사용자의 UUID를 파싱하여 반환
     */
    private UUID currentUserUuid() {
        if (UserContextHolder.get() == null || UserContextHolder.get().userId() == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다.");
        }

        String userIdStr = UserContextHolder.get().userId();

        try {
            return UUID.fromString(userIdStr); // 정상적인 UUID 포맷일 경우
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(userIdStr.getBytes(StandardCharsets.UTF_8)); // 일반 문자열일 경우의 폴백
        }
    }
}
