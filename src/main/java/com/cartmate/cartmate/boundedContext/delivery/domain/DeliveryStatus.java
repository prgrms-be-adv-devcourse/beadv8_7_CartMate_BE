package com.cartmate.cartmate.boundedContext.delivery.domain;

public enum DeliveryStatus {
    REQUESTED,  //배송요청 (판매자 확인 전)
    PREPARING,  //배송준비중 (판매자 확인 후)
    SHIPPING,   //배송중
    DELIVERED,  //배송완료
    CANCELED    //배송취소
}
