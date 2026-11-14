package com.cartmate.cartmate.support;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
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

public final class KafkaTestTopics {

    public static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL = Duration.ofMillis(200);

    private KafkaTestTopics() {
    }

    public static ConsumerRecord<String, String> awaitRecord(String topic, Predicate<ConsumerRecord<String, String>> match) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CartMateKafkaContainer.bootstrapServers(),
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

    public static void awaitConsumed(String groupId, String topic, int partition, long offset) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, CartMateKafkaContainer.bootstrapServers()))) {
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(groupId)
                        .partitionsToOffsetAndMetadata()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                OffsetAndMetadata current = committed.get(topicPartition);
                if (current != null && current.offset() > offset) {
                    return;
                }
                TimeUnit.MILLISECONDS.sleep(POLL.toMillis());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("커밋 오프셋을 기다리다 중단됨", e);
        } catch (Exception e) {
            throw new AssertionError("커밋 오프셋을 읽지 못했다: group=" + groupId + ", " + topicPartition, e);
        }
        throw new AssertionError(TIMEOUT.toSeconds() + "초 안에 그룹 " + groupId + "이 " + topicPartition
                + "의 오프셋 " + offset + "을 처리하지 못했다");
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
