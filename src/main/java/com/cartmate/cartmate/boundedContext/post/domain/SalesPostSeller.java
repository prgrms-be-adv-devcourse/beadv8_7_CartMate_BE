package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "POST_SELLER")
@NoArgsConstructor
@Getter
public class SalesPostSeller extends BaseManualIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long memberId;

    @Column(nullable = false)
    private String sellerType;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String businessNumber;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String accountHolder;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
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
