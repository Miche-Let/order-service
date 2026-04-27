package com.michelet.order.domain.model;

import com.michelet.common.entity.BaseEntity;
import com.michelet.order.domain.model.vo.Price;
import com.michelet.order.domain.model.vo.Quantity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private UUID optionId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal orderPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Builder(access = AccessLevel.PRIVATE)
    private OrderItem(UUID optionId, String productName, BigDecimal orderPrice, Integer quantity) {
        this.optionId = optionId;
        this.productName = productName;
        this.orderPrice = orderPrice;
        this.quantity = quantity;
    }

    public static OrderItem create(UUID optionId, String productName, BigDecimal orderPrice, Integer quantity) {
        Price validPrice = Price.of(orderPrice);
        Quantity validQuantity = new Quantity(quantity);
        return new OrderItem(optionId, productName, validPrice.value(), validQuantity.value());
    }

    protected void assignOrder(Order order) {
        this.order = order;
    }

    public BigDecimal getLinePrice() {
        return orderPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
