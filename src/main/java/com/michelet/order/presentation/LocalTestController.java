package com.michelet.order.presentation;

import com.michelet.common.auth.feign.internal.InternalTokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequiredArgsConstructor
public class LocalTestController {

    private final InternalTokenIssuer tokenIssuer;

    /**
     * 테스트용 - .http 스크립트에서 내부 API를 찌르기 위한 JWT 토큰 발급
     */
    @GetMapping("/test/internal-token")
    public String getInternalToken(
        @RequestParam(defaultValue = "order-service") String audience
    ) {
        return tokenIssuer.issue(audience);
    }
}
