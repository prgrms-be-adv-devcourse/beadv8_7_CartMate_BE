-- 시나리오마다 실행: 결제 · 지갑 데이터를 지우고 시험 지갑을 다시 넣는다
-- 결제 서비스를 한 번 띄워 테이블(ddl-auto)이 만들어진 뒤에 실행한다
-- 이 DB(cartmate-lt-mysql 컨테이너)는 시험 전용이다. 공용 서버에서 실행하지 않는다
USE cartmate_payment;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE payment_refund;
TRUNCATE TABLE payment;
TRUNCATE TABLE holding_settlement;
TRUNCATE TABLE holding;
TRUNCATE TABLE wallet_transaction;
TRUNCATE TABLE wallet;
TRUNCATE TABLE inbox_event;        -- Kafka 테스트: 받은 이벤트 · 처리 기록 · 보낸 이벤트
TRUNCATE TABLE processed_event;
TRUNCATE TABLE outbox_event;
SET FOREIGN_KEY_CHECKS = 1;

-- 시작 잔액 기록 (verify.sql이 "시작 잔액 - 현재 잔액 = 결제 합계"를 확인할 때 쓴다. 앱 테이블이 아니다)
DROP TABLE IF EXISTS lt_seed;
CREATE TABLE lt_seed (
    member_id       BIGINT PRIMARY KEY,
    initial_balance BIGINT NOT NULL,
    purpose         VARCHAR(20) NOT NULL
);

-- S1 · S2 정상 부하: 회원 10001~11000, 100만 원씩
INSERT INTO lt_seed
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1000)
SELECT 10000 + i, 1000000, 'S2' FROM n;

-- S3 같은 주문 동시 결제: 라운드마다 회원 하나 (15001~15020), 100만 원씩
INSERT INTO lt_seed
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 20)
SELECT 15000 + i, 1000000, 'S3' FROM n;

-- S4 같은 지갑 경합: 라운드마다 회원 하나 (20001~20005), 10만 원 → 5천 원 결제 100건 중 20건만 성공해야 함
INSERT INTO lt_seed
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 5)
SELECT 20000 + i, 100000, 'S4' FROM n;

-- S5 되돌리기 중복: 회원 30001~30010, 100만 원씩
INSERT INTO lt_seed
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 10)
SELECT 30000 + i, 1000000, 'S5' FROM n;

INSERT INTO wallet (member_id, balance, version, created_at, updated_at)
SELECT member_id, initial_balance, 0, NOW(6), NOW(6) FROM lt_seed;

SELECT purpose, COUNT(*) AS wallets, SUM(initial_balance) AS total_balance FROM lt_seed GROUP BY purpose;
