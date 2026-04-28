package com.michelet.order.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.michelet.common.config.JpaAuditingConfig;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderItem;
import com.michelet.order.domain.model.ReceivingMethod;
import com.michelet.order.infrastructure.config.JpaConfig;
import jakarta.persistence.EntityManager;
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
@Import({JpaConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class OrderPersistenceTest {

    @Autowired
    private JpaOrderRepository jpaOrderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("성공: removeOrderItem 호출 후 플러시하면 orphanRemoval에 의해 FK Null 업데이트 없이 정상 삭제(DELETE)된다")
    void removeOrderItem_ShouldDeleteOrphan() {
        // given: 2개의 아이템을 가진 주문 생성 (합계 20000원)
        OrderItem item1 = OrderItem.create(UUID.randomUUID(), "상품A", new BigDecimal("10000"), 1);
        OrderItem item2 = OrderItem.create(UUID.randomUUID(), "상품B", new BigDecimal("5000"), 2);

        Order order = Order.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "   공백 포함 주문명   ", // 테스트용 공백
            LocalDate.now(), ReceivingMethod.PICKUP, LocalDateTime.now().plusHours(2),
            List.of(item1, item2)
        );

        Order savedOrder = jpaOrderRepository.save(order);
        entityManager.flush();
        entityManager.clear(); // 영속성 컨텍스트 초기화

        // when: 첫 번째 아이템(상품A) 삭제
        Order foundOrder = jpaOrderRepository.findById(savedOrder.getId()).orElseThrow();
        OrderItem targetItem = foundOrder.getOrderItems().get(0); // 상품A 추출

        foundOrder.removeOrderItem(targetItem); // 삭제 메서드 실행

        // flush 시 FK에 Null을 넣는 Update 쿼리가 발생하면 PropertyValueException 발생.
        // 올바르게 설정되었다면 곧바로 Delete 쿼리가 나감.
        entityManager.flush();
        entityManager.clear();

        // then: 데이터베이스 검증
        Order updatedOrder = jpaOrderRepository.findById(savedOrder.getId()).orElseThrow();

        // 1) 리스트에 1개의 항목만 남음
        assertThat(updatedOrder.getOrderItems()).hasSize(1);

        // 2) 상품B(5000*2) 금액인 10000원만 남음
        assertThat(updatedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("10000"));

        // 3) Nitpick 적용 확인: 이름의 공백이 정규화(trim)되었는지
        assertThat(updatedOrder.getOrderName()).isEqualTo("공백 포함 주문명");
    }
}
