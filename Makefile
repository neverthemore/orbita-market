# ════════════════════════════════════════════════════════════════════════════
# OrbitaMarket — Developer Makefile
# ════════════════════════════════════════════════════════════════════════════
.PHONY: help build test allure up down restart logs ps clean security-scan

COMPOSE = docker compose

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ─── Build ───────────────────────────────────────────────────────────────────

build: ## Compile & package all services (skip tests)
	mvn clean package -DskipTests

build-test: ## Compile & run all unit tests
	mvn clean package

# ─── Tests & Reports ─────────────────────────────────────────────────────────

test: ## Run all unit tests
	mvn test

allure: ## Generate Allure report and open in browser
	mvn allure:serve

allure-report: ## Generate static Allure report in target/site/allure-maven-plugin/
	mvn allure:report

# ─── Docker ──────────────────────────────────────────────────────────────────

up: build ## Build JARs and start all Docker services
	$(COMPOSE) up --build -d
	@echo ""
	@echo "  Waiting for services to start..."
	@sleep 5
	@echo ""
	@echo "  ✅  Gateway  : http://localhost:8080"
	@echo "  ✅  Payments : http://localhost:8081/actuator/health"
	@echo "  ✅  Orders   : http://localhost:8082/actuator/health"

down: ## Stop and remove all containers
	$(COMPOSE) down

restart: down up ## Restart everything

logs: ## Tail logs for all services
	$(COMPOSE) logs -f payments-service orders-service gateway

ps: ## Show container status
	$(COMPOSE) ps

clean: down ## Remove containers, images and volumes
	$(COMPOSE) down -v --rmi local
	mvn clean

# ─── Security Scanning ───────────────────────────────────────────────────────

security-scan: ## Run Gitleaks + Semgrep (requires Docker)
	@echo "── Gitleaks ────────────────────────────────────────────────────────"
	docker run --rm -v "$(PWD):/path" zricethezav/gitleaks:latest \
	  detect --source /path --report-format json \
	  --report-path /path/gitleaks-report.json --no-git || true
	@echo ""
	@echo "── Semgrep ─────────────────────────────────────────────────────────"
	docker run --rm -v "$(PWD):/src" returntocorp/semgrep:latest \
	  semgrep --config p/java --config p/owasp-top-ten \
	  --json --output /src/semgrep-report.json /src || true
	@echo ""
	@echo "Results: gitleaks-report.json  semgrep-report.json"
	@echo "Triage:  docs/security-triage.md"

# ─── Quick Smoke Test ─────────────────────────────────────────────────────────

smoke: ## Run quick end-to-end smoke test against running stack
	@echo "── Creating account ─────────────────────────────────────────────────"
	curl -sf -X POST http://localhost:8080/payments/accounts -H "X-User-Id: smoke-user" | python3 -m json.tool
	@echo ""
	@echo "── Top up 1000 geocredits ──────────────────────────────────────────"
	curl -sf -X POST http://localhost:8080/payments/accounts/top-up \
	  -H "X-User-Id: smoke-user" -H "Content-Type: application/json" \
	  -d '{"amount":1000}' | python3 -m json.tool
	@echo ""
	@echo "── Place ARCHIVE order (120 geocredits) ─────────────────────────────"
	@ORDER_ID=$$(curl -sf -X POST http://localhost:8080/orders/orders \
	  -H "X-User-Id: smoke-user" -H "Content-Type: application/json" \
	  -d '{"product_type":"ARCHIVE","price":120,"payload":{"aoi":"POLYGON((0 0,1 0,1 1,0 1,0 0))","capture_date":"2024-06-15","sensor_type":"MSI"}}' | \
	  python3 -c "import sys,json; print(json.load(sys.stdin)['order_id'])"); \
	echo "  order_id=$$ORDER_ID"; \
	sleep 3; \
	echo "── Order status after 3s (expect PAID) ──────────────────────────────"; \
	curl -sf http://localhost:8080/orders/orders/$$ORDER_ID -H "X-User-Id: smoke-user" | python3 -m json.tool
	@echo ""
	@echo "── Final balance (expect 880) ───────────────────────────────────────"
	curl -sf http://localhost:8080/payments/accounts/balance -H "X-User-Id: smoke-user" | python3 -m json.tool

# ─── Analytics ───────────────────────────────────────────────────────────────

analytics: ## Run analytics.sql against orders_db
	docker exec orbita-postgres psql -U orbita -d orders_db -f /dev/stdin < docs/analytics.sql
