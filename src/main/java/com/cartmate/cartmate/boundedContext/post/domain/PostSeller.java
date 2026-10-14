package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.shared.member.dto.SellerDto;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "POST_SELLER")
@NoArgsConstructor
@Getter
public class PostSeller {
    @Id
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

    public PostSeller(int id, int memberId, String sellerType, String businessName, String businessNumber, String phoneNumber, String bankName,
                      String accountHolder, String accountNumber, String status, LocalDateTime createDate, LocalDateTime modifyDate) {
        this.id = id;
        this.memberId = memberId;
        this.sellerType = sellerType;
        this.businessName = businessName;
        this.businessNumber = businessNumber;
        this.phoneNumber = phoneNumber;
        this.bankName = bankName;
        this.accountHolder = accountHolder;
        this.accountNumber = accountNumber;
        this.status = status;
        this.createDate = createDate;
        this.modifyDate = modifyDate;
    }

}
