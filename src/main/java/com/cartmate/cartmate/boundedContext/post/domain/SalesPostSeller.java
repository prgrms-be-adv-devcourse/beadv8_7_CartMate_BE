package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.SellerStatus;
import com.cartmate.cartmate.boundedContext.member.domain.SellerType;
import com.cartmate.cartmate.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "POST_SELLER")
@NoArgsConstructor
@Getter
public class SalesPostSeller extends BaseManualIdAndTime {
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "seller_type", nullable = false, length = 20)
    private String sellerType;

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

    @Column(nullable = false, length = 20)
    private String status;

    public SalesPostSeller(Long id, Long memberId, String sellerType, String businessName, String businessNumber, String phoneNumber, String bankName,
                      String accountHolder, String accountNumber, String status, LocalDateTime createDate, LocalDateTime modifyDate) {
        super(id, createDate, modifyDate);
        this.memberId = memberId;
        this.sellerType = sellerType;
        this.businessName = businessName;
        this.businessNumber = businessNumber;
        this.phoneNumber = phoneNumber;
        this.bankName = bankName;
        this.accountHolder = accountHolder;
        this.accountNumber = accountNumber;
        this.status = status;
    }

}
