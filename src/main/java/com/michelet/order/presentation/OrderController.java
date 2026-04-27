package com.michelet.order.presentation;

import com.michelet.common.response.ApiResponse;
import com.michelet.order.application.OrderCommandService;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.presentation.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<ApiResponse<OrderResult>> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        OrderResult result = orderCommandService.createOrder(request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
