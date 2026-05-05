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

    //FIXME 아직 수정 안한 서비스들 호환을 위해 일단 넣음 - 나중엔 이 부분 삭제
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

//TODO 다른 서비스들 다 feign 연결 끝나면 아래 활성화
//package com.michelet.order.infrastructure.config;
//
//import com.michelet.common.auth.feign.interceptor.InternalFeignInterceptor;
//import com.michelet.common.auth.feign.internal.InternalTokenIssuer;
//import com.michelet.common.auth.feign.config.InternalFeignProperties;
//import feign.RequestInterceptor;
//import feign.codec.ErrorDecoder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class FeignConfig {
//
//    @Bean
//    public RequestInterceptor internalFeignInterceptor(
//        InternalTokenIssuer internalTokenIssuer,
//        InternalFeignProperties properties
//    ) {
//        return new InternalFeignInterceptor(internalTokenIssuer, properties);
//    }
//
//    @Bean
//    public ErrorDecoder errorDecoder() {
//        // 에러 로깅 및 예외 변환 로직만 남기기
//        return (methodKey, response) -> {
//            if (response.status() >= 400 && response.status() < 500) {
//                return new IllegalArgumentException("내부 통신 인증 실패");
//            }
//            return new RuntimeException("외부 서비스 서버 오류");
//        };
//    }
//}
