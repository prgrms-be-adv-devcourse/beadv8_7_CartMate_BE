package com.cartmate.cartmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// AI 서버(빈 골격). 기능 코드는 담당자가 boundedContext/aiService 아래에 추가한다.
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
