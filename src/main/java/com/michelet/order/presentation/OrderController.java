package com.michelet.order.presentation;

import com.michelet.order.application.OrderCommandService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCommandService orderCommandService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "success", true,
            "data", orderCommandService.checkHealth(),
            "message", "Order Command Service is running"
        );
    }
}
