# OrbitaMarket

Платформа заказов спутниковых продуктов: архивные снимки, tasking, мониторинг территории.

## Архитектура

```
Client
  │  HTTP :8080
  ▼
┌─────────────────┐
│   API Gateway   │  Spring Cloud Gateway
│    :8080        │  /payments/** → :8081
└────────┬────────┘  /orders/**  → :8082
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌────────┐  ┌────────┐
│Payments│  │ Orders │  Spring Boot 3 / Java 17
│  :8081 │  │  :8082 │
└───┬────┘  └───┬────┘
    │   Kafka    │
    │  ◄──────►  │
    ▼            ▼
┌────────────────────┐
│  Kafka (KRaft)     │  Topics:
│  :9092             │  • order-payment-requested
└────────────────────┘  • order-payment-completed
                        • order-payment-failed
┌──────────────┐  ┌──────────────┐
│ payments_db  │  │  orders_db   │  PostgreSQL 16
│ (accounts,   │  │ (orders,     │  Отдельные БД,
│  inbox)      │  │  outbox,     │  без общих таблиц
└──────────────┘  │  result_inbox│
                  └──────────────┘
```

## user_id

Во всех запросах user_id передаётся заголовком `X-User-Id`.
Для демо используйте любое строковое значение, например `user-42`.

```bash
X-User-Id: user-42
```

## Kafka Topics

| Топик | Направление | Событие |
|-------|-------------|---------|
| `order-payment-requested` | Orders → Payments | Запрос на списание |
| `order-payment-completed` | Payments → Orders | Списание прошло |
| `order-payment-failed` | Payments → Orders | Списание не прошло |

## Быстрый запуск

### Требования
- Docker ≥ 24, Docker Compose v2
- JDK 17 + Maven 3.9 (для сборки)

### 1. Сборка и запуск

```bash
# Собрать все JAR-файлы
mvn clean package -DskipTests

# Поднять всю инфраструктуру одной командой
docker compose up --build
```

Дождитесь сообщений `Started PaymentsServiceApplication` и `Started OrdersServiceApplication`
(обычно 30–60 секунд).

### 2. Проверка health

```bash
curl http://localhost:8080/payments/actuator/health  # {"status":"UP"}
curl http://localhost:8080/orders/actuator/health    # {"status":"UP"}
```

---

## API Reference

### Payments Service (через Gateway: `/payments/...`)

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/payments/accounts` | Создать счёт |
| POST | `/payments/accounts/top-up` | Пополнить баланс |
| GET  | `/payments/accounts/balance` | Получить баланс |

### Orders Service (через Gateway: `/orders/...`)

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/orders/orders` | Создать заказ |
| GET  | `/orders/orders` | Список заказов |
| GET  | `/orders/orders/{id}` | Детали заказа |

---

## Чек-лист сценариев (раздел 7.1 ТЗ)

Каждый блок ниже самодостаточен и может быть скопирован в терминал целиком —
не нужно вручную подставлять `order_id` из предыдущего ответа, скрипт делает
это сам через `jq` (если `jq` не установлен: `apt install jq` / `brew install jq`).

### Сценарий 1 — Happy path

```bash
# 1. Создать счёт
curl -s -X POST http://localhost:8080/payments/accounts -H "X-User-Id: user-42" | jq

# 2. Пополнить на 1000 геокредитов
curl -s -X POST http://localhost:8080/payments/accounts/top-up \
  -H "X-User-Id: user-42" -H "Content-Type: application/json" \
  -d '{"amount": 1000}' | jq

# 3. Создать заказ на 120 и сохранить order_id
ORDER_ID=$(curl -s -X POST http://localhost:8080/orders/orders \
  -H "X-User-Id: user-42" -H "Content-Type: application/json" \
  -d '{
    "product_type": "ARCHIVE",
    "price": 120,
    "payload": {
      "aoi": "POLYGON((37.6 55.7, 37.7 55.7, 37.7 55.8, 37.6 55.8, 37.6 55.7))",
      "capture_date": "2024-06-15",
      "sensor_type": "MSI"
    }
  }' | jq -r '.order_id')
echo "order_id=$ORDER_ID"

# 4. Подождать обработку платежа через Kafka и проверить статус
sleep 3
curl -s http://localhost:8080/orders/orders/$ORDER_ID -H "X-User-Id: user-42" | jq
# Ожидаем: "status": "PAID"

# 5. Проверить баланс (должен быть 880 = 1000 - 120)
curl -s http://localhost:8080/payments/accounts/balance -H "X-User-Id: user-42" | jq
# Ожидаем: "balance": 880
```

### Сценарий 2 — Недостаточно средств

```bash
# 1. Создать счёт для нового пользователя
curl -s -X POST http://localhost:8080/payments/accounts -H "X-User-Id: user-43" | jq

# 2. Пополнить только на 50 (заведомо меньше цены заказа)
curl -s -X POST http://localhost:8080/payments/accounts/top-up \
  -H "X-User-Id: user-43" -H "Content-Type: application/json" -d '{"amount": 50}' | jq

# 3. Заказ на 120 → должен стать PAYMENT_FAILED
ORDER_ID=$(curl -s -X POST http://localhost:8080/orders/orders \
  -H "X-User-Id: user-43" -H "Content-Type: application/json" \
  -d '{"product_type":"ARCHIVE","price":120,"payload":{"aoi":"x","capture_date":"2024-01-01","sensor_type":"SAR"}}' \
  | jq -r '.order_id')

sleep 3
curl -s http://localhost:8080/orders/orders/$ORDER_ID -H "X-User-Id: user-43" | jq
# Ожидаем: "status": "PAYMENT_FAILED", "failure_reason": "INSUFFICIENT_BALANCE"

# 4. Баланс не должен измениться (остаётся 50)
curl -s http://localhost:8080/payments/accounts/balance -H "X-User-Id: user-43" | jq
```

### Сценарий 3 — Идемпотентность списания (повторная доставка события)

Это покрыто на уровне unit-тестов, потому что воспроизвести повторную доставку
через HTTP API нельзя: при каждом `POST /orders/orders` сервис генерирует
новый `event_id`, так что обычный повтор curl-запроса создаст НОВЫЙ заказ,
а не повторно доставит ТО ЖЕ событие. Реальная повторная доставка происходит
на уровне Kafka (например, если consumer упал до коммита оффсета).

Проверяется тестами:
- `AccountServiceTest.processPayment_duplicateEvent_returnsDuplicate` — тот же
  `event_id` второй раз → `payment_inbox` отклоняет вставку по UNIQUE-constraint,
  баланс не трогается.
- `PaymentEventConsumerTest.duplicate_noPublishButAcks` — дубликат не публикует
  повторное событие в Kafka, но оффсет коммитится (сообщение безопасно пропустить).

Ручная проверка через реальный Kafka-топик (опционально, если нужно убедиться
вживую): зайти в контейнер брокера и повторно отправить ту же запись из
`order-payment-requested` с тем же `event_id` через `kafka-console-producer.sh`,
затем убедиться, что баланс в `payments_db.accounts` не изменился второй раз.

### Сценарий 4 — Два параллельных заказа по 400 при балансе 1000

```bash
# 1. Подготовить пользователя с балансом 1000
curl -s -X POST http://localhost:8080/payments/accounts -H "X-User-Id: user-44" | jq
curl -s -X POST http://localhost:8080/payments/accounts/top-up \
  -H "X-User-Id: user-44" -H "Content-Type: application/json" -d '{"amount": 1000}' | jq

# 2. Запустить ДВА заказа по 400 одновременно (& + wait — настоящий параллелизм)
curl -s -X POST http://localhost:8080/orders/orders -H "X-User-Id: user-44" \
  -H "Content-Type: application/json" \
  -d '{"product_type":"ARCHIVE","price":400,"payload":{"aoi":"x","capture_date":"2024-01-01","sensor_type":"MSI"}}' \
  -o /tmp/order_a.json &
curl -s -X POST http://localhost:8080/orders/orders -H "X-User-Id: user-44" \
  -H "Content-Type: application/json" \
  -d '{"product_type":"ARCHIVE","price":400,"payload":{"aoi":"x","capture_date":"2024-01-01","sensor_type":"MSI"}}' \
  -o /tmp/order_b.json &
wait

sleep 3
echo "Order A: $(jq -r '.order_id' /tmp/order_a.json)"
curl -s http://localhost:8080/orders/orders/$(jq -r '.order_id' /tmp/order_a.json) -H "X-User-Id: user-44" | jq '.status'
echo "Order B: $(jq -r '.order_id' /tmp/order_b.json)"
curl -s http://localhost:8080/orders/orders/$(jq -r '.order_id' /tmp/order_b.json) -H "X-User-Id: user-44" | jq '.status'

# 3. Итоговый баланс — оба заказа по 400 укладываются в 1000 (400+400=800 ≤ 1000),
#    так что ОБА должны стать PAID, баланс должен быть 200.
#    Гарантия корректности при гонке обеспечивается pessimistic lock
#    (SELECT ... FOR UPDATE) в AccountService — баланс никогда не уйдёт в минус,
#    независимо от порядка обработки двух параллельных списаний.
curl -s http://localhost:8080/payments/accounts/balance -H "X-User-Id: user-44" | jq
# Ожидаем: "balance": 200
```

### Сценарий 5 — Повторный POST /accounts (идемпотентность)

```bash
echo "Первый вызов (ожидаем 201 Created):"
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/payments/accounts -H "X-User-Id: user-45"

echo "Второй вызов для того же пользователя (ожидаем 200 OK, не 201 — ничего нового не создано):"
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/payments/accounts -H "X-User-Id: user-45"
```

Оба запроса возвращают один и тот же `accountId` — дубликат счёта не создаётся.
Это же поведение выдерживается и под конкурентной нагрузкой: если два запроса
на создание счёта для нового пользователя прилетают одновременно, оба получат
успешный ответ с одним и тем же счётом (один — 201, второй — 200), а не
ошибку — см. `AccountServiceTest.createAccount_concurrentRace_returnsWinnerAccount`.

---


## Запуск тестов

```bash
# Все тесты с Allure
mvn test

# Сгенерировать Allure-отчёт
mvn allure:report

# Открыть отчёт
mvn allure:serve
```

---

## ИБ-сканирование

```bash
# Gitleaks
docker run --rm -v $(pwd):/path zricethezav/gitleaks:latest detect \
  --source /path --report-format json --report-path /path/gitleaks-report.json

# Semgrep
docker run --rm -v $(pwd):/src returntocorp/semgrep:latest \
  semgrep --config p/java --config p/owasp-top-ten \
  --json --output /src/semgrep-report.json /src
```

Результаты и триаж: [docs/security-triage.md](docs/security-triage.md)

---

## SQL-статистика

Запросы к `orders_db` находятся в [docs/analytics.sql](docs/analytics.sql).

```bash
# Выполнить через psql
docker exec -it orbita-postgres psql -U orbita -d orders_db \
  -f /docker-entrypoint-initdb.d/analytics.sql
```

---

## Структура репозитория

```
orbita-market/
├── api-gateway/              # Spring Cloud Gateway
│   ├── src/main/resources/application.yml
│   └── Dockerfile
├── payments-service/         # Payments микросервис
│   ├── src/main/java/com/orbita/payments/
│   │   ├── controller/       # AccountController
│   │   ├── service/          # AccountService (бизнес-логика)
│   │   ├── domain/           # Account, PaymentInboxEntry
│   │   ├── kafka/            # PaymentEventConsumer, PaymentEventProducer
│   │   └── repository/
│   └── src/main/resources/db/migration/V1__create_payments_schema.sql
├── orders-service/           # Orders микросервис
│   ├── src/main/java/com/orbita/orders/
│   │   ├── controller/       # OrderController
│   │   ├── service/          # OrderService
│   │   ├── domain/           # Order, OutboxEvent, PaymentResultInbox
│   │   ├── kafka/            # OutboxPoller, PaymentResultConsumer
│   │   └── repository/
│   └── src/main/resources/db/migration/V1__create_orders_schema.sql
├── docs/
│   ├── analytics.sql         # Статистика «кто и сколько купил»
│   ├── c4-context.puml       # C1 — System Context
│   ├── c4-containers.puml    # C2 — Containers
│   └── security-triage.md   # Таблица триажа ИБ
├── docker-compose.yml
├── init-db.sql
├── PROJECT.md
└── README.md
```
