package com.michelet.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    @DisplayName("성공: 주문 생성 시 총 금액이 각 항목의 (가격 * 수량) 합계와 일치해야 한다")
    void createOrder_TotalAmountCalculation() {
        // given
        OrderItem item1 = OrderItem.create(UUID.randomUUID(), "상품A", new BigDecimal("10000"), 2); // 20000
        OrderItem item2 = OrderItem.create(UUID.randomUUID(), "상품B", new BigDecimal("5500"), 3);  // 16500

        // when
        Order order = Order.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "테스트 주문",
            LocalDate.now(),
            ReceivingMethod.PICKUP,
            LocalDateTime.now().plusHours(2),
            List.of(item1, item2)
        );

        // then
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("36500"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OCCUPIED);
    }

    @Test
    @DisplayName("성공: OCCUPIED 상태에서는 COMPLETED 또는 CANCELED로 전이가 가능해야 한다")
    void statusTransition_Success() {
        // given
        Order order = createDefaultOrder();

        // when & then
        order.complete();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        Order order2 = createDefaultOrder();
        order2.cancel();
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("실패: 이미 완료되거나 취소된 주문은 상태를 변경할 수 없다")
    void statusTransition_Fail() {
        // given
        Order order = createDefaultOrder();
        order.complete();

        // when & then
        assertThatThrownBy(order::cancel)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("주문 취소가 불가능한 상태입니다.");
    }

    private Order createDefaultOrder() {
        OrderItem item = OrderItem.create(UUID.randomUUID(), "기본상품", new BigDecimal("1000"), 1);
        return Order.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "테스트 주문",
            LocalDate.now(),
            ReceivingMethod.PICKUP,
            LocalDateTime.now().plusHours(2),
            List.of(item));
    }

    @Test
    @DisplayName("실패: 이미 취소된 주문은 완료 상태로 변경할 수 없다")
    void statusTransition_Fail_CanceledToCompleted() {
        // given
        Order order = createDefaultOrder();
        order.cancel();

        // when & then
        assertThatThrownBy(order::complete)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("주문 완료가 불가능한 상태입니다.");
    }
}
