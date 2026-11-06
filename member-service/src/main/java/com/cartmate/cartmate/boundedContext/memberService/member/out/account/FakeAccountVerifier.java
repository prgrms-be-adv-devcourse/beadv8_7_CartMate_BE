package com.cartmate.cartmate.boundedContext.memberService.member.out.account;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.BankCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

// 오픈뱅킹 승인 전까지 쓰는 가짜 구현체. 실제 계좌의 실존과 명의는 확인하지 않는다.
// 계좌번호 끝자리로 결과를 정한다: 0000 → 불일치, 9999 → 호출 실패(503), 그 외 → 통과.
@Slf4j
@Component
@ConditionalOnProperty(prefix = "custom.openbanking", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeAccountVerifier implements AccountVerifier {

    public FakeAccountVerifier() {
        log.warn("[계좌 확인] fake 모드입니다. 실제 계좌 검증이 비활성화되어 있습니다.");
    }

    @Override
    public boolean isHolderMatched(BankCode bank, String accountNumber, String realNumber, List<String> acceptableHolders) {
        if (accountNumber.endsWith("9999")) {
            throw new ServiceException("503-SELLER_ACCOUNT_VERIFY_UNAVAILABLE",
                    "일시적으로 계좌를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
        return !accountNumber.endsWith("0000");
    }
}
