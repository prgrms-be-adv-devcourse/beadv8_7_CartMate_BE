package com.cartmate.cartmate.boundedContext.etcService.trust.domain;

public record TrustEvaluation(Integer score, TrustLevel level) {  // NEW 등급이면 score는 null
}
