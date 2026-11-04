package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.MemberPolicy;
import com.cartmate.cartmate.boundedContext.member.domain.SellerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 판매자 등록 폼의 정산 계좌 확인 요청. 등록 요청과 같은 값을 보내야 통과 표시가 등록과 이어진다.
public record SellerAccountVerifyRequest(
        @NotNull(message = "판매자 유형을 선택해주세요.")
        SellerType sellerType,

        @Size(max = 100, message = "상호명은 100자 이하로 입력해주세요.")
        String businessName,

        @Pattern(regexp = MemberPolicy.BUSINESS_NUMBER_REGEX, message = MemberPolicy.BUSINESS_NUMBER_MESSAGE)
        String businessNumber, // 개인이면 null

        @NotBlank(message = "정산 은행을 선택해주세요.")
        String bankName,

        @NotBlank(message = "예금주를 입력해주세요.")
        @Size(max = 50)
        String accountHolder,

        @NotBlank(message = "계좌번호를 입력해주세요.")
        @Pattern(regexp = MemberPolicy.ACCOUNT_NUMBER_REGEX, message = MemberPolicy.ACCOUNT_NUMBER_MESSAGE)
        String accountNumber
) {
}
