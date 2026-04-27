package com.michelet.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michelet.order.application.dto.CreateOrderCommand;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderStatus;
import com.michelet.order.domain.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCommandServiceTest {

    @Autowired
    private OrderCommandService orderCommandService;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("성공: 다중 품목 주문 시 총 주문 금액이 정확히 계산되고 저장되어야 한다")
    void createOrder_Success_Calculation() {
        // given
        CreateOrderCommand command = new CreateOrderCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "스테이크 외 1건",
            LocalDate.now(),
            "PICKUP",
            LocalDateTime.now().plusHours(2),
            List.of(
                new CreateOrderCommand.OrderItemCommand(UUID.randomUUID(), "티본 스테이크", new BigDecimal("55000"), 2),
                new CreateOrderCommand.OrderItemCommand(UUID.randomUUID(), "하우스 와인", new BigDecimal("12000"), 3)
            )
        );

        // when
        OrderResult result = orderCommandService.createOrder(command);

        // then: (55000 * 2) + (12000 * 3) = 110000 + 36000 = 146000
        Order savedOrder = orderRepository.findById(result.orderId()).orElseThrow();
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("146000"));
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.OCCUPIED);
        assertThat(savedOrder.getOrderItems()).hasSize(2);

        // 스냅샷 검증: 상품명이 정확히 저장되었는가
        assertThat(savedOrder.getOrderItems().get(0).getProductName()).isEqualTo("티본 스테이크");
    }

    @Test
    @DisplayName("실패: 주문 상품 리스트가 비어있을 경우 예외가 발생한다")
    void createOrder_Fail_EmptyItems() {
        // given
        CreateOrderCommand command = new CreateOrderCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "빈 주문",
            LocalDate.now(),
            "PICKUP",
            LocalDateTime.now().plusHours(2),
            List.of()
        );

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("주문 항목은 최소 1개 이상이어야 합니다.");
    }

    @Test
    @DisplayName("실패: 주문 상품의 수량이 0 이하일 경우 VO 검증에 의해 예외가 발생한다")
    void createOrder_Fail_InvalidQuantity() {
        // given: 수량을 0으로 설정
        CreateOrderCommand command = new CreateOrderCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "에러 주문",
            LocalDate.now(),
            "PICKUP",
            LocalDateTime.now().plusHours(2),
            List.of(new CreateOrderCommand.OrderItemCommand(UUID.randomUUID(), "에러 상품", new BigDecimal("1000"), 0))
        );

        // when & then: OrderItem 생성 시점 혹은 Quantity VO에서 터짐
        assertThatThrownBy(() -> orderCommandService.createOrder(command))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
