package com.michelet.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.michelet.common.response.ApiResponse;
import com.michelet.order.application.dto.CreateOrderCommand;
import com.michelet.order.application.dto.OrderCreatedEventPayload;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.application.dto.StockRestoreEventPayload;
import com.michelet.order.application.port.out.ReservationValidationPort;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderItem;
import com.michelet.order.domain.model.OrderStatus;
import com.michelet.order.domain.model.ReceivingMethod;
import com.michelet.order.domain.repository.OrderRepository;
import com.michelet.order.infrastructure.client.CatalogClient;
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
    private CatalogClient catalogClient;

    // Outbox 로직 테스트를 위한 Mock 객체 추가
    @Mock
    private OrderOutboxHelper orderOutboxHelper;
    @Mock
    private OrderRepository orderRepository;
    // OrderStore Mock 객체
    @Mock
    private OrderStore orderStore;

    @InjectMocks
    private OrderCommandService orderCommandService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;
    @Captor
    private ArgumentCaptor<OrderCreatedEventPayload> payloadCaptor;

    @Test
    @DisplayName("성공: 다중 품목 주문 시 총 주문 금액이 정확히 계산되고 PENDING 상태로 저장되어야 한다")
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

        given(orderStore.saveOrderAndOutbox(
                any(Order.class),
                any(OrderCreatedEventPayload.class)
            )
        ).willAnswer(invocation -> invocation.getArgument(0));

        // when
        OrderResult result = orderCommandService.createOrder(command);

        // then
        verify(orderStore).saveOrderAndOutbox(
            orderCaptor.capture(),
            payloadCaptor.capture()
        );
        Order savedOrder = orderCaptor.getValue();
        OrderCreatedEventPayload payload = payloadCaptor.getValue();

        // 1. 총액 검증 (55000 * 2 + 12000 * 3 = 146000)
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("146000"));
        // 2. 상태 검증
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        // 3. 아이템 개수 검증
        assertThat(savedOrder.getOrderItems()).hasSize(2);
        // 4. 자동 생성된 주문 이름 검증
        assertThat(savedOrder.getOrderName()).isEqualTo("티본 스테이크 외 1건");

        // Outbox Payload 생성 검증
        assertThat(payload.reservationId()).isEqualTo(command.reservationId());
        assertThat(payload.items()).hasSize(2);
    }

    @Test
    @DisplayName("성공: 인벤토리 승인 메시지 수신 시 주문이 OCCUPIED 로 변경되어야 한다")
    void approveOrder_Success() {
        // given
        UUID reservationId = UUID.randomUUID();
        Order order = createMockOrder(OrderStatus.PENDING);
        given(orderRepository.findByReservationId(reservationId)).willReturn(Optional.of(order));

        // when
        orderCommandService.approveOrder(reservationId);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OCCUPIED);
    }

    @Test
    @DisplayName("엣지케이스: 유저가 이미 취소(CANCELED)한 주문에 인벤토리 승인이 오면, 복구(RESTORE) Outbox가 발생해야 한다")
    void approveOrder_EdgeCase_AlreadyCanceled() {
        // given
        UUID reservationId = UUID.randomUUID();
        Order order = createMockOrder(OrderStatus.CANCELED); // 유저가 이미 취소함
        given(orderRepository.findByReservationId(reservationId)).willReturn(Optional.of(order));

        // when
        orderCommandService.approveOrder(reservationId);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED); // 상태 변경 안됨
        verify(orderOutboxHelper, times(1)).append(
            eq("ORDER"), any(), eq("STOCK_RESTORE"), any(StockRestoreEventPayload.class)
        );
    }

    @Test
    @DisplayName("성공: 인벤토리 거절 메시지 수신 시 주문이 CANCELED 로 변경되어야 한다")
    void rejectOrder_Success() {
        // given
        UUID reservationId = UUID.randomUUID();
        Order order = createMockOrder(OrderStatus.PENDING);
        given(orderRepository.findByReservationId(reservationId)).willReturn(Optional.of(order));

        // when
        orderCommandService.rejectOrder(reservationId, "재고 부족");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("성공: 주문 취소 시 해당 주문의 모든 아이템에 대해 재고 복구 이벤트가 Outbox에 저장되어야 한다")
    void cancelOrder_Success_ShouldSaveOutbox() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID(); // 예약 ID 변수 추가

        OrderItem item = OrderItem.create(optionId, "테스트상품", new BigDecimal("1000"), 2);
        Order order = Order.create(
            userId,
            reservationId, // 위에서 만든 예약 ID 주입
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
            eq(reservationId.toString()), // 파티션 키가 reservationId로 잘 넘어갔는지 검증
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

    // 헬퍼 메서드
    private Order createMockOrder(OrderStatus status) {
        Order order = Order.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "테스트",
            LocalDate.now().plusDays(1),
            ReceivingMethod.PICKUP,
            LocalDateTime.now().plusHours(2),
            List.of(
                OrderItem.create(
                    UUID.randomUUID(),
                    "상품",
                    new BigDecimal("1000"), 1
                )
            )
        );
        ReflectionTestUtils.setField(order, "status", status);
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }
}
