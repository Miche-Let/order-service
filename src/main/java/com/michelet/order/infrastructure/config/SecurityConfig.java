package com.michelet.order.infrastructure.config;

import com.michelet.common.auth.webmvc.filter.InternalAuthFilter;
import com.michelet.common.auth.webmvc.internal.InternalTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.AntPathMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.application.name:order-service}")
    private String applicationName;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, InternalTokenProvider tokenProvider) throws Exception {

        InternalAuthFilter cleanInternalFilter = new InternalAuthFilter(tokenProvider, applicationName) {
            private final AntPathMatcher pathMatcher = new AntPathMatcher();

            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getServletPath();
                return !pathMatcher.match("/internal/**", path);
            }
        };

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                .requestMatchers("/internal/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(cleanInternalFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
