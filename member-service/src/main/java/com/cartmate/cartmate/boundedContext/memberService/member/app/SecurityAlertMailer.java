package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationMailSender;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 보안 알림 메일 (OTP 등록, 백업 코드 사용). 트랜잭션 안에서 부르면 <b>커밋된 뒤에만</b>, 다른 스레드에서 보낸다.
 *
 * <ul>
 *   <li>롤백되면 보내지 않는다 → 실제로 일어나지 않은 일을 알리지 않는다</li>
 *   <li>커밋 뒤 콜백도 DB 연결을 돌려주기 전에 실행되므로, 그 안에서 SMTP를 기다리지 않고 다른 스레드에 넘긴다</li>
 *   <li>보내지 못해도 등록·로그인을 되돌리지 않는다 (로그로 남김)</li>
 * </ul>
 */
@Slf4j
@Component
class SecurityAlertMailer {

    private final VerificationMailSender mailSender;
    private final Executor executor;

    // 맥락 전파 래퍼: 메일 스레드에서도 요청의 traceId가 로그에 이어진다
    @Autowired
    SecurityAlertMailer(VerificationMailSender mailSender) {
        this(mailSender, ContextExecutorService.wrap(Executors.newVirtualThreadPerTaskExecutor(),
                ContextSnapshotFactory.builder().build()));
    }

    // 테스트에서 바로 실행하는 executor를 넣기 위한 생성자
    SecurityAlertMailer(VerificationMailSender mailSender, Executor executor) {
        this.mailSender = mailSender;
        this.executor = executor;
    }

    void sendAfterCommit(Long memberId, String to, String subject, String body) {
        Runnable send = () -> {
            try {
                mailSender.send(to, subject, body);
            } catch (MailException e) {
                log.warn("[보안] 알림 메일 실패: 회원 #{} {}", memberId, subject, e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executor.execute(send);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(send);
            }
        });
    }
}
