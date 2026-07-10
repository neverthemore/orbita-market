-- ════════════════════════════════════════════════════════════════════════════
-- OrbitaMarket Analytics Queries
-- Run against orders_db (Orders Service database)
-- ════════════════════════════════════════════════════════════════════════════

-- 1. Кто и сколько купил (основной запрос из ТЗ)
--    Сумма и количество оплаченных заказов по каждому пользователю.
SELECT
    user_id,
    COUNT(*)    AS paid_orders_count,
    SUM(price)  AS total_spent_geocredits
FROM orders
WHERE status = 'PAID'
GROUP BY user_id
ORDER BY total_spent_geocredits DESC;


-- 2. Сводка по статусам заказов (операционная статистика)
SELECT
    status,
    COUNT(*) AS order_count
FROM orders
GROUP BY status
ORDER BY order_count DESC;


-- 3. Покупки по типам продуктов
SELECT
    product_type,
    COUNT(*)    AS total_orders,
    SUM(price)  AS total_revenue_geocredits,
    AVG(price)  AS avg_price_geocredits
FROM orders
WHERE status = 'PAID'
GROUP BY product_type
ORDER BY total_revenue_geocredits DESC;


-- 4. ТОП-10 пользователей по числу оплаченных заказов
SELECT
    user_id,
    COUNT(*) AS paid_orders_count,
    SUM(price) AS total_geocredits
FROM orders
WHERE status = 'PAID'
GROUP BY user_id
ORDER BY paid_orders_count DESC
LIMIT 10;


-- 5. Динамика заказов по дням (активность за последние 30 дней)
SELECT
    DATE(created_at)         AS order_date,
    COUNT(*)                 AS orders_created,
    SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) AS orders_paid,
    SUM(CASE WHEN status = 'PAYMENT_FAILED' THEN 1 ELSE 0 END) AS orders_failed
FROM orders
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY DATE(created_at)
ORDER BY order_date DESC;


-- 6. Конверсия: доля успешных платежей
SELECT
    COUNT(*) FILTER (WHERE status = 'PAID')           AS paid,
    COUNT(*) FILTER (WHERE status = 'PAYMENT_FAILED') AS failed,
    COUNT(*) FILTER (WHERE status = 'PAYMENT_PENDING') AS pending,
    ROUND(
        COUNT(*) FILTER (WHERE status = 'PAID')::NUMERIC /
        NULLIF(COUNT(*) FILTER (WHERE status IN ('PAID','PAYMENT_FAILED')), 0) * 100,
        2
    ) AS conversion_pct
FROM orders;


-- 7. Причины отказов в оплате
SELECT
    failure_reason,
    COUNT(*) AS failure_count
FROM orders
WHERE status IN ('PAYMENT_FAILED', 'REJECTED')
  AND failure_reason IS NOT NULL
GROUP BY failure_reason
ORDER BY failure_count DESC;
