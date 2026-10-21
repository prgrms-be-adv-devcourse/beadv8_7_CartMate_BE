CREATE TABLE `member` (
	`id`	BIGINT	NOT NULL,
	`email`	VARCHAR(100)	NOT NULL,
	`password`	VARCHAR(255)	NOT NULL,
	`nickname`	VARCHAR(30)	NOT NULL,
	`phone`	VARCHAR(20)	NOT NULL,
	`birth_date`	DATE	NOT NULL,
	`role`	VARCHAR(20)	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL,
	`deleted_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `member_address` (
	`id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`receiver_name`	VARCHAR(50)	NOT NULL,
	`receiver_phone`	VARCHAR(20)	NOT NULL,
	`zip_code`	VARCHAR(10)	NOT NULL,
	`basic_address`	VARCHAR(255)	NOT NULL,
	`detail_address`	VARCHAR(255)	NULL,
	`is_default`	TINYINT(1)	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `member_suspension` (
	`id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`reason_type`	VARCHAR(20)	NOT NULL,
	`reason_detail`	VARCHAR(300)	NOT NULL,
	`suspended_at`	DATETIME	NOT NULL,
	`suspended_until`	DATETIME	NULL,
	`released_at`	DATETIME	NULL,
	`release_type`	VARCHAR(20)	NULL,
	`admin_id`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `seller` (
	`id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`seller_type`	VARCHAR(20)	NOT NULL,
	`business_name`	VARCHAR(100)	NULL,
	`representative_name`	VARCHAR(50)	NULL,
	`business_number`	VARCHAR(20)	NULL,
	`phone_number`	VARCHAR(20)	NOT NULL,
	`bank_name`	VARCHAR(50)	NULL,
	`account_holder`	VARCHAR(50)	NULL,
	`account_number`	VARCHAR(50)	NULL,
	`status`	VARCHAR(20)	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `wallet` (
	`id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`balance`	BIGINT	NOT NULL,
	`version`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `wallet_charge_orders` (
	`id`	BIGINT	NOT NULL,
	`wallet_id`	BIGINT	NOT NULL,
	`order_number`	VARCHAR(255)	NOT NULL,
	`status`	VARCHAR(255)	NOT NULL,
	`amount`	BIGINT	NOT NULL,
	`payment_key`	VARCHAR(255)	NULL,
	`idempotency_key`	VARCHAR(255)	NOT NULL,
	`version`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `wallet_withdraw_orders` (
	`id`	BIGINT	NOT NULL,
	`wallet_id`	BIGINT	NOT NULL,
	`idempotency_key`	VARCHAR(255)	NOT NULL,
	`amount`	BIGINT	NOT NULL,
	`status`	VARCHAR(255)	NOT NULL,
	`version`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `wallet_transaction` (
	`id`	BIGINT	NOT NULL,
	`wallet_id`	BIGINT	NOT NULL,
	`type`	VARCHAR(255)	NOT NULL,
	`order_id`	BIGINT	NULL,
	`reference_id`	BIGINT	NULL,
	`reference_type`	VARCHAR(255)	NULL,
	`idempotency_key`	VARCHAR(255)	NULL,
	`amount`	BIGINT	NOT NULL,
	`balance_after`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `holding` (
	`id`	BIGINT	NOT NULL,
	`wallet_id`	BIGINT	NOT NULL,
	`order_id`	BIGINT	NOT NULL,
	`payment_id`	BIGINT	NOT NULL,
	`settlement_id`	BIGINT	NULL,
	`amount`	BIGINT	NOT NULL,
	`refunded_amount`	BIGINT	NOT NULL,
	`status`	VARCHAR(255)	NOT NULL,
	`version`	BIGINT	NOT NULL,
	`settle_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `delivery` (
	`id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`seller_id`	BIGINT	NOT NULL,
	`order_id`	BIGINT	NOT NULL,
	`purchase_type`	VARCHAR(255)	NOT NULL,
	`carrier`	VARCHAR(255)	NULL,
	`tracking_number`	VARCHAR(255)	NULL,
	`receiver_name`	VARCHAR(255)	NOT NULL,
	`receiver_phone`	VARCHAR(255)	NOT NULL,
	`zip_code`	VARCHAR(255)	NOT NULL,
	`address`	VARCHAR(255)	NOT NULL,
	`address_detail`	VARCHAR(255)	NOT NULL,
	`status`	VARCHAR(255)	NOT NULL,
	`idempotency_key`	VARCHAR(255)	NOT NULL,
	`request_hash`	VARCHAR(64)	NOT NULL,
	`shipped_at`	DATETIME	NULL,
	`delivered_at`	DATETIME	NULL,
	`confirmed_at`	DATETIME	NULL,
	`canceled_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `delivery_item` (
	`id`	BIGINT	NOT NULL,
	`delivery_id`	BIGINT	NOT NULL,
	`order_item_id`	BIGINT	NOT NULL,
	`product_name`	VARCHAR(255)	NULL,
	`unit_price`	BIGINT	NOT NULL,
	`quantity`	BIGINT	NOT NULL
);

CREATE TABLE `delivery_item_option` (
	`id`	BIGINT	NOT NULL,
	`delivery_item_id`	BIGINT	NOT NULL,
	`option_name`	VARCHAR(255)	NOT NULL,
	`option_value`	VARCHAR(255)	NOT NULL,
	`sort_order`	INT	NOT NULL
);

CREATE TABLE `delivery_claim` (
	`id`	BIGINT	NOT NULL,
	`delivery_id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`claim_type`	VARCHAR(255)	NOT NULL,
	`reason`	VARCHAR(255)	NOT NULL,
	`detail`	VARCHAR(1000)	NULL,
	`status`	VARCHAR(255)	NOT NULL,
	`processed_at`	DATETIME	NULL,
	`completed_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `delivery_claim_item` (
	`id`	BIGINT	NOT NULL,
	`claim_id`	BIGINT	NOT NULL,
	`delivery_item_id`	BIGINT	NOT NULL,
	`quantity`	BIGINT	NOT NULL
);

CREATE TABLE `PRODUCT_PRODUCT` (
	`id`	BIGINT	NOT NULL,
	`seller_id`	BIGINT	NOT NULL,
	`sales_post_id`	BIGINT	NOT NULL,
	`category_id`	BIGINT	NOT NULL,
	`name`	VARCHAR(255)	NOT NULL,
	`description`	VARCHAR(255)	NULL,
	`base_price`	BIGINT	NOT NULL,
	`status`	TINYINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `PRODUCT_PRODUCT_IMAGE` (
	`id`	BIGINT	NOT NULL,
	`seller_id`	BIGINT	NOT NULL,
	`product_id`	BIGINT	NOT NULL,
	`s3_key`	VARCHAR(255)	NOT NULL,
	`image_seq`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `POST_SELLER` (
	`id`	BIGINT	NOT NULL,
	`member_id`	BIGINT	NOT NULL,
	`seller_type`	VARCHAR(255)	NOT NULL,
	`business_name`	VARCHAR(255)	NOT NULL,
	`business_number`	VARCHAR(255)	NOT NULL,
	`phone_number`	VARCHAR(255)	NOT NULL,
	`bank_name`	VARCHAR(255)	NOT NULL,
	`account_holder`	VARCHAR(255)	NOT NULL,
	`account_number`	VARCHAR(255)	NOT NULL,
	`status`	VARCHAR(255)	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `POST_SALES_POST` (
	`id`	BIGINT	NOT NULL,
	`seller_id`	BIGINT	NOT NULL,
	`title`	VARCHAR(255)	NOT NULL,
	`content`	VARCHAR(255)	NULL,
	`status`	VARCHAR(255)	NULL,
	`view_count`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `POST_PRODUCT` (
	`id`	BIGINT	NOT NULL,
	`seller_id`	BIGINT	NOT NULL,
	`sales_post_id`	BIGINT	NOT NULL,
	`category_id`	BIGINT	NOT NULL,
	`name`	VARCHAR(255)	NOT NULL,
	`description`	VARCHAR(255)	NULL,
	`base_price`	BIGINT	NOT NULL,
	`status`	TINYINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `POST_PRODUCT_IMAGE` (
	`id`	BIGINT	NOT NULL,
	`seller_id`	BIGINT	NOT NULL,
	`product_id`	BIGINT	NOT NULL,
	`s3_key`	VARCHAR(255)	NOT NULL,
	`image_seq`	BIGINT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL
);

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

ALTER TABLE `member` ADD CONSTRAINT `PK_MEMBER` PRIMARY KEY (
	`id`
);

ALTER TABLE `member_address` ADD CONSTRAINT `PK_MEMBER_ADDRESS` PRIMARY KEY (
	`id`
);

ALTER TABLE `member_suspension` ADD CONSTRAINT `PK_MEMBER_SUSPENSION` PRIMARY KEY (
	`id`
);

ALTER TABLE `seller` ADD CONSTRAINT `PK_SELLER` PRIMARY KEY (
	`id`
);

ALTER TABLE `wallet` ADD CONSTRAINT `PK_WALLET` PRIMARY KEY (
	`id`
);

ALTER TABLE `wallet_charge_orders` ADD CONSTRAINT `PK_WALLET_CHARGE_ORDERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `wallet_withdraw_orders` ADD CONSTRAINT `PK_WALLET_WITHDRAW_ORDERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `wallet_transaction` ADD CONSTRAINT `PK_WALLET_TRANSACTION` PRIMARY KEY (
	`id`
);

ALTER TABLE `holding` ADD CONSTRAINT `PK_HOLDING` PRIMARY KEY (
	`id`
);

ALTER TABLE `delivery` ADD CONSTRAINT `PK_DELIVERY` PRIMARY KEY (
	`id`
);

ALTER TABLE `delivery_item` ADD CONSTRAINT `PK_DELIVERY_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `delivery_item_option` ADD CONSTRAINT `PK_DELIVERY_ITEM_OPTION` PRIMARY KEY (
	`id`
);

ALTER TABLE `delivery_claim` ADD CONSTRAINT `PK_DELIVERY_CLAIM` PRIMARY KEY (
	`id`
);

ALTER TABLE `delivery_claim_item` ADD CONSTRAINT `PK_DELIVERY_CLAIM_ITEM` PRIMARY KEY (
	`id`
);

ALTER TABLE `PRODUCT_PRODUCT` ADD CONSTRAINT `PK_PRODUCT_PRODUCT` PRIMARY KEY (
	`id`
);

ALTER TABLE `PRODUCT_PRODUCT_IMAGE` ADD CONSTRAINT `PK_PRODUCT_PRODUCT_IMAGE` PRIMARY KEY (
	`id`
);

ALTER TABLE `POST_SELLER` ADD CONSTRAINT `PK_POST_SELLER` PRIMARY KEY (
	`id`
);

ALTER TABLE `POST_SALES_POST` ADD CONSTRAINT `PK_POST_SALES_POST` PRIMARY KEY (
	`id`
);

ALTER TABLE `POST_PRODUCT` ADD CONSTRAINT `PK_POST_PRODUCT` PRIMARY KEY (
	`id`
);

ALTER TABLE `POST_PRODUCT_IMAGE` ADD CONSTRAINT `PK_POST_PRODUCT_IMAGE` PRIMARY KEY (
	`id`
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

ALTER TABLE `member_address` ADD CONSTRAINT `FK_member_TO_member_address_1` FOREIGN KEY (
	`member_id`
)
REFERENCES `member` (
	`id`
);

ALTER TABLE `member_suspension` ADD CONSTRAINT `FK_member_TO_member_suspension_1` FOREIGN KEY (
	`member_id`
)
REFERENCES `member` (
	`id`
);

ALTER TABLE `seller` ADD CONSTRAINT `FK_member_TO_seller_1` FOREIGN KEY (
	`member_id`
)
REFERENCES `member` (
	`id`
);

ALTER TABLE `wallet_charge_orders` ADD CONSTRAINT `FK_wallet_TO_wallet_charge_orders_1` FOREIGN KEY (
	`wallet_id`
)
REFERENCES `wallet` (
	`id`
);

ALTER TABLE `wallet_withdraw_orders` ADD CONSTRAINT `FK_wallet_TO_wallet_withdraw_orders_1` FOREIGN KEY (
	`wallet_id`
)
REFERENCES `wallet` (
	`id`
);

ALTER TABLE `wallet_transaction` ADD CONSTRAINT `FK_wallet_TO_wallet_transaction_1` FOREIGN KEY (
	`wallet_id`
)
REFERENCES `wallet` (
	`id`
);

ALTER TABLE `holding` ADD CONSTRAINT `FK_wallet_TO_holding_1` FOREIGN KEY (
	`wallet_id`
)
REFERENCES `wallet` (
	`id`
);

ALTER TABLE `delivery_item` ADD CONSTRAINT `FK_delivery_TO_delivery_item_1` FOREIGN KEY (
	`delivery_id`
)
REFERENCES `delivery` (
	`id`
);

ALTER TABLE `delivery_item_option` ADD CONSTRAINT `FK_delivery_item_TO_delivery_item_option_1` FOREIGN KEY (
	`delivery_item_id`
)
REFERENCES `delivery_item` (
	`id`
);

ALTER TABLE `delivery_claim` ADD CONSTRAINT `FK_delivery_TO_delivery_claim_1` FOREIGN KEY (
	`delivery_id`
)
REFERENCES `delivery` (
	`id`
);

ALTER TABLE `delivery_claim_item` ADD CONSTRAINT `FK_delivery_claim_TO_delivery_claim_item_1` FOREIGN KEY (
	`claim_id`
)
REFERENCES `delivery_claim` (
	`id`
);

ALTER TABLE `delivery_claim_item` ADD CONSTRAINT `FK_delivery_item_TO_delivery_claim_item_1` FOREIGN KEY (
	`delivery_item_id`
)
REFERENCES `delivery_item` (
	`id`
);

ALTER TABLE `PRODUCT_PRODUCT_IMAGE` ADD CONSTRAINT `FK_PRODUCT_PRODUCT_TO_PRODUCT_PRODUCT_IMAGE_1` FOREIGN KEY (
	`product_id`
)
REFERENCES `PRODUCT_PRODUCT` (
	`id`
);

ALTER TABLE `POST_PRODUCT` ADD CONSTRAINT `FK_POST_SALES_POST_TO_POST_PRODUCT_1` FOREIGN KEY (
	`sales_post_id`
)
REFERENCES `POST_SALES_POST` (
	`id`
);

ALTER TABLE `POST_PRODUCT_IMAGE` ADD CONSTRAINT `FK_POST_PRODUCT_TO_POST_PRODUCT_IMAGE_1` FOREIGN KEY (
	`product_id`
)
REFERENCES `POST_PRODUCT` (
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

ALTER TABLE `order_item` ADD CONSTRAINT `FK_orders_TO_order_item_1` FOREIGN KEY (
	`order_id`
)
REFERENCES `orders` (
	`id`
);
