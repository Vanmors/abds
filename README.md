# Энергосбыт

[![labs](https://github.com/Vanmors/abds/actions/workflows/energosbyt.lab.yml/badge.svg)](https://github.com/Vanmors/abds/actions/workflows/energosbyt.lab.yml)

Лабораторная работа №1 курса «Архитектура систем обработки больших данных»: SCD 1/2/3 в ClickHouse.
HTTP-сервис на Java 21 (Javalin, virtual threads, ClickHouse client-v2) поверх схемы «звезда»
энергосбытовой компании.

## Запуск

```bash
make run        # ClickHouse и приложение в docker compose
make setup      # схема, датасеты, загрузка (нужен make run)
curl -i localhost:8080/ping
make teardown   # удалить таблицы
make stop
```

| Цель | Что делает |
| --- | --- |
| `make gen` | `generator/generate.py` пишет CSV в `data/` (только стандартная библиотека, фиксированный seed, ~4 с) |
| `make load` | `TRUNCATE` и `INSERT ... FORMAT CSVWithNames` для каждой таблицы |
| `make schema` | `sql/table_init.sql` |
| `make teardown` | `sql/table_drop.sql` |

Все настройки берутся из `.env.local`, включая порты: ClickHouse и внутри контейнера слушает
`CLICKHOUSE_HTTP_PORT` и `CLICKHOUSE_PORT`, поэтому порты на хосте и в контейнере совпадают.
`.env` это симлинк на `.env.local`, а не отдельный файл настроек: docker compose читает `.env` автоматически,
поэтому шаги CI, которые вызывают `docker compose logs` и `docker compose down -v` без `--env-file`, тоже
получают переменные.

**Повторная загрузка.** `make load` очищает каждую таблицу перед вставкой, поэтому повторный запуск даёт те же
данные без дублей в любом движке. Генератор детерминированный: `make gen` всегда создаёт одинаковые файлы.

## Схема

| Таблица | Движок | Ключ сортировки | Партиция | Почему |
| --- | --- | --- | --- | --- |
| `dim_realty` | `MergeTree` | `id` | нет | поиск по id, 1 000 строк |
| `dim_gauges` | `MergeTree` | `id` | нет | поиск по id |
| `dim_users` | `ReplacingMergeTree(updated_at)` | `id` | нет | одна актуальная строка на собственника (SCD 1 и SCD 3) |
| `dim_tariffs` | `ReplacingMergeTree(updated_at)` | `(id, valid_from)` | нет | каждая версия имеет свой ключ (SCD 2); закрытие версии перезаписывает тот же ключ с более новым `updated_at` |
| `fact_gauge_bindings` | `VersionedCollapsingMergeTree(sign, version)` | `(realty_id, gauge_id)` | нет | поиск по объекту; строка аннулирования повторяет ключ и версию установки |
| `fact_meter_readings` | `ReplacingMergeTree(created_at)` | `(realty_id, reading_date)` | `toYear(reading_date)` | одно значение на объект и дату, побеждает более поздний `created_at` |
| `fact_invoice` | `MergeTree` | `(realty_id, created_at)` | нет | страница счетов объекта, отсортированная по дате |

- Каждый запрос API фильтрует по префиксу ключа сортировки, поэтому читаются только гранулы одного объекта.
- `ReplacingMergeTree` схлопывает дубли только внутри партиции. Партиция показаний считается от `reading_date`,
  а он входит в ключ, поэтому корректировка всегда попадает в ту же партицию, что и исходная строка.
  Партиции по годам держат число parts небольшим (4 партиции на 3 года).
- Справочники маленькие, партиционирование только добавило бы parts.

### FINAL

`ReplacingMergeTree` и `VersionedCollapsingMergeTree` схлопывают строки при фоновых слияниях, в неизвестный
момент. Запросы, которым нужна актуальная версия, используют `FINAL`:

- `dim_users`, `fact_meter_readings`, поиск версии `dim_tariffs` по дате: `FINAL` (важно, какая версия вернётся);
- проверки существования (`SELECT 1 ... LIMIT 1`): без `FINAL`, любая версия доказывает, что строка есть;
- `fact_gauge_bindings`: `GROUP BY gauge_id, version HAVING sum(sign) > 0`, результат верен до и после слияний;
- таблицы на `MergeTree`: `FINAL` неприменим.

### Почему VersionedCollapsingMergeTree для привязок счётчиков

`ReplacingMergeTree` с ключом `realty_id` был бы проще (одна вставка, без чтения перед записью), но без
отдельного признака удаления он не умеет аннулировать установку без замены, а его состояние видно только
через `FINAL` или `argMax`. С `sign` аннулирование это обычная вставка, а `sum(sign)` даёт состояние без
схлопывания при чтении.

## API

| Метод | Путь | Примечания |
| --- | --- | --- |
| GET | `/ping` | 200, пустое тело |
| GET | `/users/{id}` | |
| PATCH | `/users/{id}` | `fio` и/или `phone`; 204 |
| GET | `/realty/{id}` | |
| POST | `/realty/{id}/readings` | все поля обязательны; объект, собственник, счётчик и тариф должны существовать; 201 |
| GET | `/realty/{id}/readings?date_from=&date_to=` | оба обязательны, границы включительно; одно значение на дату |
| GET | `/realty/{id}/invoices?limit=10&offset=0` | сначала новые; `count` это общее число счетов |
| GET | `/tariffs?date=` | версия с `valid_from <= date < valid_to` |
| POST | `/realty/{id}/gauges` | аннулирует активную установку и устанавливает счётчик; 201 |

Ошибки в формате `{"message": "..."}`: 400 `invalid "<name>" field` / `invalid "<name>" parameter`, 404 `Not found`.
Валидация выполняется до поиска сущностей, поэтому невалидный ввод даёт 400, даже если сущности нет.
Два текста в задании не определены: `"fio" or "phone" field is required` (пустой PATCH) и
`invalid request body` (тело не JSON-объект). Перевёрнутый период сообщается как ошибка `date_from`.

Структура кода: `controller` (HTTP, валидация) → `service` (правила SCD) → `repository` (SQL), связывание вручную в `App`.

## Данные

| Таблица | Строк | Содержимое |
| --- | --- | --- |
| `dim_realty` | 1 000 | 5 домов, 950 квартир и 50 нежилых помещений |
| `dim_users` | 1 000 | `user_id = realty_id`, у ~10% заполнен `phone_prev` |
| `dim_tariffs` | 3 | тариф 1: с 2023-01-01, 2024-07-01, 2025-11-01 (открыта до 2099-01-01) |
| `dim_gauges` | 1 050 | счётчик `id = realty_id`, 50 замен с id 1001..1050 |
| `fact_gauge_bindings` | 1 100 в CSV, 1 000 в таблице | `+1 v1` на каждый объект, замены добавляют `-1 v1` и `+1 v2` |
| `fact_meter_readings` | 1 128 993 в CSV, 1 096 000 в таблице | ежедневные нарастающие показания 2023-11-01..2026-10-31, ~3% с корректировкой |
| `fact_invoice` | 36 000 | ежемесячные, выставлены в последний день месяца в 23:59:59 по версии тарифа на этот момент |

Строк в таблице меньше, чем в CSV, из-за настройки `optimize_on_insert` (включена по умолчанию): строки
внутри одного `INSERT` схлопываются уже при записи. Корректировки из CSV и пары `+1`/`-1` попадают в один блок
вставки и до таблицы не доходят. Через API каждая строка пишется отдельным `INSERT`, поэтому в сценариях SCD
видны обе строки до слияния.

## Сценарии SCD и запросы для отладки

- [docs/scd-scenarios.md](docs/scd-scenarios.md): SQL и запросы к API для каждого сценария SCD из задания
  и что они показывают.
- [api/energosbyt.http](api/energosbyt.http): запросы ко всем эндпоинтам, включая ошибки 400 и 404.
  Запускается из IntelliJ IDEA (HTTP Client) или VS Code (расширение REST Client).
