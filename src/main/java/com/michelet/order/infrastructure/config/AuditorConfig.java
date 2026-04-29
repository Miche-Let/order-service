package com.michelet.order.infrastructure.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class AuditorConfig {

    @Bean(name = "auditorAware")
    @Profile("!test")
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(auth -> auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) // 익명 객체 제외 추가
            .map(auth -> {
                try {
                    // 보안 컨텍스트에서 사용자 ID 추출 (현재는 이름을 UUID로 가정)
                    return UUID.fromString(auth.getName());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            // 로그인 정보가 없는 시스템 내부 호출 시 사용할 고정 시스템 ID
            .or(() -> Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Bean(name = "auditorAware")
    @Profile("test")
    public AuditorAware<UUID> testAuditorAware() {
        return () -> Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
