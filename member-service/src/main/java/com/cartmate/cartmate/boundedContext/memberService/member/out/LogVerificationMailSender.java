package com.cartmate.cartmate.boundedContext.memberService.member.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// cartmate.mail.enabled=false(기본): 보내지 않고 로그로 남긴다. 운영에선 쓰지 않는다 (prod는 enabled=true)
@Slf4j
@Component
@ConditionalOnProperty(name = "cartmate.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogVerificationMailSender implements VerificationMailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[인증 메일 — 발송 안 함] to={} subject={}\n{}", to, subject, body);
    }
}
