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

ALTER TABLE `orders` ADD CONSTRAINT `PK_ORDERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `order_item` ADD CONSTRAINT `PK_ORDER_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `order_item` ADD CONSTRAINT `FK_orders_TO_order_item_1` FOREIGN KEY (
	`order_id`
)
REFERENCES `orders` (
	`id`
);
