# PROJECT.md — OrbitaMarket

## Цель проекта

**OrbitaMarket** — платформа заказов спутниковых продуктов (архивные снимки, tasking,
мониторинг территории). Ядро платформы: приём заказов, биллинг в геокредитах,
согласованное списание при конкурентной нагрузке.

Технически: два микросервиса (Payments, Orders) + API Gateway, асинхронная оплата через
Kafka с гарантией effectively-exactly-once дебетования.

---

## Стейкхолдеры

| Роль | Интерес |
|------|---------|
| **Оператор ДЗЗ** | Быстрое создание заказов, прозрачный статус оплаты, надёжность |
| **Аналитическая компания** | Подписки на мониторинг, API-доступ к истории заказов |
| **Администратор платформы** | Инструменты пополнения счетов, SQL-статистика «кто и сколько купил», безопасность |

---

## Roadmap

### Этап 1 — Payments Service (Неделя 1)
- [x] Создание счёта (идемпотентное)
- [x] Пополнение баланса
- [x] Получение баланса
- [x] Оптимистическая блокировка + pessimistic lock при списании
- [x] Flyway миграции, PostgreSQL
- [x] Unit-тесты (MockMvc + Mockito)

### Этап 2 — Orders Service (Неделя 2)
- [x] Создание заказов ARCHIVE / TASKING / MONITORING
- [x] Жизненный цикл: CREATED → PAYMENT_PENDING → PAID / PAYMENT_FAILED
- [x] Список заказов и детали по ID (только owner)
- [x] Flyway миграции, PostgreSQL

### Этап 3 — Асинхронный брокер (Неделя 3)
- [x] Kafka в KRaft-режиме (docker-compose)
- [x] Transactional Outbox в Orders (атомарная запись заказ + событие)
- [x] OutboxPoller — реле событий из БД в Kafka
- [x] Inbox в Payments — идемпотентность списания по event_id
- [x] Inbox в Orders — защита от дублей результатов оплаты
- [x] Топики: order-payment-requested, order-payment-completed, order-payment-failed

### Этап 4 — API Gateway + Docker (Неделя 4)
- [x] Spring Cloud Gateway: /payments/** → :8081, /orders/** → :8082
- [x] Проброс X-User-Id
- [x] docker-compose (postgres + kafka + 3 сервиса)
- [x] Dockerfiles для всех сервисов

### Этап 5 — Качество и документация (Неделя 5) — **MVP**
- [x] Allure-отчёты (allure-junit5)
- [x] Чек-лист сценариев (5 из ТЗ)
- [x] docs/analytics.sql — 7 запросов
- [x] C4 диаграммы C1 + C2 (PlantUML)
- [x] Таблица триажа ИБ (Gitleaks + Semgrep, ≥7 строк)
- [x] README.md

### Следующие шаги (после MVP)

| Шаг | Описание |
|-----|----------|
| **Аутентификация** | Spring Security + JWT/OAuth2 в Gateway; user_id из claims |
| **Frontend** | React SPA / Next.js для пользовательского интерфейса |
| **Notification Service** | Email/Webhook уведомления об изменении статуса заказа |
| **Retry / DLQ** | Dead Letter Queue для необрабатываемых сообщений Kafka |
| **Prometheus + Grafana** | Метрики: баланс, throughput, consumer lag |
| **CI/CD** | GitHub Actions: build → test → docker push → deploy |
| **k8s** | Helm charts для Kubernetes-деплоя |
