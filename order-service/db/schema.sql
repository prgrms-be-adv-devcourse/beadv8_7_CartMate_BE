-- 주문 서버(cartmate_order) 운영 스키마. 운영은 ddl-auto: validate라 첫 배포 전에 빈 DB에서 1회 실행한다
-- 엔티티에서 만든 것이다(MySQL 8.4, Hibernate update → mysqldump --no-data). 엔티티가 바뀌면 다시 뽑는다

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `abuse_account_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confirmed_at` datetime(6) NOT NULL,
  `identity_type` varchar(20) NOT NULL,
  `member_high_id` bigint NOT NULL,
  `member_low_id` bigint NOT NULL,
  `shared_context_count` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_abuse_account_link_pair` (`member_low_id`,`member_high_id`),
  KEY `idx_abuse_account_link_high` (`member_high_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `abuse_member_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `identity_hash` varchar(64) NOT NULL,
  `identity_type` varchar(20) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_abuse_member_identity` (`member_id`,`identity_type`,`identity_hash`),
  KEY `idx_abuse_member_identity_hash` (`identity_type`,`identity_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `abuse_member_state` (
  `member_id` bigint NOT NULL,
  `active_restriction_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_level` varchar(20) NOT NULL,
  `last_judged_at` datetime(6) DEFAULT NULL,
  `restricted_until` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `abuse_restriction_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `ends_at` datetime(6) NOT NULL,
  `extended_count` int NOT NULL,
  `last_signal_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_abuse_restriction_history_member_started` (`member_id`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `abuse_signal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_count` int NOT NULL,
  `context_key` varchar(64) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `judged_at` datetime(6) DEFAULT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `level` varchar(20) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `received_at` datetime(6) NOT NULL,
  `rule_results` json DEFAULT NULL,
  `signal_type` varchar(40) NOT NULL,
  `source_key` varchar(64) NOT NULL,
  `status` varchar(20) NOT NULL,
  `total_score` int DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_abuse_signal_source` (`member_id`,`signal_type`,`source_key`),
  KEY `idx_abuse_signal_member_type_occurred` (`member_id`,`signal_type`,`occurred_at`),
  KEY `idx_abuse_signal_status_received` (`status`,`received_at`),
  KEY `idx_abuse_signal_context_member` (`context_key`,`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7dds3r67nkhxm9sbs9r5obd46` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `cart_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `inventory_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cart_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_item_cart_inventory` (`cart_id`,`inventory_id`),
  KEY `idx_cart_item_updated_product` (`updated_at`,`product_id`,`cart_id`),
  CONSTRAINT `FK1uobyhgl1wvgt1jpccia8xxs3` FOREIGN KEY (`cart_id`) REFERENCES `cart` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `inbox_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempts` int NOT NULL,
  `envelope` longtext NOT NULL,
  `event_id` varchar(36) NOT NULL,
  `event_type` varchar(100) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `next_attempt_at` datetime(6) NOT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `received_at` datetime(6) NOT NULL,
  `status` varchar(20) NOT NULL,
  `topic` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbox_event_topic_event` (`topic`,`event_id`),
  KEY `idx_inbox_event_status_id` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `individual_group_purchase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `creator_member_id` bigint NOT NULL,
  `deadline_at` datetime(6) NOT NULL,
  `demand_product_key` bigint DEFAULT NULL,
  `invite_code` varchar(12) NOT NULL,
  `origin` varchar(20) NOT NULL DEFAULT 'MEMBER',
  `revision` bigint NOT NULL DEFAULT '0',
  `source_product_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `title` varchar(50) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKa3lujel30ha1x3ua5u9mllql9` (`invite_code`),
  UNIQUE KEY `uk_individual_group_purchase_demand_product` (`demand_product_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `individual_group_purchase_order` (
  `order_id` bigint NOT NULL,
  `buyer_member_id` bigint DEFAULT NULL,
  `individual_group_purchase_id` bigint DEFAULT NULL,
  `ordered_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`order_id`),
  KEY `idx_igp_order_purchase_status` (`individual_group_purchase_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `individual_group_purchase_order_line` (
  `order_item_id` bigint NOT NULL,
  `canceled` bit(1) NOT NULL,
  `inventory_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `seller_id` bigint DEFAULT NULL,
  `unit_price` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  PRIMARY KEY (`order_item_id`),
  KEY `FKke8oqm4lw7rxrctn2xylknohc` (`order_id`),
  CONSTRAINT `FKke8oqm4lw7rxrctn2xylknohc` FOREIGN KEY (`order_id`) REFERENCES `individual_group_purchase_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `individual_group_purchase_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `product_id` bigint NOT NULL,
  `individual_group_purchase_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_individual_group_purchase_product` (`individual_group_purchase_id`,`product_id`),
  CONSTRAINT `FKdxx0oiae9x7p137khaipfm4l6` FOREIGN KEY (`individual_group_purchase_id`) REFERENCES `individual_group_purchase` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `individual_group_purchase_replica` (
  `individual_group_purchase_id` bigint NOT NULL,
  `closed` bit(1) NOT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `deadline_at` datetime(6) NOT NULL,
  `revision` bigint NOT NULL,
  PRIMARY KEY (`individual_group_purchase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `individual_group_purchase_replica_product` (
  `individual_group_purchase_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`individual_group_purchase_id`,`product_id`),
  CONSTRAINT `FKad8tlyxayqeou63orievymun2` FOREIGN KEY (`individual_group_purchase_id`) REFERENCES `individual_group_purchase_replica` (`individual_group_purchase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `canceled_at` datetime(6) DEFAULT NULL,
  `inventory_id` bigint NOT NULL,
  `option_name` varchar(100) DEFAULT NULL,
  `pending_buyer_member_id` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(100) NOT NULL,
  `quantity` int NOT NULL,
  `seller_id` bigint DEFAULT NULL,
  `unit_price` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item_pending_buyer_inventory` (`pending_buyer_member_id`,`inventory_id`),
  KEY `idx_order_item_order_id` (`order_id`),
  CONSTRAINT `FKt4dc2r9nbvbujrljv3e23iibt` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `order_purchase_confirmation` (
  `order_id` bigint NOT NULL,
  `confirmed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `buyer_member_id` bigint NOT NULL,
  `cancel_last_sent_at` datetime(6) DEFAULT NULL,
  `cancel_reason` varchar(30) DEFAULT NULL,
  `cancel_reject_code` varchar(100) DEFAULT NULL,
  `cancel_request_seq` int NOT NULL DEFAULT '0',
  `cancel_requested_at` datetime(6) DEFAULT NULL,
  `canceled_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `address` varchar(255) NOT NULL,
  `address_detail` varchar(255) DEFAULT NULL,
  `receiver_name` varchar(50) NOT NULL,
  `receiver_phone` varchar(20) NOT NULL,
  `zip_code` varchar(10) NOT NULL,
  `discount_amount` bigint NOT NULL,
  `discount_burden` varchar(20) DEFAULT NULL,
  `discount_seller_id` bigint DEFAULT NULL,
  `individual_group_purchase_id` bigint DEFAULT NULL,
  `item_refunded_amount` bigint NOT NULL,
  `member_coupon_id` bigint DEFAULT NULL,
  `order_number` varchar(30) NOT NULL,
  `order_type` varchar(20) NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `partially_canceled` bit(1) NOT NULL,
  `payment_deadline_at` datetime(6) NOT NULL,
  `payment_id` bigint DEFAULT NULL,
  `pending_individual_group_purchase_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `total_amount` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_number` (`order_number`),
  UNIQUE KEY `uk_orders_pending_buyer_individual_group` (`buyer_member_id`,`pending_individual_group_purchase_id`),
  KEY `idx_orders_individual_group_purchase_status` (`individual_group_purchase_id`,`status`),
  KEY `idx_orders_buyer_created` (`buyer_member_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `outbox_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aggregate_id` varchar(100) NOT NULL,
  `aggregate_type` varchar(100) NOT NULL,
  `attempts` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `event_id` varchar(36) NOT NULL,
  `event_type` varchar(100) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `next_attempt_at` datetime(6) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `payload` longtext NOT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `topic` varchar(100) NOT NULL,
  `version` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKc7pmgf9c7t5nt657in8sec87v` (`event_id`),
  KEY `idx_outbox_event_status_id` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `processed_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `consumer` varchar(100) NOT NULL,
  `event_id` varchar(36) NOT NULL,
  `processed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_processed_event_consumer_event` (`consumer`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `product_embedding` (
  `product_id` bigint NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `dimension` int NOT NULL,
  `model` varchar(50) NOT NULL,
  `text_version` varchar(20) NOT NULL,
  `vector` mediumblob NOT NULL,
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shared_group_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cancel_reason` varchar(30) DEFAULT NULL,
  `canceled_at` datetime(6) DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `creator_member_id` bigint NOT NULL,
  `payment_deadline_at` datetime(6) NOT NULL,
  `shared_group_purchase_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `stock_reserved` bit(1) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `address_detail` varchar(255) DEFAULT NULL,
  `receiver_name` varchar(50) DEFAULT NULL,
  `receiver_phone` varchar(20) DEFAULT NULL,
  `zip_code` varchar(10) DEFAULT NULL,
  `total_amount` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKnklhd003gjy4jwj5unuhcpmde` (`shared_group_purchase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shared_group_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `canceled_at` datetime(6) DEFAULT NULL,
  `inventory_id` bigint NOT NULL,
  `option_name` varchar(100) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(100) NOT NULL,
  `quantity` int NOT NULL,
  `seller_id` bigint DEFAULT NULL,
  `unit_price` bigint NOT NULL,
  `shared_group_order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKl8bt3t19unuptlj5aj614ycwr` (`shared_group_order_id`),
  CONSTRAINT `FKl8bt3t19unuptlj5aj614ycwr` FOREIGN KEY (`shared_group_order_id`) REFERENCES `shared_group_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shared_group_order_participant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `payment_id` bigint DEFAULT NULL,
  `share_amount` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `version` bigint NOT NULL,
  `shared_group_order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shared_group_order_participant` (`shared_group_order_id`,`member_id`),
  CONSTRAINT `FKs9jhm58704bw7nnk4idxetl2b` FOREIGN KEY (`shared_group_order_id`) REFERENCES `shared_group_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shared_group_purchase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `creator_member_id` bigint NOT NULL,
  `edit_deadline_at` datetime(6) NOT NULL,
  `frozen_at` datetime(6) DEFAULT NULL,
  `invite_code` varchar(12) NOT NULL,
  `max_participants` int NOT NULL,
  `source_shared_group_purchase_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `title` varchar(50) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKs29v3mq7ur6191c2bmx8tr8rf` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shared_group_purchase_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `inventory_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_member_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  `shared_group_purchase_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shared_group_purchase_item_inventory` (`shared_group_purchase_id`,`inventory_id`),
  CONSTRAINT `FKip9mhlh4bptcbxhajhbxk6moe` FOREIGN KEY (`shared_group_purchase_id`) REFERENCES `shared_group_purchase` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shared_group_purchase_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `member_id` bigint NOT NULL,
  `shared_group_purchase_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shared_group_purchase_member` (`shared_group_purchase_id`,`member_id`),
  CONSTRAINT `FKbm2ijmkxt83h226f0m1ekauv8` FOREIGN KEY (`shared_group_purchase_id`) REFERENCES `shared_group_purchase` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shedlock` (
  `name` varchar(64) NOT NULL,
  `lock_until` datetime(6) NOT NULL,
  `locked_at` datetime(6) NOT NULL,
  `locked_by` varchar(255) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
