package com.michelet.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.michelet.common.response.ApiResponse;
import com.michelet.order.application.dto.CreateOrderCommand;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.application.dto.StockRestoreEventPayload;
import com.michelet.order.application.port.out.ReservationValidationPort;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderItem;
import com.michelet.order.domain.model.OrderStatus;
import com.michelet.order.domain.model.ReceivingMethod;
import com.michelet.order.domain.repository.OrderRepository;
import com.michelet.order.infrastructure.client.CatalogClient;
import com.michelet.order.infrastructure.client.InventoryClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private ReservationValidationPort reservationValidationPort;

    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private CatalogClient catalogClient;

    // Outbox 로직 테스트를 위한 Mock 객체 추가
    @Mock
    private OrderOutboxHelper orderOutboxHelper;

    @InjectMocks
    private OrderCommandService orderCommandService;

    @Mock
    private OrderRepository orderRepository;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    // OrderStore Mock 객체
    @Mock
    private OrderStore orderStore;

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
            any(),
            any(),
            any()
        )).willReturn(LocalDate.now());

        given(catalogClient.validateOption(optionId1))
            .willReturn(ApiResponse.ok(
                new CatalogClient.OptionValidationResponse(optionId1, "티본 스테이크", new BigDecimal("55000"))));
        given(catalogClient.validateOption(optionId2))
            .willReturn(ApiResponse.ok(
                new CatalogClient.OptionValidationResponse(optionId2, "하우스 와인", new BigDecimal("12000"))));

        given(orderStore.saveOrder(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        OrderResult result = orderCommandService.createOrder(command);

        // then
        verify(orderStore).saveOrder(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        // 1. 총액 검증 (55000 * 2 + 12000 * 3 = 146000)
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("146000"));
        // 2. 상태 검증
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.OCCUPIED);
        // 3. 아이템 개수 검증
        assertThat(savedOrder.getOrderItems()).hasSize(2);
        // 4. 자동 생성된 주문 이름 검증
        assertThat(savedOrder.getOrderName()).isEqualTo("티본 스테이크 외 1건");

        // 5. 카탈로그에서 가져온 이름이 정상적으로 스냅샷에 저장되었는지 검증
        assertThat(savedOrder.getOrderItems())
            .extracting("productName", "quantity")
            .containsExactly(
                tuple("티본 스테이크", 2),
                tuple("하우스 와인", 3)
            );
    }

    @Test
    @DisplayName("실패: 두 번째 상품 재고 선점 중 에러 발생 시, 첫 번째 상품에 대한 복구 이벤트가 Outbox에 저장되어야 한다")
    void createOrder_Fail_ShouldSaveOutboxCompensation() {
        // given
        UUID optionId1 = UUID.randomUUID();
        UUID optionId2 = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        CreateOrderCommand command = new CreateOrderCommand(
            UUID.randomUUID(), reservationId, UUID.randomUUID(),
            null, "PICKUP", LocalDateTime.now().plusHours(2),
            List.of(
                new CreateOrderCommand.OrderItemCommand(optionId1, 2), // 1번은 성공 가정
                new CreateOrderCommand.OrderItemCommand(optionId2, 3)  // 2번에서 에러 발생 예정
            )
        );

        given(reservationValidationPort.validateAndGetDate(
            any(),
            any(),
            any()
        )).willReturn(LocalDate.now());

        given(catalogClient.validateOption(optionId1))
            .willReturn(
                ApiResponse.ok(new CatalogClient.OptionValidationResponse(
                    optionId1,
                    "상품1",
                    new BigDecimal("1000")
                )));
        given(catalogClient.validateOption(optionId2))
            .willReturn(
                ApiResponse.ok(new CatalogClient.OptionValidationResponse(
                    optionId2,
                    "상품2",
                    new BigDecimal("2000")
                )));

        // 첫 번째 상품(optionId1)은 정상적으로 통과되도록 Mock 설정 추가 (Strict Stubbing 해결)
        given(inventoryClient.reserveStock(new InventoryClient.ReserveStockRequest(optionId1, 2)))
            .willReturn(ApiResponse.ok(null));

        // 두 번째 아이템 예약 시 강제로 에러를 던지도록 설정
        doThrow(new RuntimeException("인벤토리 통신 에러"))
            .when(inventoryClient).reserveStock(new InventoryClient.ReserveStockRequest(optionId2, 3));

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("인벤토리 통신 에러");

        // 첫 번째 상품(성공했던 것)을 다시 돌려놓기 위해 appendCompensation이 호출되었는지 검증
        verify(orderStore, times(1)).saveCompensationOutbox(
            eq(reservationId),
            any()
        );
    }

    @Test
    @DisplayName("성공: 주문 취소 시 해당 주문의 모든 아이템에 대해 재고 복구 이벤트가 Outbox에 저장되어야 한다")
    void cancelOrder_Success_ShouldSaveOutbox() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();

        OrderItem item = OrderItem.create(optionId, "테스트상품", new BigDecimal("1000"), 2);
        Order order = Order.create(
            userId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "테스트 주문",
            LocalDate.now().plusDays(1),
            ReceivingMethod.PICKUP,
            LocalDateTime.now().plusHours(2),
            List.of(item)
        );

        // 단위 테스트에서는 DB에 안 가므로 ID가 null임 - 리플렉션으로 가짜 ID 주입 (NPE 해결)
        ReflectionTestUtils.setField(order, "id", orderId);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        orderCommandService.cancelOrder(orderId, userId);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(orderOutboxHelper, times(1)).append(
            eq("ORDER"),
            eq(orderId.toString()),
            eq("STOCK_RESTORE"),
            any(StockRestoreEventPayload.class)
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
