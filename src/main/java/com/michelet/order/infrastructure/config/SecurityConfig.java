package com.michelet.order.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // TODO: JWT 도입 시 SessionCreationPolicy.STATELESS 설정 추가 필요
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/orders/health", "/internal/**").permitAll()
                // TODO: 현재는 별도의 인증 필터가 없으므로 모든 요청이 차단될 수 있음
                // 테스트 시에는 임시로 permitAll()을 사용하거나, 인증 필터 구현 후 사용
                .anyRequest().authenticated()
            );

        // TODO: JwtAuthenticationFilter 구현 후 여기에 필터 등록 필요
        // http.addFilterBefore(new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
