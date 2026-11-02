package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.Seller;
import com.cartmate.cartmate.boundedContext.member.domain.SellerType;
import com.cartmate.cartmate.boundedContext.member.dto.SellerRegisterRequest;
import com.cartmate.cartmate.boundedContext.member.dto.SellerResponse;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.SellerRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.JwtProvider;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberSellerUseCase {
    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final EventPublisher eventPublisher;
    private final JwtProvider jwtProvider;

    public record RegisterResult(SellerResponse seller, String accessToken) {
    }

    public Optional<SellerResponse> getMySeller(Long memberId) {
        return sellerRepository.findByMemberId(memberId).map(SellerResponse::from);
    }

    public boolean isBusinessNumberAvailable(String businessNumber) {
        return !sellerRepository.existsByBusinessNumber(businessNumber.trim());
    }

    public RegisterResult register(Long memberId, SellerRegisterRequest req) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));

        if (sellerRepository.existsByMemberId(memberId)) {
            throw new ServiceException("409-SELLER_ALREADY_REGISTERED", "이미 판매자로 등록된 회원입니다.");
        }

        boolean business = req.sellerType() == SellerType.BUSINESS;
        String businessName = trimToNull(req.businessName());
        String representativeName = trimToNull(req.representativeName());
        String businessNumber = trimToNull(req.businessNumber());

        if (business) {
            if (businessName == null || representativeName == null || businessNumber == null) {
                throw new ServiceException("400-SELLER_BUSINESS_INFO_REQUIRED", "사업자는 상호명, 대표자명, 사업자등록번호를 모두 입력해야 합니다.");
            }
            if (sellerRepository.existsByBusinessNumber(businessNumber)) {
                throw new ServiceException("409-SELLER_DUPLICATE_BUSINESS_NUMBER", "이미 등록된 사업자등록번호입니다.");
            }
        } else {
            // 개인 판매자는 사업자 정보를 저장하지 않음
            businessName = null;
            representativeName = null;
            businessNumber = null;
        }

        member.becomeSeller(); // 나이, 상태, 역할 검사

        Seller seller;
        try {
            seller = sellerRepository.saveAndFlush(Seller.create(member, req.sellerType(), businessName,
                    representativeName, businessNumber, req.phoneNumber().trim(), req.bankName().trim(),
                    req.accountHolder().trim(), req.accountNumber().trim()));
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException("409-SELLER_ALREADY_REGISTERED", "이미 등록된 판매자 정보입니다.");
        }

        eventPublisher.publish(
                new SellerRegisteredEvent(
                        seller.getId(),
                        member.getId(),
                        seller.getSellerType().name(),
                        seller.getDisplayName(),
                        seller.getBusinessNumber(),
                        seller.getPhoneNumber(),
                        seller.getBankName(),
                        seller.getAccountHolder(),
                        seller.getAccountNumber(),
                        seller.getStatus().name(),
                        seller.getCreateDate(),
                        seller.getModifyDate()
        ));

        // 역할이 SELLER로 바뀌었으니 새 토큰 발급 (다시 로그인하지 않아도 판매자로 인식, 판매자 ID 포함)
        String accessToken = jwtProvider.createAccessToken(
                member.getId(), member.getEmail(), member.getRole().name(), seller.getId());

        return new RegisterResult(SellerResponse.from(seller), accessToken);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}