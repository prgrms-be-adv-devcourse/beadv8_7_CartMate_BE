package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.boundedContext.member.dto.MemberAddressRequest;
import com.cartmate.cartmate.boundedContext.member.dto.MemberAddressResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members/me/addresses")
@RequiredArgsConstructor
public class MemberAddressController {
    private final MemberFacade memberFacade;

    @GetMapping
    @Operation(operationId = "getMyAddresses", summary = "내 배송지 목록")
    public RsData<List<MemberAddressResponse>> list(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "배송지 목록 조회 성공", memberFacade.getAddresses(user.memberId()));
    }

    @PostMapping
    @Operation(operationId = "createMyAddress", summary = "배송지 추가")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<MemberAddressResponse> create(@AuthenticationPrincipal SecurityUser user,
                                                @Valid @RequestBody MemberAddressRequest request) {
        return new RsData<>("201-1", "배송지가 등록되었습니다.", memberFacade.createAddress(user.memberId(), request));
    }

    @PutMapping("/{addressId}")
    @Operation(operationId = "updateMyAddress", summary = "배송지 수정")
    public RsData<MemberAddressResponse> update(@AuthenticationPrincipal SecurityUser user,
                                                @PathVariable Long addressId,
                                                @Valid @RequestBody MemberAddressRequest request) {
        return new RsData<>("200-1", "배송지가 수정되었습니다.",
                memberFacade.updateAddress(user.memberId(), addressId, request));
    }

    @PatchMapping("/{addressId}/default")
    @Operation(operationId = "makeDefaultMyAddress", summary = "기본 배송지 지정")
    public RsData<MemberAddressResponse> makeDefault(@AuthenticationPrincipal SecurityUser user,
                                                     @PathVariable Long addressId) {
        return new RsData<>("200-1", "기본 배송지로 지정되었습니다.",
                memberFacade.makeDefaultAddress(user.memberId(), addressId));
    }

    @DeleteMapping("/{addressId}")
    @Operation(operationId = "deleteMyAddress", summary = "배송지 삭제")
    public RsData<Void> delete(@AuthenticationPrincipal SecurityUser user, @PathVariable Long addressId) {
        memberFacade.deleteAddress(user.memberId(), addressId);
        return new RsData<>("200-1", "배송지가 삭제되었습니다.");
    }
}