-- 옵션 종류(product_product_option_type) · 상품옵션(product_product_option_value) 초기 데이터
-- 서버가 시작될 때마다 실행된다(spring.sql.init.mode=always, Hibernate가 테이블을 만든 뒤).
-- 이미 있는 ID는 그대로 두고(ON DUPLICATE KEY UPDATE id = id), 없는 ID만 들어간다. 중복이 아닌 오류는 그대로 실패한다.
-- 규칙: 이미 쓰는 ID와 이름은 바꾸지 않는다. 새 데이터는 마지막 ID 다음 번호로 추가한다.

-- ===== 옵션 종류 =====
INSERT INTO product_product_option_type (id, name, created_at, updated_at) VALUES
    (1, '의류 사이즈', NOW(6), NOW(6)),
    (2, '색상', NOW(6), NOW(6)),
    (3, '신발 사이즈', NOW(6), NOW(6)),
    (4, '공 사이즈', NOW(6), NOW(6)),
    (5, '골키퍼 장갑 사이즈', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE id = id;

-- ===== 상품옵션: 1 의류 사이즈 =====
INSERT INTO product_product_option_value (id, type_id, name, created_at, updated_at) VALUES
    (1, 1, 'XS', NOW(6), NOW(6)),
    (2, 1, 'S', NOW(6), NOW(6)),
    (3, 1, 'M', NOW(6), NOW(6)),
    (4, 1, 'L', NOW(6), NOW(6)),
    (5, 1, 'XL', NOW(6), NOW(6)),
    (6, 1, '2XL', NOW(6), NOW(6)),
    (7, 1, '3XL', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE id = id;

-- ===== 상품옵션: 2 색상 =====
INSERT INTO product_product_option_value (id, type_id, name, created_at, updated_at) VALUES
    (8, 2, '블랙', NOW(6), NOW(6)),
    (9, 2, '화이트', NOW(6), NOW(6)),
    (10, 2, '레드', NOW(6), NOW(6)),
    (11, 2, '블루', NOW(6), NOW(6)),
    (12, 2, '네이비', NOW(6), NOW(6)),
    (13, 2, '옐로우', NOW(6), NOW(6)),
    (14, 2, '그린', NOW(6), NOW(6)),
    (15, 2, '그레이', NOW(6), NOW(6)),
    (16, 2, '오렌지', NOW(6), NOW(6)),
    (17, 2, '핑크', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE id = id;

-- ===== 상품옵션: 3 신발 사이즈 (220 ~ 300, 5mm 단위) =====
INSERT INTO product_product_option_value (id, type_id, name, created_at, updated_at) VALUES
    (18, 3, '220', NOW(6), NOW(6)),
    (19, 3, '225', NOW(6), NOW(6)),
    (20, 3, '230', NOW(6), NOW(6)),
    (21, 3, '235', NOW(6), NOW(6)),
    (22, 3, '240', NOW(6), NOW(6)),
    (23, 3, '245', NOW(6), NOW(6)),
    (24, 3, '250', NOW(6), NOW(6)),
    (25, 3, '255', NOW(6), NOW(6)),
    (26, 3, '260', NOW(6), NOW(6)),
    (27, 3, '265', NOW(6), NOW(6)),
    (28, 3, '270', NOW(6), NOW(6)),
    (29, 3, '275', NOW(6), NOW(6)),
    (30, 3, '280', NOW(6), NOW(6)),
    (31, 3, '285', NOW(6), NOW(6)),
    (32, 3, '290', NOW(6), NOW(6)),
    (33, 3, '295', NOW(6), NOW(6)),
    (34, 3, '300', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE id = id;

-- ===== 상품옵션: 4 공 사이즈 =====
INSERT INTO product_product_option_value (id, type_id, name, created_at, updated_at) VALUES
    (35, 4, '3호', NOW(6), NOW(6)),
    (36, 4, '4호', NOW(6), NOW(6)),
    (37, 4, '5호', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE id = id;

-- ===== 상품옵션: 5 골키퍼 장갑 사이즈 =====
INSERT INTO product_product_option_value (id, type_id, name, created_at, updated_at) VALUES
    (38, 5, '6', NOW(6), NOW(6)),
    (39, 5, '7', NOW(6), NOW(6)),
    (40, 5, '8', NOW(6), NOW(6)),
    (41, 5, '9', NOW(6), NOW(6)),
    (42, 5, '10', NOW(6), NOW(6)),
    (43, 5, '11', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE id = id;
