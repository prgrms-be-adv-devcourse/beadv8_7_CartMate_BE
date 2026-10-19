package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

@Component
class OrderNumberGenerator {

    private static final String CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final int RANDOM_LENGTH = 8;

    private static final int MAX_ATTEMPTS = 5;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom random = new SecureRandom();

    String generateUnique(LocalDate orderDate, Predicate<String> isTaken) {
        String prefix = orderDate.format(DATE_FORMAT) + "-";
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String orderNumber = prefix + randomPart();
            if (!isTaken.test(orderNumber)) {
                return orderNumber;
            }
        }
        throw new ServiceException(OrderErrorCode.ORDER_NUMBER_GENERATION_FAILED);
    }

    private String randomPart() {
        StringBuilder part = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            part.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return part.toString();
    }
}
