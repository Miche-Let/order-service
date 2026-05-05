package com.michelet.order.application;

import com.michelet.order.application.dto.CreateOrderCommand;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderItem;
import com.michelet.order.domain.model.ReceivingMethod;
import com.michelet.order.domain.repository.OrderRepository;
import com.michelet.order.infrastructure.client.CatalogClient;
import com.michelet.order.infrastructure.client.InventoryClient;
import com.michelet.order.infrastructure.client.ReservationClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final ReservationClient reservationClient;
    private final InventoryClient inventoryClient;
    private final CatalogClient catalogClient;

    @Transactional(readOnly = true)
    public String checkHealth() {
        return "Order Command Service is Healthy";
    }

    public OrderResult createOrder(CreateOrderCommand command) {
        // 0. 로컬 DB 중복 주문 방지 (UK)
        if (orderRepository.existsByReservationId(command.reservationId())) {
            throw new IllegalStateException("해당 예약으로 이미 생성된 주문이 존재합니다.");
        }

        // 0-1. 예약 서비스 검증
        var resResponse = reservationClient.verifyReservation(command.userId(), command.restaurantId());
        if (resResponse == null || resResponse.data() == null || !resResponse.data().isValid()) {
            throw new IllegalArgumentException("유효한 예약 내역을 찾을 수 없거나 이미 처리된 예약입니다.");
        }
        if (!resResponse.data().reservationId().equals(command.reservationId())) {
            throw new IllegalArgumentException("요청된 예약 ID가 유효한 예약 정보와 일치하지 않습니다.");
        }

        // 예약 서비스로부터 받아온 예약날짜
        LocalDate verifiedDate = resResponse.data().reservationDate();

        // 보상 트랜잭션 기록용 리스트 및 주문 상품 스냅샷 리스트
        List<InventoryClient.RestoreStockRequest> reservedStocks = new ArrayList<>();
        List<OrderItem> orderItems = new ArrayList<>();

        try {
            // 0-2. 카탈로그 가격 검증 및 인벤토리 재고 선점
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
                verifiedDate,
                method,
                command.expiredAt(),
                orderItems
            );

            // 3. 저장
            Order savedOrder = orderRepository.save(order);

            return new OrderResult(savedOrder.getId(), savedOrder.getStatus().name(), savedOrder.getOrderName());

        } catch (Exception e) {
            // 4. 에러 발생 시 재고 복구 로직
            log.error("주문 생성 중 예외 발생. 보상 트랜잭션(재고 복구)을 실행합니다. 원인: {}", e.getMessage());
            for (InventoryClient.RestoreStockRequest restoreReq : reservedStocks) {
                try {
                    inventoryClient.restoreStock(restoreReq);
                    log.info("보상 완료: 옵션 {} 재고 복구", restoreReq.optionId());
                } catch (Exception ex) {
                    log.error("크리티컬: 재고 복구 실패 (데이터 불일치 발생!): optionId={}, quantity={}",
                        restoreReq.optionId(), restoreReq.quantity(), ex);
                }
            }
            throw e;
        }
    }
}
