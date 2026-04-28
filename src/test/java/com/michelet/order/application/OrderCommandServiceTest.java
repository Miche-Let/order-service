package com.michelet.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
        assertThat(savedOrder.getOrderItems())
            .extracting("productName", "quantity")
            .containsExactly(
                tuple("티본 스테이크", 2),
                tuple("하우스 와인", 3)
            );
    }

    @Test
    @DisplayName("실패: 주문 상품 리스트가 비어있을 경우 Command 생성 시 예외가 발생한다")
    void createOrder_Fail_EmptyItems() {
        // when & then: Command 객체를 생성하는 순간 예외가 터져야 함
        assertThatThrownBy(() -> new CreateOrderCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "빈 주문",
            LocalDate.now(),
            "PICKUP",
            LocalDateTime.now().plusHours(2),
            List.of() // 빈 리스트 전달
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("주문 항목은 최소 1개 이상이어야 합니다.");
    }

    @Test
    @DisplayName("실패: 주문 상품의 수량이 0 이하일 경우 OrderItemCommand 생성 시 예외가 발생한다")
    void createOrder_Fail_InvalidQuantity() {
        // when & then: OrderItemCommand 객체를 생성하는 순간 예외가 터져야 함
        assertThatThrownBy(() -> new CreateOrderCommand.OrderItemCommand(
            UUID.randomUUID(),
            "에러 상품",
            new BigDecimal("1000"),
            0 // 수량 0 전달
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quantity는 1 이상이어야 합니다.");
    }
}
