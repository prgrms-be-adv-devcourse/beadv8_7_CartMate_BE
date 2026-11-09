package com.cartmate.cartmate.boundedContext.member.out.account;

import com.cartmate.cartmate.boundedContext.member.domain.SellerAccountProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OpenBankingProperties.class, SellerAccountProperties.class})
public class AccountVerifierConfig {
}
