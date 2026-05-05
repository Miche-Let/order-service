package com.michelet.order.infrastructure.client;

import com.michelet.common.response.ApiResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "catalog-service", url = "${client.catalog-service.url}")
public interface CatalogClient {
    @GetMapping("/internal/options/{optionId}/validate")
    ApiResponse<OptionValidationResponse> validateOption(@PathVariable("optionId") UUID optionId);

    record OptionValidationResponse(UUID optionId, String name, BigDecimal totalPrice) {
    }

}
