package com.michelet.order.infrastructure.config;

import com.michelet.common.auth.webmvc.context.UserContextHolder;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.AuditorAware;

@Slf4j
@Configuration
public class AuditorConfig {

    @Bean(name = "auditorAware")
    @Profile("!test")
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            try {
                if (UserContextHolder.get() != null && UserContextHolder.get().userId() != null) {
                    String userIdStr = UserContextHolder.get().userId();

                    try {
                        // 이미 정상적인 UUID 문자열이면 그대로 파싱
                        return Optional.of(UUID.fromString(userIdStr));
                    } catch (IllegalArgumentException e) {
                        // 일반 문자열일 경우에만 바이트 해시를 통해 UUID 생성
                        return Optional.of(UUID.nameUUIDFromBytes(userIdStr.getBytes()));
                    }
                }
            } catch (Exception e) {
                log.warn("AuditorAware에서 사용자 ID를 추출하는 중 오류가 발생했습니다. 시스템 기본 계정으로 폴백합니다.", e);
            }
            return Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        };
    }

    @Bean(name = "auditorAware")
    @Profile("test")
    public AuditorAware<UUID> testAuditorAware() {
        return () -> Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
