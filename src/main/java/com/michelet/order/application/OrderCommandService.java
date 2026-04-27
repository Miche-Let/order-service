package com.michelet.order.application;

import com.michelet.order.application.dto.CreateOrderCommand;
import com.michelet.order.application.dto.OrderResult;
import com.michelet.order.domain.model.Order;
import com.michelet.order.domain.model.OrderItem;
import com.michelet.order.domain.model.ReceivingMethod;
import com.michelet.order.domain.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public String checkHealth() {
        return "Order Command Service is Healthy";
    }

    public OrderResult createOrder(CreateOrderCommand command) {
        // 1. OrderItem 생성 - 스냅샷
        List<OrderItem> orderItems = command.items().stream()
            .map(it -> OrderItem.create(it.optionId(), it.productName(), it.orderPrice(), it.quantity()))
            .toList();

        // 2. Aggregate Root(Order) 생성 및 계산
        ReceivingMethod method = command.receivingMethod() != null ?
            ReceivingMethod.valueOf(command.receivingMethod().toUpperCase()) : ReceivingMethod.PICKUP;

        Order order = Order.create(command.userId(), command.reservationId(),
            command.restaurantId(),
            command.orderName(),
            command.reservedDate(),
            method,
            command.expiredAt(),
            orderItems);

        // 3. 저장
        Order savedOrder = orderRepository.save(order);

        return new OrderResult(savedOrder.getId(), savedOrder.getStatus().name());
    }
}
