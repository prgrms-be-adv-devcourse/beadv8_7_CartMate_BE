package com.cartmate.cartmate;

import com.cartmate.cartmate.global.global.RedisConfig;
import com.cartmate.cartmate.global.global.RedissonConfig;
import com.cartmate.cartmate.global.global.S3Config;
import com.cartmate.cartmate.global.global.WebConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {RedisConfig.class, RedissonConfig.class, S3Config.class, WebConfig.class}),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.cartmate\\.cartmate\\.global\\.lock\\..*")
})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
