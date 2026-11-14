package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAccountLink;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseAccountLinkRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberIdentityRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository.SharedContextCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
class AbuseAccountRingSupport {

    static final int MIN_SHARED_CONTEXTS = 2;
    static final int MAX_MEMBERS_PER_IDENTITY = 10;
    static final int MAX_RING_SIZE = 50;
    static final int MAX_LINK_CANDIDATES = 100;

    private final AbuseSignalRepository abuseSignalRepository;
    private final AbuseMemberIdentityRepository abuseMemberIdentityRepository;
    private final AbuseAccountLinkRepository abuseAccountLinkRepository;
    private final PlatformTransactionManager transactionManager;

    void confirmLinksOfPending(Long memberId, LocalDateTime now) {
        confirmLinks(memberId,
                abuseSignalRepository.findContextKeysByMemberIdAndStatus(memberId, AbuseSignalStatus.RECEIVED), now);
    }

    private void confirmLinks(Long memberId, Collection<String> contextKeys, LocalDateTime now) {
        if (contextKeys.isEmpty()) {
            return;
        }
        List<SharedContextCandidate> candidates = abuseSignalRepository.findLinkCandidates(
                memberId, contextKeys, MIN_SHARED_CONTEXTS, MAX_LINK_CANDIDATES);
        for (SharedContextCandidate candidate : candidates) {
            Long other = candidate.getMemberId();
            if (abuseAccountLinkRepository.existsByMemberLowIdAndMemberHighId(
                    Math.min(memberId, other), Math.max(memberId, other))) {
                continue;
            }
            List<String> sharedIdentities = abuseMemberIdentityRepository.findSharedIdentityTypes(
                    memberId, other, MAX_MEMBERS_PER_IDENTITY);
            if (sharedIdentities.isEmpty()) {
                continue;
            }
            linkInOwnTransaction(memberId, other, sharedIdentities.getFirst(),
                    Math.toIntExact(candidate.getSharedContexts()), now);
            log.info("어뷰징 계정 연결 확정: memberId={}, otherMemberId={}, identityType={}, sharedContexts={}",
                    memberId, other, sharedIdentities.getFirst(), candidate.getSharedContexts());
        }
    }

    private void linkInOwnTransaction(Long memberId, Long other, String identityType, int sharedContexts,
                                      LocalDateTime now) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status ->
                abuseAccountLinkRepository.insertIfAbsent(memberId, other, identityType, sharedContexts, now));
    }

    Set<Long> ringMembersOf(Long memberId) {
        Set<Long> visited = new LinkedHashSet<>();
        visited.add(memberId);
        Deque<Long> queue = new ArrayDeque<>(List.of(memberId));
        while (!queue.isEmpty() && visited.size() < MAX_RING_SIZE) {
            Long current = queue.poll();
            for (AbuseAccountLink link : abuseAccountLinkRepository.findByMember(current)) {
                Long next = link.otherThan(current);
                if (visited.size() < MAX_RING_SIZE && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (visited.size() >= MAX_RING_SIZE) {
            log.warn("어뷰징 계정 무리가 상한에 닿아 일부만 계산: memberId={}, maxRingSize={}", memberId, MAX_RING_SIZE);
        }
        visited.remove(memberId);
        return visited;
    }
}
