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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    @Getter(AccessLevel.NONE) // 캡슐화를 위해 Lombok 자동 생성 방지
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
        // 필수 값에 대한 Fail-fast 검증
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(reservationId, "reservationId는 필수입니다.");
        Objects.requireNonNull(restaurantId, "restaurantId는 필수입니다.");
        if (orderName == null || orderName.trim().isEmpty()) {
            throw new IllegalArgumentException("orderName은 필수이며 비어있을 수 없습니다.");
        }
        Objects.requireNonNull(reservedDate, "reservedDate는 필수입니다.");
        Objects.requireNonNull(expiredAt, "expiredAt은 필수입니다.");

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 최소 1개 이상이어야 합니다.");
        }
        Order order = new Order(userId, reservationId, restaurantId, orderName, reservedDate, receivingMethod,
            expiredAt);
        // addOrderItem 내부에서 점진적 덧셈을 하므로 N^2 문제 해결 및 별도 calculateTotalAmount 호출 불필요해짐!
        items.forEach(order::addOrderItem);
        return order;
    }

    public void addOrderItem(OrderItem item) {
        // Null 가드 및 소유권 검증
        if (item == null) {
            throw new IllegalArgumentException("추가할 주문 항목(OrderItem)이 null입니다.");
        }
        if (item.getOrder() != null && item.getOrder() != this) {
            throw new IllegalStateException("해당 주문 항목은 이미 다른 주문에 할당되어 있습니다.");
        }

        this.orderItems.add(item);
        item.assignOrder(this);
        // O(1) 복잡도로 상태 정합성 유지 (N^2 문제 회피)
        this.totalAmount = this.totalAmount.add(item.getLinePrice());
    }

    // 외부 노출 시 읽기 전용 리스트 반환을 통한 상태 변경 차단
    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(this.orderItems);
    }

    // 향후 주문 상품 삭제 등 요구사항을 위한 역방향 연관관계 해제 및 정합성 유지 메서드
    public void removeOrderItem(OrderItem item) {
        if (this.orderItems.remove(item)) {
            item.assignOrder(null);
            this.totalAmount = this.totalAmount.subtract(item.getLinePrice());
        }
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
