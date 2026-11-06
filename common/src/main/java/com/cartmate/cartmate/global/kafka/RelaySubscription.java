package com.cartmate.cartmate.global.kafka;

import java.util.Set;

public record RelaySubscription(String groupId, Set<String> modules) {
}
