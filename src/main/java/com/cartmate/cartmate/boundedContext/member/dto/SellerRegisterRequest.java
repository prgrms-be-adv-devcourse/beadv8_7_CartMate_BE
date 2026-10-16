package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.MemberPolicy;
import com.cartmate.cartmate.boundedContext.member.domain.SellerType;
import jakarta.validation.constraints.*;

public record SellerRegisterRequest(
        @NotNull(message = "판매자 유형을 선택해주세요.")
        SellerType sellerType,

        @Size(max = 100, message = "상호명은 100자 이하로 입력해주세요.")
        String businessName,

        @Size(max = 50, message = "대표자명은 50자 이하로 입력해주세요.")
        String representativeName,

        @Pattern(regexp = MemberPolicy.BUSINESS_NUMBER_REGEX, message = MemberPolicy.BUSINESS_NUMBER_MESSAGE)
        String businessNumber, // 개인이면 null

        @NotBlank(message = "담당자 연락처를 입력해주세요.")
        @Pattern(regexp = MemberPolicy.PHONE_REGEX, message = MemberPolicy.PHONE_MESSAGE)
        String phoneNumber,

        @NotBlank(message = "정산 은행을 선택해주세요.")
        String bankName,

        @NotBlank(message = "예금주를 입력해주세요.")
        @Size(max = 50)
        String accountHolder,

        @NotBlank(message = "계좌번호를 입력해주세요.")
        @Pattern(regexp = MemberPolicy.ACCOUNT_NUMBER_REGEX, message = MemberPolicy.ACCOUNT_NUMBER_MESSAGE)
        String accountNumber,

        @AssertTrue(message = "판매자 이용약관에 동의해주세요.")
        boolean agreed
) {
}