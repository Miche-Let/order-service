package com.michelet.order.infrastructure.config;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("perf")
@RequiredArgsConstructor
public class OrderPerfDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    // 타겟 유저 고정 UUID
    private static final UUID TARGET_USER_ID = UUID.fromString("7e6e1217-8fd6-4424-bd8f-2382d54f01cc");
    // Auditing (생성자/수정자) 용 더미 UUID
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public void run(ApplicationArguments args) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_service.p_orders", Long.class);
        if (count != null && count > 0) {
            log.info("[OrderPerfInitializer] 이미 데이터가 존재하여 더미 삽입을 생략합니다. ({}건)", count);
            return;
        }

        log.info("[OrderPerfInitializer] 100만 건 더미 주문 데이터 Batch Insert 시작...");
        long startTime = System.currentTimeMillis();

        String sql =
            "INSERT INTO order_service.p_orders (id, user_id, status, receiving_method, expired_at, reservation_id, restaurant_id, order_name, reserved_date, total_amount, created_at, updated_at, created_by, updated_by) "
                + "VALUES (?, ?, 'COMPLETED', 'PICKUP', NOW() + INTERVAL '1 DAY', ?, ?, ?, ?, 50000.00, NOW(), NOW(), ?, ?)";

        int batchSize = 10000;
        for (int i = 0; i < 100; i++) {
            final int batchIndex = i;
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int j) throws SQLException {
                    int globalIndex = batchIndex * batchSize + j;

                    ps.setObject(1, UUID.randomUUID()); // id
                    ps.setObject(2, globalIndex < 100000 ? TARGET_USER_ID : UUID.randomUUID()); // user_id
                    ps.setObject(3, UUID.randomUUID()); // reservation_id
                    ps.setObject(4, UUID.randomUUID()); // restaurant_id
                    ps.setString(5, "부하테스트 주문 " + globalIndex); // order_name
                    ps.setObject(6, LocalDate.now().minusDays(globalIndex % 365)); // reserved_date

                    ps.setObject(7, SYSTEM_USER_ID);
                    ps.setObject(8, SYSTEM_USER_ID);
                }

                @Override
                public int getBatchSize() {
                    return batchSize;
                }
            });
            log.info("... {}건 삽입 완료", (i + 1) * batchSize);
        }

        log.info("[OrderPerfInitializer] 100만 건 적재 완료! (소요 시간: {}ms)", System.currentTimeMillis() - startTime);
    }
}
