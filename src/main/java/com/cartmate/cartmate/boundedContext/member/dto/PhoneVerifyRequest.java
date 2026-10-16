package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.MemberPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerifyRequest(
        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = MemberPolicy.PHONE_REGEX, message = MemberPolicy.PHONE_MESSAGE)
        String phone,

        @NotBlank(message = "인증번호는 필수입니다.")
        @Pattern(regexp = MemberPolicy.PHONE_CODE_REGEX, message = "인증번호는 숫자 6자리입니다.")
        String code
) {
}