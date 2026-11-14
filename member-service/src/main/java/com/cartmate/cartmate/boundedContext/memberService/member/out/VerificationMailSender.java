package com.cartmate.cartmate.boundedContext.memberService.member.out;

// 인증 메일 보내기. cartmate.mail.enabled=true면 SMTP(로컬 Mailpit, 운영 SES), 아니면 로그 출력
public interface VerificationMailSender {

    void send(String to, String subject, String body);
}
