-- 기존 데이터를 옮긴 DB에서 새 코드를 띄우기 전에 1회 실행한다. 빈 DB면 바뀌는 행이 없다
-- 공동 주문이 이미 있는 CLOSED 공용 공동구매를 ORDER_CREATED로 옮긴다.
-- 그대로 두면 마감 뒤 시간 초과 스케줄러가 FROZEN으로 되돌려 수정 · 취소가 다시 열린다
UPDATE shared_group_purchase p
    JOIN shared_group_order o ON o.shared_group_purchase_id = p.id
SET p.status = 'ORDER_CREATED'
WHERE p.status = 'CLOSED';
