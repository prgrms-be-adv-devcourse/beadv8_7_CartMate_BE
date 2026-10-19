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
	`paid_at`	DATETIME	NULL,
	`version`	BIGINT	NOT NULL
);

CREATE TABLE `shared_group_order_item` (
	`id`	BIGINT	NOT NULL,
	`shared_group_order_id`	BIGINT	NOT NULL,
	`product_id`	BIGINT	NOT NULL,
	`inventory_id`	BIGINT	NOT NULL,
	`quantity`	INT	NOT NULL,
	`unit_price`	BIGINT	NOT NULL
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
