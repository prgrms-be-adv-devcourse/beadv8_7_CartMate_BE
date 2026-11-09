package com.cartmate.cartmate.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Kafka는 다른 스레드에서 받으므로 결과를 바로 확인할 수 없다. 기대한 상태가 될 때까지 기다리는 도우미(order-service와 같은 방식).
 */
public final class KafkaTestTopics {

    public static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL = Duration.ofMillis(200);

    private KafkaTestTopics() {
    }

    // 토픽을 처음부터 읽어 조건에 맞는 메시지를 찾는다. 앱의 컨슈머 그룹과 상관없이 따로 읽는다
    public static ConsumerRecord<String, String> awaitRecord(String topic, Predicate<ConsumerRecord<String, String>> match) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ProductServiceKafkaContainer.bootstrapServers(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            boolean assigned = false;
            while (System.nanoTime() < deadline) {
                if (!assigned) {
                    List<PartitionInfo> partitions = consumer.partitionsFor(topic, POLL);
                    if (partitions == null || partitions.isEmpty()) {
                        continue;
                    }
                    List<TopicPartition> topicPartitions = partitions.stream()
                            .map(p -> new TopicPartition(topic, p.partition()))
                            .toList();
                    consumer.assign(topicPartitions);
                    consumer.seekToBeginning(topicPartitions);
                    assigned = true;
                }
                for (ConsumerRecord<String, String> record : consumer.poll(POLL)) {
                    if (match.test(record)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError(TIMEOUT.toSeconds() + "초 안에 토픽 " + topic + "에서 기대한 메시지를 받지 못했다");
    }

    public static void awaitUntil(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(TIMEOUT.toSeconds() + "초 안에 기대한 상태가 되지 않았다: " + description);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("상태를 기다리다 중단됨: " + description, e);
            }
        }
    }

    public static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
