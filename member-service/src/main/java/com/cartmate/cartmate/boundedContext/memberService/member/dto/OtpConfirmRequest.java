package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpConfirmRequest(
        @NotBlank(message = "OTP 코드는 필수입니다.")
        @Pattern(regexp = "^\\d{6}$", message = "OTP 코드는 숫자 6자리입니다.")
        String code
) {
}
