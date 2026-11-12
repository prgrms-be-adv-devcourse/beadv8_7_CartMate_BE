package com.cartmate.cartmate.support.context;

import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.support.CartMateJpaTest;
import com.cartmate.cartmate.support.CartMateKafkaIntegrationTest;

final class SharedContextBaselines {

    private SharedContextBaselines() {
    }

    @CartMateIntegrationTest
    static final class Integration {
    }

    @CartMateJpaTest
    static final class Jpa {
    }

    @CartMateKafkaIntegrationTest
    static final class Kafka {
    }
}
