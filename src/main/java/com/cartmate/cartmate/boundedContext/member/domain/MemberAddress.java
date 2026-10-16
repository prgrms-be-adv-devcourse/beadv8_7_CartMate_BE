package com.cartmate.cartmate.boundedContext.member.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAddress extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    @Column(name = "basic_address", nullable = false)
    private String basicAddress;

    @Column(name = "detail_address")
    private String detailAddress;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
}