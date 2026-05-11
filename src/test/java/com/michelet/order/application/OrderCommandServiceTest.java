package com.michelet.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.michelet.common.response.ApiResponse;
import com.michelet.order.application.dto.CreateOrderCommand;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.application.port.out.ReservationValidationPort;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderStatus;
import com.michelet.order.domain.repository.OrderRepository;
import com.michelet.order.infrastructure.client.CatalogClient;
import com.michelet.order.infrastructure.client.InventoryClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private ReservationValidationPort reservationValidationPort;

    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private OrderCommandService orderCommandService;

    @Mock
    private OrderRepository orderRepository;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Test
    @DisplayName("성공: 다중 품목 주문 시 총 주문 금액이 정확히 계산되고 저장되어야 한다")
    void createOrder_Success_Calculation() {
        // given
        UUID optionId1 = UUID.randomUUID();
        UUID optionId2 = UUID.randomUUID();

        CreateOrderCommand command = new CreateOrderCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null, // 자동 생성을 위해 orderName은 null로 설정
            "PICKUP",
            LocalDateTime.now().plusHours(2),
            List.of(
                new CreateOrderCommand.OrderItemCommand(optionId1, 2),
                new CreateOrderCommand.OrderItemCommand(optionId2, 3)
            )
        );

        given(reservationValidationPort.validateAndGetDate(
            command.reservationId(),
            command.userId(),
            command.restaurantId()
        )).willReturn(LocalDate.now());

        given(catalogClient.validateOption(optionId1))
            .willReturn(ApiResponse.ok(
                new CatalogClient.OptionValidationResponse(optionId1, "티본 스테이크", new BigDecimal("55000"))));
        given(catalogClient.validateOption(optionId2))
            .willReturn(ApiResponse.ok(
                new CatalogClient.OptionValidationResponse(optionId2, "하우스 와인", new BigDecimal("12000"))));

        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        OrderResult result = orderCommandService.createOrder(command);

        // then
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        // 55000 * 2 + 12000 * 3 = 146000
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("146000"));
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.OCCUPIED);
        assertThat(savedOrder.getOrderItems()).hasSize(2);

        // 3. 자동 생성된 주문 이름 검증
        assertThat(savedOrder.getOrderName()).isEqualTo("티본 스테이크 외 1건");

        // 4. 카탈로그에서 가져온 이름이 정상적으로 스냅샷에 저장되었는지 검증
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
        // when & then
        assertThatThrownBy(() -> new CreateOrderCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "빈 주문",
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
        // when & then
        assertThatThrownBy(() -> new CreateOrderCommand.OrderItemCommand(
            UUID.randomUUID(),
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quantity는 1 이상이어야 합니다.");
    }
}
