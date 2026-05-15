package com.michelet.order.infrastructure.client;

import com.michelet.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", url = "${client.inventory-service.url}")
public interface InventoryClient {
    @PostMapping("/internal/stocks/reserve")
    ApiResponse<Void> reserveStock(@RequestBody ReserveStockRequest request);

    @PostMapping("/internal/stocks/restore")
    ApiResponse<Void> restoreStock(@RequestBody RestoreStockRequest request);

    record ReserveStockRequest(UUID optionId, Integer quantity, UUID reservationId) {
    }

    record RestoreStockRequest(UUID optionId, Integer quantity, UUID reservationId) {
    }
}
