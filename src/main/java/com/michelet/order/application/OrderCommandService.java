package com.michelet.order.application;

import com.michelet.order.application.dto.CreateOrderCommand;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.application.dto.StockRestoreEventPayload;
import com.michelet.order.application.port.out.ReservationValidationPort;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderItem;
import com.michelet.order.domain.model.ReceivingMethod;
import com.michelet.order.domain.repository.OrderRepository;
import com.michelet.order.infrastructure.client.CatalogClient;
import com.michelet.order.infrastructure.client.InventoryClient;
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

    private final OrderRepository orderRepository;
    private final ReservationValidationPort reservationValidationPort;
    private final InventoryClient inventoryClient;
    private final CatalogClient catalogClient;

    private final OrderOutboxHelper orderOutboxHelper;

    @Transactional(readOnly = true)
    public String checkHealth() {
        return "Order Command Service is Healthy";
    }

    public OrderResult createOrder(CreateOrderCommand command) {
        // 전략 패턴을 통한 예약 검증 (프로필에 따라 진짜 또는 가짜 어댑터가 동작함)
        LocalDate verifiedDate = reservationValidationPort.validateAndGetDate(
            command.reservationId(),
            command.userId(),
            command.restaurantId()
        );

        // 보상 트랜잭션 기록용 리스트 및 주문 상품 스냅샷 리스트
        List<InventoryClient.RestoreStockRequest> reservedStocks = new ArrayList<>();
        List<OrderItem> orderItems = new ArrayList<>();

        try {
            // 0-2. 카탈로그 가격 검증 및 인벤토리 재고 선점 (예약 선점은 일단 동기 유지)
            for (CreateOrderCommand.OrderItemCommand item : command.items()) {
                var catalogRes = catalogClient.validateOption(item.optionId());
                if (catalogRes == null || catalogRes.data() == null) {
                    throw new IllegalArgumentException("상품 옵션 정보를 확인할 수 없습니다.");
                }

                var catalogData = catalogRes.data();

                // 1. 카탈로그에서 검증된 진짜 이름과 가격으로 OrderItem 스냅샷 생성
                orderItems.add(OrderItem.create(
                    catalogData.optionId(),
                    catalogData.name(),
                    catalogData.totalPrice(),
                    item.quantity()
                ));

                inventoryClient.reserveStock(new InventoryClient.ReserveStockRequest(item.optionId(), item.quantity()));
                reservedStocks.add(new InventoryClient.RestoreStockRequest(item.optionId(), item.quantity()));
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

            // 2. Aggregate Root(Order) 생성 및 계산
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

            // 3. 저장
            Order savedOrder = orderRepository.save(order);

            return new OrderResult(savedOrder.getId(), savedOrder.getStatus().name(), savedOrder.getOrderName());

        } catch (Exception e) {
            // 성공적으로 선점했던 내역(reservedStocks)만 순회하며 주문 시도 전체에 대한 성공 아이템에 대해서도 복구 기록을 남김
            log.error("주문 생성 중 예외 발생. 보상 트랜잭션(재고 복구)을 Outbox에 저장합니다. 원인: {}", e.getMessage());

            // 반복문 안쪽에 try-catch를 두어 독립적인 실패 추적 및 계속 진행 보장
            for (InventoryClient.RestoreStockRequest restoreReq : reservedStocks) {
                try {
                    orderOutboxHelper.appendCompensation(
                        "ORDER",
                        command.reservationId().toString(), // Order가 생성되기 전이므로 예약 ID를 Aggregate ID로 사용
                        "STOCK_RESTORE",
                        // UUID.randomUUID()를 통해 고유한 이벤트 식별자(eventId) 발급
                        new StockRestoreEventPayload(UUID.randomUUID(), restoreReq.optionId(), restoreReq.quantity())
                    );
                    log.info("보상 Outbox 저장 완료: 옵션 {} 재고 복구 대기", restoreReq.optionId());
                } catch (Exception outboxEx) {
                    // 향후 모니터링/알림 시스템 연동을 위한 상세 로그 기록
                    log.error(
                        "[CRITICAL ALERT] 보상 트랜잭션 Outbox 저장 실패. 수동 복구 요망! reservationId: {}, optionId: {}, quantity: {}",
                        command.reservationId(), restoreReq.optionId(), restoreReq.quantity(), outboxEx);
                    // TODO: Slack, Datadog 알림 발송 등
                }
            }
            throw e;
        }
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
