package com.michelet.order.application;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository; // 다른 메서드에서 쓰기 위해 남겨둠
    private final ReservationValidationPort reservationValidationPort;
    private final CatalogClient catalogClient;

    private final OrderOutboxHelper orderOutboxHelper;

    private final OrderStore orderStore;

    @Transactional(readOnly = true)
    public String checkHealth() {
        return "Order Command Service is Healthy";
    }

    // 트랜잭션 없음 (외부 통신 병목 방지)
    public OrderResult createOrder(CreateOrderCommand command) {
        // 전략 패턴을 통한 예약 검증 (프로필에 따라 진짜 또는 가짜 어댑터가 동작함)
        LocalDate verifiedDate = reservationValidationPort.validateAndGetDate(
            command.reservationId(),
            command.userId(),
            command.restaurantId()
        );

        List<OrderItem> orderItems = new ArrayList<>();

        // 1. 인벤토리 Feign 호출 전면 제거 (순수 카탈로그 가격 검증만 진행)
        for (CreateOrderCommand.OrderItemCommand item : command.items()) {
            var catalogRes = catalogClient.validateOption(item.optionId());
            if (catalogRes == null || catalogRes.data() == null) {
                throw new IllegalArgumentException("상품 옵션 정보를 확인할 수 없습니다.");
            }

            var catalogData = catalogRes.data();

//            // 우회 테스트용 - 통신을 기다리지 않고, 0초 만에 더미 데이터를 반환하도록 강제 우회함
//            var catalogData = new CatalogClient.OptionValidationResponse(
//                item.optionId(),
//                "병목 증명용 더미 상품",
//                new java.math.BigDecimal("10000")
//            );

            // 카탈로그에서 검증된 진짜 이름과 가격으로 OrderItem 스냅샷 생성
            orderItems.add(OrderItem.create(
                catalogData.optionId(),
                catalogData.name(),
                catalogData.totalPrice(),
                item.quantity()
            ));
        }

        // 주문 이름 자동 생성 로직
        String finalOrderName = command.orderName();
        if (finalOrderName == null || finalOrderName.isBlank()) {
            // 카탈로그에서 가져온 첫 번째 상품명 추출
            String firstName = orderItems.get(0).getProductName();
            finalOrderName = (orderItems.size() > 1)
                ? firstName + " 외 " + (orderItems.size() - 1) + "건"
                : firstName;
        }

        // Aggregate Root(Order) 생성 및 계산
        ReceivingMethod method;
        try {
            method = command.receivingMethod() != null ?
                ReceivingMethod.valueOf(command.receivingMethod().toUpperCase()) : ReceivingMethod.PICKUP;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 수령 방법입니다: " + command.receivingMethod());
        }

        Order order = Order.create(
            command.userId(),
            command.reservationId(),
            command.restaurantId(),
            finalOrderName,
            verifiedDate, // 포트를 통해 받아온 날짜
            method,
            command.expiredAt(),
            orderItems
        );

        // 2. 카프카 전송용 페이로드 생성
        OrderCreatedEventPayload payload = new OrderCreatedEventPayload(
            UUID.randomUUID(), command.reservationId(),
            orderItems.stream()
                .map(i -> new OrderCreatedEventPayload.OrderItemPayload(
                    i.getOptionId(),
                    i.getQuantity()
                )).toList()
        );

        // 3. PENDING 상태로 주문 저장 및 아웃박스 동시 기록
        Order savedOrder = orderStore.saveOrderAndOutbox(order, payload);

        return new OrderResult(savedOrder.getId(), savedOrder.getStatus().name(), savedOrder.getOrderName());
    }

    // 인벤토리에서 승인 완료 메시지가 오면 상태 변경
    @Transactional
    public void approveOrder(UUID reservationId) {
        Order order = orderRepository.findByReservationId(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        // 이미 처리되었거나 상태가 넘어간 경우 (멱등성 방어)
        if (order.getStatus() != OrderStatus.PENDING) {
            if (order.getStatus() == OrderStatus.CANCELED) {
                log.warn("[Order Saga Edge-Case] 이미 유저가 취소한 주문에 대해 승인이 도착했습니다. 인벤토리 롤백 이벤트를 발행합니다: {}", reservationId);
                for (OrderItem item : order.getOrderItems()) {
                    orderOutboxHelper.append(
                        "ORDER", order.getId().toString(), "STOCK_RESTORE",
                        new StockRestoreEventPayload(UUID.randomUUID(), item.getOptionId(), item.getQuantity())
                    );
                }
            } else {
                log.info("[Idempotency] 이미 처리된 주문입니다. (현재 상태: {})", order.getStatus());
            }
            return; // 상태 변경 없이 안전하게 종료
        }

        // 정상 PENDING 상태라면 승인 처리 (이때 Order.occupy() 가 호출됨)
        order.occupy();
        log.info("[Order Saga] 예약 승인 및 주문 최종 확정 완료: {}", reservationId);
    }

    // 인벤토리에서 재고 부족 등으로 거절 메시지가 오면 강제 취소
    @Transactional
    public void rejectOrder(UUID reservationId, String reason) {
        Order order = orderRepository.findByReservationId(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("[Idempotency] 이미 처리된 주문입니다. (현재 상태: {})", order.getStatus());
            return;
        }

        order.markAsCanceled();
        log.error("[Order Saga] 인벤토리 재고 부족으로 주문 강제 취소: {}, 사유: {}", reservationId, reason);
    }

    @Transactional
    public void cancelOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }

        // 방문 예정일 이전인지 확인하고 상태 변경 (현재 시점 전달)
        order.cancel(LocalDate.now());

        for (OrderItem item : order.getOrderItems()) {
            orderOutboxHelper.append(
                "ORDER",
                order.getId().toString(),
                "STOCK_RESTORE",
                new StockRestoreEventPayload(UUID.randomUUID(), item.getOptionId(), item.getQuantity())
            );
            log.info("주문 취소에 따른 재고 복구 Outbox 저장 완료: optionId={}, quantity={}", item.getOptionId(), item.getQuantity());
        }
    }

    @Transactional
    // TODO: 향후 결제 시스템 연동 완료 시, 클라이언트 직접 호출이 아닌 Kafka 결제 완료 이벤트 리스너에서 호출하도록 변경
    public void completeOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        order.complete();
    }

    @Transactional
    public void receiveOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        // 방문 예정 당일인지 확인하고 상태를 RECEIVED로 변경
        // 추후 Outbox를 활용하여 리뷰/통계 이벤트를 발행할 예정
        order.receive(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public OrderResult getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        return new OrderResult(order.getId(), order.getStatus().name(), order.getOrderName());
    }
}
