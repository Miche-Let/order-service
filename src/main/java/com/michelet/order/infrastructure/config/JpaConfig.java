package com.michelet.order.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
// Redis와 스캔 충돌을 막기 위해 패키지 명시
@EnableJpaRepositories(basePackages = "com.michelet.order.infrastructure.repository")
public class JpaConfig {
}
