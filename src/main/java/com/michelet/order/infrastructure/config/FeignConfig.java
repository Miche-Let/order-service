package com.michelet.order.infrastructure.config;

import com.michelet.common.auth.feign.internal.InternalTokenIssuer;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FeignConfig {

    private final InternalTokenIssuer internalTokenIssuer;

    @Bean
    public RequestInterceptor internalAuthRequestInterceptor() {
        return template -> {
            String targetService = template.feignTarget().name();
            // 실시간 내부 토큰 생성
            String token = internalTokenIssuer.issue(targetService);

            template.header("Authorization", "Bearer " + token);
            template.header("X-Internal-Token", token);

            // 권한 헤더 주입
            template.header("X-User-Role", "SYSTEM");
            template.header("X-User-Id", "order-service");

            log.debug("[Feign] Internal Auth Header Injected for: {}", targetService);
        };
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            String reason = response.reason() != null ? response.reason() : "Unknown";

            log.error("Feign 통신 에러: Method={}, Status={}, Reason={}", methodKey, status, reason);

            if (status >= 300 && status < 400) {
                return new RuntimeException("리다이렉트 응답(3xx): status=" + status + ", reason=" + reason);
            } else if (status >= 400 && status < 500) {
                return new IllegalArgumentException("클라이언트 오류(4xx): status=" + status + ", reason=" + reason);
            } else if (status >= 500) {
                return new RuntimeException("서버 오류(5xx): status=" + status + ", reason=" + reason);
            }

            return new RuntimeException("알 수 없는 통신 오류: status=" + status + ", reason=" + reason);
        };
    }
}
