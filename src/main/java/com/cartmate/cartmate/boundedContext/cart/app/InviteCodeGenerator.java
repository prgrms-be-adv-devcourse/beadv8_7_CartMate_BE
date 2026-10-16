package com.cartmate.cartmate.boundedContext.cart.app;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.function.Predicate;

@Component
public class InviteCodeGenerator {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static final int CODE_LENGTH = 12;


    private static final int MAX_ATTEMPTS = 5;


    private final SecureRandom random = new SecureRandom();


    public String generateUnique(Predicate<String> isTaken) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = generate();
            if (!isTaken.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드를 생성하지 못했습니다.");
    }


    private String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }
}
