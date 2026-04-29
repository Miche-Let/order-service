package com.michelet.order.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.michelet.common.config.JpaAuditingConfig;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderItem;
import com.michelet.order.domain.model.ReceivingMethod;
import com.michelet.order.infrastructure.config.AuditorConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({AuditorConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class OrderAuditingTest {

    @Autowired
    private JpaOrderRepository jpaOrderRepository;

    @Test
    @DisplayName("성공: 데이터 저장 시 AuditorConfig에 설정된 테스트용 UUID가 createdBy에 저장되어야 한다")
    void auditing_CreatedBy_ShouldMatchTestAuditor() {
        // given
        OrderItem item = OrderItem.create(UUID.randomUUID(), "테스트", new BigDecimal("1000"), 1);
        Order order = Order.create(UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Auditing 테스트",
            LocalDate.now(),
            ReceivingMethod.PICKUP,
            LocalDateTime.now().plusHours(2),
            List.of(item));

        // when
        Order savedOrder = jpaOrderRepository.save(order);

        // then
        // AuditorConfig의 testAuditorAware에서 반환하는 "0000...0000"과 일치해야 함
        assertThat(savedOrder.getCreatedBy()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        assertThat(savedOrder.getCreatedAt()).isNotNull();
    }
}
