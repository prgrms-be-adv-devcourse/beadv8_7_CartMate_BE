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

    @Column(name = "address_name", length = 50)
    private String addressName; // 예: 우리팀 홈구장 (선택)

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

    public static MemberAddress create(Member member, String addressName, String receiverName, String receiverPhone,
                                       String zipCode, String basicAddress, String detailAddress) {
        MemberAddress a = new MemberAddress();
        a.member = member;
        a.change(addressName, receiverName, receiverPhone, zipCode, basicAddress, detailAddress);
        a.defaultAddress = false;
        return a;
    }

    public void change(String addressName, String receiverName, String receiverPhone,
                       String zipCode, String basicAddress, String detailAddress) {
        this.addressName = addressName;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipCode = zipCode;
        this.basicAddress = basicAddress;
        this.detailAddress = detailAddress;
    }

    public void markDefault() {
        this.defaultAddress = true;
    }

    public void unmarkDefault() {
        this.defaultAddress = false;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }
}