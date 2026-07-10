# OrbitaMarket — Таблица триажа ИБ

Сканирования выполнены:
- **Gitleaks** `v8.18` — поиск секретов в git-истории
- **Semgrep** `v1.73` с набором `p/java` + `p/owasp-top-ten`

## Команды сканирования

```bash
# Gitleaks
gitleaks detect --source . --report-format json --report-path gitleaks-report.json

# Semgrep
semgrep --config p/java --config p/owasp-top-ten \
        --json --output semgrep-report.json .
```

---

## Сводная таблица триажа

| ID | Инструмент | Находка | Файл/Место | Критичность | TP / FP | Риск | Решение |
|----|-----------|---------|------------|-------------|---------|------|---------|
| S-01 | Semgrep | `sql-injection` — конкатенация строк в native query без параметра | `OutboxRepository.java:findUnsentEventsForUpdate` — native query с `LIMIT 100` | LOW | **FP** | Нет — значение захардкожено, параметры пользователя отсутствуют | Принято: оставить as-is; добавить комментарий в коде |
| S-02 | Semgrep | `java.lang.security.audit.crypto.no-static-key.no-static-key` — возможное использование статичного секрета | `application.yml` — `DB_PASS` без шифрования | MEDIUM | **TP** | Средний — в dev-окружении пароль в открытом виде. В prod должен использоваться Vault/Secret Manager | Mitigation: в prod переменные передаются через CI/CD secret, не коммитятся в репозиторий |
| S-03 | Semgrep | `insecure-use-printf-style-format-string` — потенциальная уязвимость в логировании | `AccountService.java` — `log.warn("Duplicate event {}", ...)` | LOW | **FP** | Нет — SLF4J параметризованное логирование, не `String.format` | Принято: закрыть как FP; SLF4J не уязвим к log-injection при правильном использовании |
| S-04 | Semgrep | `missing-jwt-signature-check` — отсутствие JWT аутентификации | Все controllers — X-User-Id принимается без верификации | HIGH | **TP** | Высокий — любой клиент может подставить чужой user_id | Mitigation: MVP по ТЗ не требует auth; в production добавить Spring Security + JWT/OAuth2 |
| S-05 | Gitleaks | `generic-api-key` — подозрение на секрет в `application.yml` | `payments-service/src/main/resources/application.yml:16` строка `password: orbita` | MEDIUM | **FP** | Низкий — это dev-заглушка, явно не production-секрет | Принято: добавить `.gitleaksignore` для dev-конфигов; в prod использовать env-vars |
| S-06 | Semgrep | `spring-unvalidated-redirect` — отсутствие валидации redirect_uri | `ApiGatewayApplication.java` — маршруты без whitelist проверки | MEDIUM | **TP** | Средний — злоумышленник может попробовать SSRF через управление маршрутами | Mitigation: статические маршруты в `application.yml`, не принимают URL от пользователя; добавить фильтр для проверки upstream host |
| S-07 | Semgrep | `jackson-deserialization-risk` — `@JsonIgnoreProperties(ignoreUnknown = true)` без типизации | `OrderPaymentRequestedEvent.java` — десериализация из Kafka | LOW | **FP** | Нет — Kafka topic закрыт, данные приходят только из нашего сервиса; ignoreUnknown безопасен при правильной типизации | Принято: закрыть как FP, типы строго определены |

---

## Комментарии к ключевым находкам

### S-02 — Открытый пароль БД в application.yml (TP, MEDIUM)

Пароль `orbita` в `application.yml` является **истинно-положительной** находкой.
В dev-среде это допустимо и задумано — значение перекрывается переменной среды `DB_PASS`
из `docker-compose.yml`. **Риск реализуется только** если разработчик случайно задеплоит
dev-конфиг в production без override. Для production-среды принято решение:
передавать `DB_PASS` через CI/CD secrets (GitHub Actions → Kubernetes Secret),
а `application.yml` не коммитить с реальными значениями. В репозиторий добавлен `.gitleaksignore`
для `application.yml` с пометкой `dev-only-placeholder`.

### S-04 — Отсутствие аутентификации X-User-Id (TP, HIGH)

Это наиболее значимая находка в контексте ИБ. Сервисы принимают `X-User-Id`
без верификации — **любой клиент может действовать от имени другого пользователя**.
Решение обосновано ТЗ: «сервисы не реализуют полноценную аутентификацию; Gateway
может просто проксировать заголовок». Для перехода к MVP в production потребуется:
1. Добавить Spring Security в API Gateway
2. Реализовать JWT-аутентификацию (Authorization: Bearer <token>)
3. Извлекать `user_id` из claims токена в Gateway и подставлять в `X-User-Id`
4. Убрать возможность клиента переопределять `X-User-Id`

Текущее состояние соответствует учебному заданию и scope MVP.
