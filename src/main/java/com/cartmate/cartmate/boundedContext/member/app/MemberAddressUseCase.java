package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.MemberAddress;
import com.cartmate.cartmate.boundedContext.member.domain.MemberPolicy;
import com.cartmate.cartmate.boundedContext.member.dto.MemberAddressRequest;
import com.cartmate.cartmate.boundedContext.member.dto.MemberAddressResponse;
import com.cartmate.cartmate.boundedContext.member.out.MemberAddressRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberAddressUseCase {
    private final MemberAddressRepository memberAddressRepository;
    private final MemberSupport memberSupport;

    public List<MemberAddressResponse> list(Long memberId) {
        return memberAddressRepository.findAllByMemberIdOrderByDefaultAddressDescIdDesc(memberId).stream()
                .map(MemberAddressResponse::from)
                .toList();
    }

    public MemberAddressResponse create(Long memberId, MemberAddressRequest req) {
        long count = memberAddressRepository.countByMemberId(memberId);
        if (count >= MemberPolicy.MAX_ADDRESS_COUNT) {
            throw new ServiceException("409-MEMBER_ADDRESS_LIMIT",
                    "배송지는 최대 %d개까지 등록할 수 있습니다.".formatted(MemberPolicy.MAX_ADDRESS_COUNT));
        }

        Member member = memberSupport.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));

        MemberAddress address = memberAddressRepository.save(MemberAddress.create(member,
                trimToNull(req.addressName()), req.receiverName().trim(), req.receiverPhone(),
                req.zipCode(), req.basicAddress().trim(), trimToNull(req.detailAddress())));

        // 첫 배송지이거나 기본으로 지정하면 기본 배송지로
        if (count == 0 || req.makeDefault()) {
            changeDefault(memberId, address);
        }
        return MemberAddressResponse.from(address);
    }

    public MemberAddressResponse update(Long memberId, Long addressId, MemberAddressRequest req) {
        MemberAddress address = getOwned(memberId, addressId);
        address.change(trimToNull(req.addressName()), req.receiverName().trim(), req.receiverPhone(),
                req.zipCode(), req.basicAddress().trim(), trimToNull(req.detailAddress()));

        if (req.makeDefault() && !address.isDefaultAddress()) {
            changeDefault(memberId, address);
        }
        return MemberAddressResponse.from(address);
    }

    public MemberAddressResponse makeDefault(Long memberId, Long addressId) {
        MemberAddress address = getOwned(memberId, addressId);
        if (!address.isDefaultAddress()) {
            changeDefault(memberId, address);
        }
        return MemberAddressResponse.from(address);
    }

    public void delete(Long memberId, Long addressId) {
        MemberAddress address = getOwned(memberId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        memberAddressRepository.delete(address);
        memberAddressRepository.flush();

        // 기본 배송지를 지웠으면 가장 최근 배송지를 기본으로
        if (wasDefault) {
            memberAddressRepository.findAllByMemberIdOrderByDefaultAddressDescIdDesc(memberId).stream()
                    .findFirst()
                    .ifPresent(MemberAddress::markDefault);
        }
    }

    // 기본 배송지는 회원당 하나만
    private void changeDefault(Long memberId, MemberAddress target) {
        memberAddressRepository.findFirstByMemberIdAndDefaultAddressTrue(memberId)
                .filter(current -> !current.getId().equals(target.getId()))
                .ifPresent(MemberAddress::unmarkDefault);
        target.markDefault();
    }

    private MemberAddress getOwned(Long memberId, Long addressId) {
        MemberAddress address = memberAddressRepository.findById(addressId)
                .orElseThrow(() -> new ServiceException("404-MEMBER_ADDRESS_NOT_FOUND", "배송지를 찾을 수 없습니다."));
        if (!address.isOwnedBy(memberId)) {
            // 남의 배송지가 있다는 사실도 숨기려고 404로 응답
            throw new ServiceException("404-MEMBER_ADDRESS_NOT_FOUND", "배송지를 찾을 수 없습니다.");
        }
        return address;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}