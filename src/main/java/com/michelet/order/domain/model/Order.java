package com.michelet.order.domain.model;

import com.michelet.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "p_orders",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_reservation_id", columnNames = {"reservation_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceivingMethod receivingMethod;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 200)
    private String orderName;

    @Column(nullable = false)
    private LocalDate reservedDate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Builder(access = AccessLevel.PRIVATE)
    private Order(UUID userId, UUID reservationId, UUID restaurantId, String orderName, LocalDate reservedDate,
                  ReceivingMethod receivingMethod, LocalDateTime expiredAt) {
        this.userId = userId;
        this.reservationId = reservationId;
        this.restaurantId = restaurantId;
        this.orderName = orderName;
        this.reservedDate = reservedDate;
        this.receivingMethod = receivingMethod != null ? receivingMethod : ReceivingMethod.PICKUP;
        this.expiredAt = expiredAt;
        this.status = OrderStatus.OCCUPIED; // MVP 기준 : 주문 생성 즉시 현장 결제 대기 상태
        this.totalAmount = BigDecimal.ZERO;
    }

    public static Order create(UUID userId, UUID reservationId, UUID restaurantId, String orderName,
                               LocalDate reservedDate, ReceivingMethod receivingMethod, LocalDateTime expiredAt,
                               List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 최소 1개 이상이어야 합니다.");
        }
        Order order = new Order(userId, reservationId, restaurantId, orderName, reservedDate, receivingMethod,
            expiredAt);
        items.forEach(order::addOrderItem);
        order.calculateTotalAmount(); // N^2 연산 방지를 위해 항목을 모두 추가한 뒤 마지막에 1회만 계산
        return order;
    }

    public void addOrderItem(OrderItem item) {
        this.orderItems.add(item);
        item.assignOrder(this);
    }

    private void calculateTotalAmount() {
        this.totalAmount = orderItems.stream()
            .map(OrderItem::getLinePrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void complete() {
        if (!this.status.canTransitionTo(OrderStatus.COMPLETED)) {
            throw new IllegalStateException("주문 완료가 불가능한 상태입니다.");
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (!this.status.canTransitionTo(OrderStatus.CANCELED)) {
            throw new IllegalStateException("주문 취소가 불가능한 상태입니다.");
        }
        this.status = OrderStatus.CANCELED;
    }
}
