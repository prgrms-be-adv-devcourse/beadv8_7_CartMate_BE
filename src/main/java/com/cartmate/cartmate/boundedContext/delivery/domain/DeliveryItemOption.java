package com.cartmate.cartmate.boundedContext.delivery.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "delivery_item_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_item_id", nullable = false)
    private DeliveryItem deliveryItem;

    @Column(name = "option_name", nullable = false)
    private String optionName;      //옵션명 (예: 사이즈)

    @Column(name = "option_value", nullable = false)
    private String optionValue;     //옵션값 (예: L)

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;          //옵션 표시 순서

    public static DeliveryItemOption create(
            DeliveryItem deliveryItem, String optionName, String optionValue, int sortOrder
    ) {
        return new DeliveryItemOption(deliveryItem, optionName, optionValue, sortOrder);
    }

    private DeliveryItemOption(
            DeliveryItem deliveryItem, String optionName, String optionValue, int sortOrder
    ) {
        this.deliveryItem = deliveryItem;
        this.optionName = optionName;
        this.optionValue = optionValue;
        this.sortOrder = sortOrder;
    }
}
