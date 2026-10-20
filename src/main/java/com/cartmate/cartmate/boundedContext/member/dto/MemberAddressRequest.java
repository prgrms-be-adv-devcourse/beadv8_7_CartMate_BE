package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.MemberPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberAddressRequest(
        @Size(max = 50, message = "배송지 명칭은 50자 이하로 입력해주세요.")
        String addressName,

        @NotBlank(message = "받는 분을 입력해주세요.")
        @Size(max = 50, message = "받는 분은 50자 이하로 입력해주세요.")
        String receiverName,

        @NotBlank(message = "연락처를 입력해주세요.")
        @Pattern(regexp = MemberPolicy.PHONE_REGEX, message = MemberPolicy.PHONE_MESSAGE)
        String receiverPhone,

        @NotBlank(message = "우편번호를 입력해주세요.")
        @Pattern(regexp = MemberPolicy.ZIP_CODE_REGEX, message = MemberPolicy.ZIP_CODE_MESSAGE)
        String zipCode,

        @NotBlank(message = "주소를 입력해주세요.")
        @Size(max = 255)
        String basicAddress,

        @Size(max = 255)
        String detailAddress,

        boolean makeDefault
) {
}