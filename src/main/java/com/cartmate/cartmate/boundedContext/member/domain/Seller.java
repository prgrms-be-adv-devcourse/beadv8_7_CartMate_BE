package com.cartmate.cartmate.boundedContext.member.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "seller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseIdAndTime {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "seller_type", nullable = false, length = 20)
    private SellerType sellerType;

    @Column(name = "business_name", length = 100)
    private String businessName;

    @Column(name = "representative_name", length = 50)
    private String representativeName;

    @Column(name = "business_number", length = 20, unique = true)
    private String businessNumber;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SellerStatus status;

    public static Seller create(Member member, SellerType type, String businessName, String representativeName,
                                String businessNumber, String phoneNumber, String bankName,
                                String accountHolder, String accountNumber) {
        Seller seller = new Seller();
        seller.member = member;
        seller.sellerType = type;
        seller.businessName = businessName;
        seller.representativeName = representativeName;
        seller.businessNumber = businessNumber;
        seller.phoneNumber = phoneNumber;
        seller.bankName = bankName;
        seller.accountHolder = accountHolder;
        seller.accountNumber = accountNumber;
        seller.status = SellerStatus.ACTIVE;
        return seller;
    }

    public String getDisplayName() {
        return sellerType == SellerType.BUSINESS ? businessName : member.getNickname();
    }

    // 화면에 보여줄 때는 뒤 4자리만
    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}