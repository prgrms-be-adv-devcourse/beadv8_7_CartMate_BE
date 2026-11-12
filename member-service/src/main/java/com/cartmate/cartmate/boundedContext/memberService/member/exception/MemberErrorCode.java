package com.cartmate.cartmate.boundedContext.memberService.member.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 회원 서비스 오류 코드. 다른 서비스(OrderErrorCode 등)와 같은 방식.
 * 응답의 resultCode는 "HTTP상태-코드" (예: 400-MEMBER_EMAIL_CODE_MISMATCH) 그대로라 프론트는 바뀌지 않는다.
 *
 * <p>메시지에 값이 들어가는 경우(남은 횟수, 몇 초 뒤 등)는
 * {@code new ServiceException(MemberErrorCode.X.resultCode(), 메시지)}로 메시지만 바꿔 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    // ===== 회원·인증 =====
    MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    MEMBER_PHONE_CODE_NOT_SENT("MEMBER_PHONE_CODE_NOT_SENT", "인증번호를 먼저 요청해주세요.", HttpStatus.BAD_REQUEST),
    MEMBER_PHONE_CODE_EXPIRED("MEMBER_PHONE_CODE_EXPIRED", "인증번호가 만료되었습니다. 다시 요청해주세요.", HttpStatus.BAD_REQUEST),
    MEMBER_PHONE_TOO_MANY_ATTEMPTS("MEMBER_PHONE_TOO_MANY_ATTEMPTS", "인증 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_PHONE_CODE_MISMATCH("MEMBER_PHONE_CODE_MISMATCH", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_DUPLICATE_NICKNAME("MEMBER_DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
    MEMBER_PHONE_NOT_VERIFIED("MEMBER_PHONE_NOT_VERIFIED", "휴대폰 인증을 완료해주세요.", HttpStatus.BAD_REQUEST),
    MEMBER_PHONE_RESEND_TOO_SOON("MEMBER_PHONE_RESEND_TOO_SOON", "잠시 후 다시 요청해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_PHONE_TOO_MANY_SENDS("MEMBER_PHONE_TOO_MANY_SENDS", "인증번호를 너무 많이 요청했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_TRUSTED_DEVICE_NOT_FOUND("MEMBER_TRUSTED_DEVICE_NOT_FOUND", "기기를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    AUTH_REFRESH_INVALID("AUTH_REFRESH_INVALID", "로그인이 만료되었습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED),
    MEMBER_PASSWORD_MISMATCH("MEMBER_PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_PASSWORD_SAME("MEMBER_PASSWORD_SAME", "새 비밀번호가 현재 비밀번호와 같습니다.", HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH("PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_WITHDRAWAL_BLOCKED("MEMBER_WITHDRAWAL_BLOCKED", "지금은 탈퇴할 수 없습니다.", HttpStatus.CONFLICT),
    COMMON_INVALID_INPUT("COMMON_INVALID_INPUT", "정지 사유와 기간을 입력해주세요.", HttpStatus.BAD_REQUEST),
    MEMBER_ADDRESS_LIMIT("MEMBER_ADDRESS_LIMIT", "배송지를 더 등록할 수 없습니다.", HttpStatus.CONFLICT),
    MEMBER_ADDRESS_NOT_FOUND("MEMBER_ADDRESS_NOT_FOUND", "배송지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    MEMBER_DUPLICATE_EMAIL("MEMBER_DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    MEMBER_EMAIL_RESEND_TOO_SOON("MEMBER_EMAIL_RESEND_TOO_SOON", "잠시 후 다시 요청해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_EMAIL_TOO_MANY_SENDS("MEMBER_EMAIL_TOO_MANY_SENDS", "인증 메일을 너무 많이 요청했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_EMAIL_SEND_FAILED("MEMBER_EMAIL_SEND_FAILED", "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    MEMBER_EMAIL_CODE_NOT_SENT("MEMBER_EMAIL_CODE_NOT_SENT", "인증번호를 먼저 요청해주세요.", HttpStatus.BAD_REQUEST),
    MEMBER_EMAIL_CODE_EXPIRED("MEMBER_EMAIL_CODE_EXPIRED", "인증번호가 만료되었습니다. 다시 요청해주세요.", HttpStatus.BAD_REQUEST),
    MEMBER_EMAIL_CODE_MISMATCH("MEMBER_EMAIL_CODE_MISMATCH", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_EMAIL_TOO_MANY_ATTEMPTS("MEMBER_EMAIL_TOO_MANY_ATTEMPTS", "인증 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_LOGIN_FAILED("MEMBER_LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    MEMBER_MFA_TOO_MANY_ATTEMPTS("MEMBER_MFA_TOO_MANY_ATTEMPTS", "인증 시도 횟수를 초과했습니다. 다시 로그인해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_MFA_CODE_MISMATCH("MEMBER_MFA_CODE_MISMATCH", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_MFA_RESEND_TOO_SOON("MEMBER_MFA_RESEND_TOO_SOON", "잠시 후 다시 요청해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_MFA_TOO_MANY_SENDS("MEMBER_MFA_TOO_MANY_SENDS", "인증 메일을 너무 많이 요청했습니다. 다시 로그인해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_MFA_SEND_FAILED("MEMBER_MFA_SEND_FAILED", "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    MEMBER_MFA_LOCKED("MEMBER_MFA_LOCKED", "인증 실패가 많아 잠시 로그인할 수 없습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MEMBER_MFA_EXPIRED("MEMBER_MFA_EXPIRED", "인증 시간이 지났습니다. 다시 로그인해주세요.", HttpStatus.BAD_REQUEST),
    MEMBER_EMAIL_NOT_VERIFIED("MEMBER_EMAIL_NOT_VERIFIED", "이메일 인증을 완료해주세요.", HttpStatus.BAD_REQUEST),
    MEMBER_DUPLICATE("MEMBER_DUPLICATE", "이미 사용 중인 이메일 또는 닉네임입니다.", HttpStatus.CONFLICT),
    MEMBER_SUSPENDED("MEMBER_SUSPENDED", "이용이 정지된 계정입니다.", HttpStatus.FORBIDDEN),
    MEMBER_WITHDRAWAL_ALREADY_REQUESTED("MEMBER_WITHDRAWAL_ALREADY_REQUESTED", "이미 탈퇴를 신청했습니다.", HttpStatus.CONFLICT),
    MEMBER_CANNOT_WITHDRAW("MEMBER_CANNOT_WITHDRAW", "지금 상태에서는 탈퇴를 신청할 수 없습니다.", HttpStatus.CONFLICT),
    MEMBER_WITHDRAWAL_NOT_REQUESTED("MEMBER_WITHDRAWAL_NOT_REQUESTED", "탈퇴를 신청한 상태가 아닙니다.", HttpStatus.CONFLICT),
    MEMBER_WITHDRAWN("MEMBER_WITHDRAWN", "탈퇴한 회원은 정지할 수 없습니다.", HttpStatus.CONFLICT),
    MEMBER_ALREADY_SUSPENDED("MEMBER_ALREADY_SUSPENDED", "이미 정지된 회원입니다.", HttpStatus.CONFLICT),
    MEMBER_NOT_SUSPENDED("MEMBER_NOT_SUSPENDED", "정지 상태가 아닌 회원입니다.", HttpStatus.CONFLICT),
    MEMBER_NOT_ACTIVE("MEMBER_NOT_ACTIVE", "정상 상태인 회원만 판매자로 등록할 수 있습니다.", HttpStatus.FORBIDDEN),

    // ===== 관리자 =====
    ADMIN_CANNOT_SUSPEND_SELF("ADMIN_CANNOT_SUSPEND_SELF", "자기 자신은 정지할 수 없습니다.", HttpStatus.BAD_REQUEST),
    ADMIN_CANNOT_SUSPEND_ADMIN("ADMIN_CANNOT_SUSPEND_ADMIN", "관리자 계정은 정지할 수 없습니다.", HttpStatus.BAD_REQUEST),

    // ===== 판매자 =====
    SELLER_BUSINESS_INFO_REQUIRED("SELLER_BUSINESS_INFO_REQUIRED", "사업자는 상호명, 대표자명, 사업자등록번호를 모두 입력해야 합니다.", HttpStatus.BAD_REQUEST),
    SELLER_ACCOUNT_VERIFY_TOO_MANY_REQUESTS("SELLER_ACCOUNT_VERIFY_TOO_MANY_REQUESTS", "계좌 확인 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    SELLER_ACCOUNT_MISMATCH("SELLER_ACCOUNT_MISMATCH", "예금주 정보가 일치하지 않습니다. 은행에 등록된 이름과 똑같이 입력해 주세요.", HttpStatus.BAD_REQUEST),
    SELLER_ALREADY_REGISTERED("SELLER_ALREADY_REGISTERED", "이미 판매자로 등록된 회원입니다.", HttpStatus.CONFLICT),
    SELLER_DUPLICATE_BUSINESS_NUMBER("SELLER_DUPLICATE_BUSINESS_NUMBER", "이미 등록된 사업자등록번호입니다.", HttpStatus.CONFLICT),
    SELLER_ACCOUNT_NOT_VERIFIED("SELLER_ACCOUNT_NOT_VERIFIED", "계좌 확인이 필요합니다. 정산 계좌를 먼저 확인해주세요.", HttpStatus.BAD_REQUEST),
    SELLER_ACCOUNT_DUPLICATE("SELLER_ACCOUNT_DUPLICATE", "이미 등록된 정산 계좌입니다.", HttpStatus.CONFLICT),
    SELLER_ACCOUNT_VERIFY_UNAVAILABLE("SELLER_ACCOUNT_VERIFY_UNAVAILABLE", "일시적으로 계좌를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    SELLER_BANK_NOT_SUPPORTED("SELLER_BANK_NOT_SUPPORTED", "지원하지 않는 은행입니다.", HttpStatus.BAD_REQUEST),
    SELLER_ADMIN_NOT_ALLOWED("SELLER_ADMIN_NOT_ALLOWED", "관리자 계정은 판매자로 등록할 수 없습니다.", HttpStatus.BAD_REQUEST),
    SELLER_UNDERAGE("SELLER_UNDERAGE", "판매자 등록 가능 나이가 아닙니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}
