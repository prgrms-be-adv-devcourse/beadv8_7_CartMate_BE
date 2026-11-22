-- 시나리오가 끝난 뒤 실행: 돈 불변식 검사. violations가 전부 0이어야 합격
USE cartmate_payment;

-- 결제 합계 (지갑별 실제로 빠진 돈 = 결제액 - 환불액)
DROP TEMPORARY TABLE IF EXISTS lt_paid;
CREATE TEMPORARY TABLE lt_paid AS
SELECT wallet_id, COUNT(*) AS payments, SUM(amount - refunded_amount) AS net_paid
FROM payment GROUP BY wallet_id;

SELECT 'V1 주문 하나에 결제 2건 이상' AS check_name, COUNT(*) AS violations
FROM (SELECT payment_type, order_id, member_id FROM payment
      GROUP BY payment_type, order_id, member_id HAVING COUNT(*) > 1) t
UNION ALL
SELECT 'V2 잔액이 음수인 지갑', COUNT(*) FROM wallet WHERE balance < 0
UNION ALL
SELECT 'V3 시작 잔액 - 현재 잔액 != 결제 합계', COUNT(*)
FROM wallet w
JOIN lt_seed s ON s.member_id = w.member_id
LEFT JOIN lt_paid p ON p.wallet_id = w.id
WHERE s.initial_balance - w.balance <> COALESCE(p.net_paid, 0)
UNION ALL
SELECT 'V4 결제는 있는데 지갑 사용 기록(USE)이 1건이 아님', COUNT(*)
FROM payment p
WHERE (SELECT COUNT(*) FROM wallet_transaction t
       WHERE t.type = 'USE' AND t.order_id = p.order_id AND t.reference_id = p.id) <> 1
UNION ALL
SELECT 'V5 지갑 사용 기록(USE)은 있는데 결제가 없음', COUNT(*)
FROM wallet_transaction t
WHERE t.type = 'USE' AND NOT EXISTS (SELECT 1 FROM payment p WHERE p.id = t.reference_id)
UNION ALL
SELECT 'V6 결제 하나에 홀딩이 1건이 아님', COUNT(*)
FROM payment p
WHERE (SELECT COUNT(*) FROM holding h WHERE h.payment_id = p.id) <> 1
UNION ALL
SELECT 'V7 환불액이 결제액보다 큼', COUNT(*) FROM payment WHERE refunded_amount > amount
UNION ALL
SELECT 'V8 결제 하나에 되돌리기 환불이 2건 이상', COUNT(*)
FROM (SELECT payment_id FROM payment_refund WHERE reason = 'CHARGE_COMPENSATION'
      GROUP BY payment_id HAVING COUNT(*) > 1) t
UNION ALL
SELECT 'V9 결제의 환불액 != 환불 기록 합계', COUNT(*)
FROM payment p
WHERE p.refunded_amount <> (SELECT COALESCE(SUM(r.amount), 0) FROM payment_refund r WHERE r.payment_id = p.id)
UNION ALL
SELECT 'V10 지갑 잔액 != 마지막 거래 기록의 balance_after', COUNT(*)
FROM wallet w
WHERE EXISTS (SELECT 1 FROM wallet_transaction t WHERE t.wallet_id = w.id)
  AND w.balance <> (SELECT t.balance_after FROM wallet_transaction t WHERE t.wallet_id = w.id
                    ORDER BY t.id DESC LIMIT 1)
UNION ALL
SELECT 'V11 결제 하나에 구매자 취소 환불이 2건 이상 (Kafka 중복)', COUNT(*)
FROM (SELECT payment_id FROM payment_refund WHERE reason = 'BUYER_CANCEL'
      GROUP BY payment_id HAVING COUNT(*) > 1) t
UNION ALL
SELECT 'V12 처리 실패로 남은 인박스 이벤트', COUNT(*) FROM inbox_event WHERE status = 'FAILED';

-- 참고 수치 (위반 아님)
SELECT s.purpose,
       COUNT(DISTINCT p.id)                                   AS payments,
       COALESCE(SUM(p.amount), 0)                             AS paid_amount,
       COALESCE(SUM(p.refunded_amount), 0)                    AS refunded_amount,
       (SELECT COUNT(*) FROM payment_refund r JOIN payment p2 ON p2.id = r.payment_id
          JOIN lt_seed s2 ON s2.member_id = p2.member_id WHERE s2.purpose = s.purpose) AS refunds
FROM lt_seed s
LEFT JOIN payment p ON p.member_id = s.member_id
GROUP BY s.purpose;

-- Kafka 테스트 참고 수치
SELECT (SELECT COUNT(*) FROM inbox_event) AS inbox_rows,
       (SELECT COUNT(*) FROM inbox_event WHERE status = 'PROCESSED') AS inbox_processed,
       (SELECT COUNT(*) FROM payment_refund WHERE reason = 'BUYER_CANCEL') AS buyer_cancel_refunds,
       (SELECT COUNT(*) FROM payment WHERE status = 'CANCELED') AS fully_refunded_payments;
