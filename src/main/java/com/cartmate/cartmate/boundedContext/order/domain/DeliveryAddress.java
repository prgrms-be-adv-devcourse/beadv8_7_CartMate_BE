package com.cartmate.cartmate.boundedContext.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAddress {

    @Column(length = 50)
    private String receiverName;

    @Column(length = 20)
    private String receiverPhone;

    @Column(length = 10)
    private String zipCode;

    @Column(length = 255)
    private String address;

    @Column(length = 255)
    private String addressDetail;

    private DeliveryAddress(String receiverName, String receiverPhone, String zipCode,
                            String address, String addressDetail) {
        if (isBlank(receiverName) || isBlank(receiverPhone) || isBlank(zipCode) || isBlank(address)) {
            throw new IllegalArgumentException("받는 사람, 연락처, 우편번호, 주소는 모두 입력해야 합니다.");
        }
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
    }

    public static DeliveryAddress of(String receiverName, String receiverPhone, String zipCode,
                                     String address, String addressDetail) {
        return new DeliveryAddress(receiverName, receiverPhone, zipCode, address, addressDetail);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
