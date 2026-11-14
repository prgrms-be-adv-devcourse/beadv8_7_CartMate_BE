package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.BankCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.SellerAccountFingerprint;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.SellerAccountProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.SellerType;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.SellerAccountVerifyRequest;
import com.cartmate.cartmate.boundedContext.memberService.member.out.AccountVerificationStore;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.account.AccountVerifier;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberSellerAccountVerifyUseCase {
    private static final DateTimeFormatter BIRTH = DateTimeFormatter.ofPattern("yyMMdd");

    private final MemberRepository memberRepository;
    private final AccountVerifier accountVerifier;
    private final AccountVerificationStore accountVerificationStore;
    private final SellerAccountProperties properties;

    public void verify(Long memberId, SellerAccountVerifyRequest req) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException(MemberErrorCode.MEMBER_NOT_FOUND));

        boolean business = req.sellerType() == SellerType.BUSINESS;
        String businessName = trimToNull(req.businessName());
        String representativeName = trimToNull(req.representativeName());
        String businessNumber = trimToNull(req.businessNumber());
        if (business && (businessName == null || representativeName == null || businessNumber == null)) {
            throw new ServiceException(MemberErrorCode.SELLER_BUSINESS_INFO_REQUIRED);
        }

        String bankName = req.bankName().trim();
        String accountNumber = req.accountNumber().trim();
        String accountHolder = req.accountHolder().trim();
        BankCode bank = BankCode.fromName(bankName);

        // 값 검증을 통과한 요청만 횟수를 센다. 외부 호출이 나가는 요청이 제한 대상이다.
        if (!accountVerificationStore.tryAcquireAttempt(memberId, properties.maxAttempts(), properties.attemptWindow())) {
            throw new ServiceException(MemberErrorCode.SELLER_ACCOUNT_VERIFY_TOO_MANY_REQUESTS);
        }

        // 개인은 회원 생년월일, 사업자는 사업자등록번호가 실명번호다. 비교할 이름도 유형에 따라 다르다.
        // 사업자는 법인명 계좌와 대표자 명의 개인사업자 계좌를 모두 받기 위해 상호명 또는 대표자명이면 통과한다.
        String realNumber = business ? businessNumber.replace("-", "") : member.getBirthDate().format(BIRTH);
        List<String> acceptableHolders = business ? List.of(businessName, representativeName) : List.of(accountHolder);

        // 불일치의 원인(실명번호, 이름, 없는 계좌)은 구분하지 않고 하나로 응답한다.
        if (!accountVerifier.isHolderMatched(bank, accountNumber, realNumber, acceptableHolders)) {
            throw new ServiceException(MemberErrorCode.SELLER_ACCOUNT_MISMATCH);
        }

        String fingerprint = SellerAccountFingerprint.of(req.sellerType(), bankName, accountNumber,
                accountHolder, businessName, representativeName, businessNumber);
        accountVerificationStore.saveVerified(memberId, fingerprint, properties.verifiedTtl());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
