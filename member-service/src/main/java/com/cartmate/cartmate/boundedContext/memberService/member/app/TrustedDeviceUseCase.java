package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MfaProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.TrustedDevice;
import com.cartmate.cartmate.boundedContext.memberService.member.out.TrustedDeviceRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 신뢰 기기: 로그인 때 확인 + 토큰 교체, 등록, 목록, 해제(행 삭제), 만료 정리
@Service
@RequiredArgsConstructor
class TrustedDeviceUseCase {

    private static final int DEVICE_NAME_MAX = 200;

    private final TrustedDeviceRepository repository;
    private final MfaProperties properties;

    // 쿠키의 기기 토큰이 이 회원의 쓸 수 있는 기기면 새 토큰으로 바꿔 돌려준다. 아니면 비어 있음 → 2차 인증.
    // 동시에 같은 토큰으로 두 번 오면 하나만 바뀌고, 진 쪽은 2차 인증으로 넘어간다
    Optional<String> useForLogin(Long memberId, String rawToken, LocalDateTime now) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String newToken = SecureTokens.newToken();
        boolean rotated = repository.rotate(memberId, SecureTokens.sha256(rawToken), SecureTokens.sha256(newToken), now) == 1;
        return rotated ? Optional.of(newToken) : Optional.empty();
    }

    // 2차 인증 성공 + "30일 기억": 새 기기 등록, 쿠키에 넣을 토큰을 돌려준다
    String register(Long memberId, String deviceName, LocalDateTime now) {
        String token = SecureTokens.newToken();
        repository.save(TrustedDevice.register(memberId, SecureTokens.sha256(token), shorten(deviceName), now,
                now.plus(properties.trustedDeviceTtl())));
        return token;
    }

    List<TrustedDevice> list(Long memberId, LocalDateTime now) {
        return repository.findUsable(memberId, now);
    }

    void revoke(Long memberId, Long deviceId) {
        if (repository.deleteByIdAndMemberId(deviceId, memberId) == 0) {
            throw new ServiceException(MemberErrorCode.MEMBER_TRUSTED_DEVICE_NOT_FOUND);
        }
    }

    int revokeAll(Long memberId) {
        return repository.deleteAllByMemberId(memberId);
    }

    int deleteExpired(LocalDateTime now) {
        return repository.deleteExpired(now);
    }

    private static String shorten(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "알 수 없는 기기";
        }
        return userAgent.length() > DEVICE_NAME_MAX ? userAgent.substring(0, DEVICE_NAME_MAX) : userAgent;
    }
}
