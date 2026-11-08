.PHONY: run rund services stop clean wait schema gen load setup teardown
.DEFAULT_GOAL = run

export COMPOSE_ENV_FILES := .env.local
include .env.local

CLICKHOUSE_CLIENT = docker compose exec -T clickhouse clickhouse-client --port $(CLICKHOUSE_PORT) --multiquery

# Runs all services in detached mode.
run:
	docker compose up -d --build

# Runs all services without detached mode (for debugging).
rund:
	docker compose up --build

# Shows all service statuses.
services:
	docker compose ps

# Stops all running services.
stop:
	docker compose down

# Cleans up all resources including volumes.
clean:
	docker compose down -v

# Waits until ClickHouse accepts queries (up to 60 seconds).
wait:
	@for i in $$(seq 1 60); do \
		$(CLICKHOUSE_CLIENT) --query "SELECT 1" >/dev/null 2>&1 && exit 0; \
		sleep 1; \
	done; \
	echo "ClickHouse is not ready" >&2; exit 1

# Creates the database and tables.
schema: wait
	$(CLICKHOUSE_CLIENT) < sql/table_init.sql

# Generates datasets into data/.
gen:
	python3 generator/generate.py --out data

TABLES = dim_realty dim_gauges dim_tariffs dim_users fact_gauge_bindings fact_meter_readings fact_invoice

# Loads datasets from data/ into ClickHouse. Each table is truncated first, so reloading does not duplicate rows.
load: wait
	@for t in $(TABLES); do \
		test -f data/$$t.csv || { echo "data/$$t.csv not found, run make gen" >&2; exit 1; }; \
		echo "loading $$t"; \
		$(CLICKHOUSE_CLIENT) --query "TRUNCATE TABLE $(CLICKHOUSE_DATABASE).$$t" || exit 1; \
		$(CLICKHOUSE_CLIENT) --query "INSERT INTO $(CLICKHOUSE_DATABASE).$$t FORMAT CSVWithNames" < data/$$t.csv || exit 1; \
	done

# Creates the schema, generates and loads data. Requires running ClickHouse (make run).
setup: schema gen load

# Drops all tables, the database returns to its initial state.
teardown: wait
	$(CLICKHOUSE_CLIENT) < sql/table_drop.sql
