package com.cartmate.cartmate.shared.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SellerDto {
    private int id;
    private int memberId;
    private String sellerType;
    private String businessName;
    private String businessNumber;
    private String phoneNumber;
    private String bankName;
    private String accountHolder;
    private String accountNumber;
    private String status;
    private LocalDateTime createDate;
    private LocalDateTime modifyDate;
}
