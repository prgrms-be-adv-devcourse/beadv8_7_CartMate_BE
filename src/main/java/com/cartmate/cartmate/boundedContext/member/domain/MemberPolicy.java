package com.cartmate.cartmate.boundedContext.member.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

@Service
public class MemberPolicy {

    // 입력 형식 규칙: DTO 어노테이션에서 쓰므로 상수
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,20}$";
    public static final String PASSWORD_MESSAGE = "비밀번호는 8~20자, 영문·숫자·특수문자를 모두 포함해야 합니다.";
    public static final String NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{2,10}$";
    public static final String NICKNAME_MESSAGE = "닉네임은 2~10자의 한글, 영문, 숫자만 가능합니다.";
    public static final String PHONE_REGEX = "^010-\\d{4}-\\d{4}$";
    public static final String PHONE_MESSAGE = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.";

    // 운영 정책 값: yml에서 읽음
    private static int SELLER_MIN_AGE;

    @Value("${custom.member.seller.minAge}")
    public void setSellerMinAge(int minAge) {
        SELLER_MIN_AGE = minAge;
    }

    public int getSellerMinAge() {
        return SELLER_MIN_AGE;
    }

    public boolean canRegisterAsSeller(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears() >= SELLER_MIN_AGE;
    }

    public static final String PHONE_CODE_REGEX = "^\\d{6}$";

    private static int PHONE_CODE_TTL_SECONDS;
    private static int PHONE_VERIFIED_TTL_SECONDS;
    private static int PHONE_MAX_ATTEMPTS;

    @Value("${custom.member.phone.codeTtlSeconds:180}")
    public void setPhoneCodeTtlSeconds(int seconds) {
        PHONE_CODE_TTL_SECONDS = seconds;
    }

    @Value("${custom.member.phone.verifiedTtlSeconds:600}")
    public void setPhoneVerifiedTtlSeconds(int seconds) {
        PHONE_VERIFIED_TTL_SECONDS = seconds;
    }

    @Value("${custom.member.phone.maxAttempts:5}")
    public void setPhoneMaxAttempts(int attempts) {
        PHONE_MAX_ATTEMPTS = attempts;
    }

    public int getPhoneCodeTtlSeconds() { return PHONE_CODE_TTL_SECONDS; }
    public int getPhoneVerifiedTtlSeconds() { return PHONE_VERIFIED_TTL_SECONDS; }
    public int getPhoneMaxAttempts() { return PHONE_MAX_ATTEMPTS; }
}