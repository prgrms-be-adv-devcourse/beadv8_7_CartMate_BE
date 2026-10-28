CREATE TABLE `cart` (
	`id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `cart_item` (
	`id`	BIGINT	NOT NULL,
	`cart_id`	BIGINT	NOT NULL,
	`product_id`	BIGINT	NOT NULL,
	`inventory_id`	BIGINT	NOT NULL,
	`quantity`	INT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `shared_group_purchase` (
	`id`	BIGINT	NOT NULL,
	`title`	VARCHAR(50)	NOT NULL,
	`creator_member_id`	BIGINT	NOT NULL,
	`max_participants`	INT	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL,
	`invite_code`	VARCHAR(12)	NOT NULL,
	`edit_deadline_at`	DATETIME	NOT NULL,
	`frozen_at`	DATETIME	NULL,
	`closed_at`	DATETIME	NULL,
	`source_shared_group_purchase_id`	BIGINT	NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `shared_group_purchase_member` (
	`id`	BIGINT	NOT NULL,
	`shared_group_purchase_id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL
);

CREATE TABLE `shared_group_purchase_item` (
	`id`	BIGINT	NOT NULL,
	`shared_group_purchase_id`	BIGINT	NOT NULL,
	`product_id`	BIGINT	NOT NULL,
	`inventory_id`	BIGINT	NOT NULL,
	`quantity`	INT	NOT NULL,
	`updated_member_id`	BIGINT	NOT NULL,
	`version`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `individual_group_purchase` (
	`id`	BIGINT	NOT NULL,
	`title`	VARCHAR(50)	NOT NULL,
	`creator_member_id`	BIGINT	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL,
	`invite_code`	VARCHAR(12)	NOT NULL,
	`deadline_at`	DATETIME	NOT NULL,
	`closed_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `individual_group_purchase_product` (
	`id`	BIGINT	NOT NULL,
	`individual_group_purchase_id`	BIGINT	NOT NULL,
	`product_id`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL
);

CREATE TABLE `shared_group_order` (
	`id`	BIGINT	NOT NULL,
	`shared_group_purchase_id`	BIGINT	NOT NULL,
	`creator_member_id`	BIGINT	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL,
	`cancel_reason`	VARCHAR(30)	NULL,
	`total_amount`	BIGINT	NOT NULL,
	`payment_deadline_at`	DATETIME	NOT NULL,
	`stock_reserved`	TINYINT(1)	NOT NULL,
	`receiver_name`	VARCHAR(50)	NULL,
	`receiver_phone`	VARCHAR(20)	NULL,
	`zip_code`	VARCHAR(10)	NULL,
	`address`	VARCHAR(255)	NULL,
	`address_detail`	VARCHAR(255)	NULL,
	`confirmed_at`	DATETIME	NULL,
	`canceled_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `shared_group_order_participant` (
	`id`	BIGINT	NOT NULL,
	`shared_group_order_id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`share_amount`	BIGINT	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL,
	`payment_id`	BIGINT	NULL,
	`paid_at`	DATETIME	NULL,
	`version`	BIGINT	NOT NULL
);

CREATE TABLE `shared_group_order_item` (
	`id`	BIGINT	NOT NULL,
	`shared_group_order_id`	BIGINT	NOT NULL,
	`product_id`	BIGINT	NOT NULL,
	`inventory_id`	BIGINT	NOT NULL,
	`seller_id`	BIGINT	NULL,
	`product_name`	VARCHAR(100)	NOT NULL,
	`option_name`	VARCHAR(100)	NULL,
	`quantity`	INT	NOT NULL,
	`unit_price`	BIGINT	NOT NULL
);

CREATE TABLE `orders` (
	`id`	BIGINT	NOT NULL,
	`order_number`	VARCHAR(30)	NOT NULL,
	`order_type`	VARCHAR(20)	NOT NULL,
	`buyer_member_id`	BIGINT	NOT NULL,
	`individual_group_purchase_id`	BIGINT	NULL,
	`status`	VARCHAR(20)	NOT NULL,
	`cancel_reason`	VARCHAR(30)	NULL,
	`total_amount`	BIGINT	NOT NULL,
	`payment_deadline_at`	DATETIME	NOT NULL,
	`receiver_name`	VARCHAR(50)	NOT NULL,
	`receiver_phone`	VARCHAR(20)	NOT NULL,
	`zip_code`	VARCHAR(10)	NOT NULL,
	`address`	VARCHAR(255)	NOT NULL,
	`address_detail`	VARCHAR(255)	NULL,
	`payment_id`	BIGINT	NULL,
	`paid_at`	DATETIME	NULL,
	`canceled_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `order_item` (
	`id`	BIGINT	NOT NULL,
	`order_id`	BIGINT	NOT NULL,
	`product_id`	BIGINT	NOT NULL,
	`inventory_id`	BIGINT	NOT NULL,
	`seller_id`	BIGINT	NULL,
	`product_name`	VARCHAR(100)	NOT NULL,
	`option_name`	VARCHAR(100)	NULL,
	`unit_price`	BIGINT	NOT NULL,
	`quantity`	INT	NOT NULL
);

ALTER TABLE `cart` ADD CONSTRAINT `PK_CART` PRIMARY KEY (
	`id`
);

ALTER TABLE `cart_item` ADD CONSTRAINT `PK_CART_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `shared_group_purchase` ADD CONSTRAINT `PK_SHARED_GROUP_PURCHASE` PRIMARY KEY (
	`id`
);

ALTER TABLE `shared_group_purchase_member` ADD CONSTRAINT `PK_SHARED_GROUP_PURCHASE_MEMBER` PRIMARY KEY (
	`id`
);

ALTER TABLE `shared_group_purchase_item` ADD CONSTRAINT `PK_SHARED_GROUP_PURCHASE_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `individual_group_purchase` ADD CONSTRAINT `PK_INDIVIDUAL_GROUP_PURCHASE` PRIMARY KEY (
	`id`
);

ALTER TABLE `individual_group_purchase_product` ADD CONSTRAINT `PK_INDIVIDUAL_GROUP_PURCHASE_PRODUCT` PRIMARY KEY (
	`id`
);

ALTER TABLE `shared_group_order` ADD CONSTRAINT `PK_SHARED_GROUP_ORDER` PRIMARY KEY (
	`id`
);

ALTER TABLE `shared_group_order_participant` ADD CONSTRAINT `PK_SHARED_GROUP_ORDER_PARTICIPANT` PRIMARY KEY (
	`id`
);

ALTER TABLE `shared_group_order_item` ADD CONSTRAINT `PK_SHARED_GROUP_ORDER_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `orders` ADD CONSTRAINT `PK_ORDERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `order_item` ADD CONSTRAINT `PK_ORDER_ITEM` PRIMARY KEY (
	`id`
);

-- 엔티티에 이름이 없는 unique(@Column(unique = true))는 문서용 이름을 붙였다. 실제 DB 이름은 Hibernate가 자동 생성한다(UK...).
ALTER TABLE `cart` ADD CONSTRAINT `uk_cart_member_id` UNIQUE (
	`member_id`
);

ALTER TABLE `cart_item` ADD CONSTRAINT `uk_cart_item_cart_inventory` UNIQUE (
	`cart_id`,
	`inventory_id`
);

ALTER TABLE `shared_group_purchase` ADD CONSTRAINT `uk_shared_group_purchase_invite_code` UNIQUE (
	`invite_code`
);

ALTER TABLE `shared_group_purchase_member` ADD CONSTRAINT `uk_shared_group_purchase_member` UNIQUE (
	`shared_group_purchase_id`,
	`member_id`
);

ALTER TABLE `shared_group_purchase_item` ADD CONSTRAINT `uk_shared_group_purchase_item_inventory` UNIQUE (
	`shared_group_purchase_id`,
	`inventory_id`
);

ALTER TABLE `individual_group_purchase` ADD CONSTRAINT `uk_individual_group_purchase_invite_code` UNIQUE (
	`invite_code`
);

ALTER TABLE `individual_group_purchase_product` ADD CONSTRAINT `uk_individual_group_purchase_product` UNIQUE (
	`individual_group_purchase_id`,
	`product_id`
);

ALTER TABLE `shared_group_order` ADD CONSTRAINT `uk_shared_group_order_shared_group_purchase_id` UNIQUE (
	`shared_group_purchase_id`
);

ALTER TABLE `shared_group_order_participant` ADD CONSTRAINT `uk_shared_group_order_participant` UNIQUE (
	`shared_group_order_id`,
	`member_id`
);

ALTER TABLE `orders` ADD CONSTRAINT `uk_orders_order_number` UNIQUE (
	`order_number`
);

ALTER TABLE `cart_item` ADD CONSTRAINT `FK_cart_TO_cart_item_1` FOREIGN KEY (
	`cart_id`
)
REFERENCES `cart` (
	`id`
);

ALTER TABLE `shared_group_purchase_member` ADD CONSTRAINT `FK_shared_group_purchase_TO_shared_group_purchase_member_1` FOREIGN KEY (
	`shared_group_purchase_id`
)
REFERENCES `shared_group_purchase` (
	`id`
);

ALTER TABLE `shared_group_purchase_item` ADD CONSTRAINT `FK_shared_group_purchase_TO_shared_group_purchase_item_1` FOREIGN KEY (
	`shared_group_purchase_id`
)
REFERENCES `shared_group_purchase` (
	`id`
);

ALTER TABLE `individual_group_purchase_product` ADD CONSTRAINT `FK_individual_group_purchase_TO_individual_group_purchase_product_1` FOREIGN KEY (
	`individual_group_purchase_id`
)
REFERENCES `individual_group_purchase` (
	`id`
);

ALTER TABLE `shared_group_order_participant` ADD CONSTRAINT `FK_shared_group_order_TO_shared_group_order_participant_1` FOREIGN KEY (
	`shared_group_order_id`
)
REFERENCES `shared_group_order` (
	`id`
);

ALTER TABLE `shared_group_order_item` ADD CONSTRAINT `FK_shared_group_order_TO_shared_group_order_item_1` FOREIGN KEY (
	`shared_group_order_id`
)
REFERENCES `shared_group_order` (
	`id`
);

ALTER TABLE `order_item` ADD CONSTRAINT `FK_orders_TO_order_item_1` FOREIGN KEY (
	`order_id`
)
REFERENCES `orders` (
	`id`
);

CREATE INDEX `idx_orders_individual_group_purchase_status` ON `orders` (`individual_group_purchase_id`, `status`);

CREATE INDEX `idx_orders_buyer_created` ON `orders` (`buyer_member_id`, `created_at`);

CREATE INDEX `idx_order_item_order_id` ON `order_item` (`order_id`);
