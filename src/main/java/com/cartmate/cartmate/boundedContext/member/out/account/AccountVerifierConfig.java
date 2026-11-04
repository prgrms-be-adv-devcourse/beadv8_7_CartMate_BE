package com.cartmate.cartmate.boundedContext.member.out.account;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenBankingProperties.class)
public class AccountVerifierConfig {
}
