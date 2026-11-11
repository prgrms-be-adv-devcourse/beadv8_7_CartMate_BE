package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailVerifyRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100)
        String email,

        @NotBlank(message = "인증번호는 필수입니다.")
        @Pattern(regexp = MemberPolicy.PHONE_CODE_REGEX, message = "인증번호는 숫자 6자리입니다.")
        String code
) {
}
