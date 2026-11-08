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
вставки и до таблицы не доходят. Через API каждая строка пишется отдельным `INSERT`, поэтому в сценариях ниже
видны обе строки до слияния.

## Сценарии SCD

Ниже `CH` это `docker compose exec -T clickhouse clickhouse-client --port 9000 -d energosbyt --query`
(запускать с `COMPOSE_ENV_FILES=.env.local`).

### Собственник сменил фамилию (SCD 1)

```bash
curl -X PATCH localhost:8080/users/1 -d '{"fio": "Петрова Анна Сергеевна"}'
curl localhost:8080/users/1
$CH "SELECT id, fio, updated_at FROM dim_users WHERE id = 1"        # старая и новая строка до слияния
$CH "SELECT id, fio, updated_at FROM dim_users FINAL WHERE id = 1"  # только новое ФИО
$CH "OPTIMIZE TABLE dim_users FINAL"                                 # принудительное слияние: старое ФИО удалено физически
```

`fio` перезаписывается, предыдущее значение нигде не сохраняется. Старая строка физически существует только
до ближайшего слияния.

### Собственник сменил телефон дважды (SCD 3)

```bash
curl -X PATCH localhost:8080/users/1 -d '{"phone": "+79111111111"}'
curl -X PATCH localhost:8080/users/1 -d '{"phone": "+79222222222"}'
curl localhost:8080/users/1   # phone +79222222222, phone_prev +79111111111
$CH "SELECT phone, phone_prev, updated_at FROM dim_users WHERE id = 1 ORDER BY updated_at"
```

Хранится только одно предыдущее значение. Каждое изменение пишет `updated_at = greatest(now(), updated_at + 1)`,
поэтому два изменения в одну секунду всё равно получают разные версии.

### Тариф изменился с 1 ноября (SCD 2)

```bash
curl 'localhost:8080/tariffs?date=2025-10-31'   # 560/280, valid_to 2025-11-01 00:00:00
curl 'localhost:8080/tariffs?date=2025-11-01'   # 620/310, valid_to 2099-01-01 00:00:00
$CH "SELECT id, price_day_kwph, price_night_kwph, valid_from, valid_to FROM dim_tariffs FINAL ORDER BY valid_from"
```

Новая смена тарифа, например с 2026-12-01, это две строки: открытая версия с тем же ключом и более новым
`updated_at` (закрыта датой смены) и новая открытая версия:

```sql
INSERT INTO dim_tariffs VALUES
    (1, 'День-Ночь', 620, 310, '2025-11-01 00:00:00', '2026-12-01 00:00:00', '2025-10-02 00:00:00', now()),
    (1, 'День-Ночь', 680, 340, '2026-12-01 00:00:00', '2099-01-01 00:00:00', now(), now());
```

### Пересчёт счёта за старый месяц (SCD 2)

```sql
SELECT i.created_at, i.day_kw, i.night_kw, i.price,
       t.valid_from, i.day_kw * t.price_day_kwph + i.night_kw * t.price_night_kwph AS recalculated
FROM fact_invoice AS i
CROSS JOIN (SELECT * FROM dim_tariffs FINAL) AS t
WHERE i.realty_id = 1 AND t.id = i.tariff_id
  AND t.valid_from <= i.created_at AND i.created_at < t.valid_to
ORDER BY i.created_at;
```

`recalculated` совпадает с `price` за каждый месяц, в том числе после добавления новой версии тарифа: счета
никогда не изменяются, а цена берётся из версии, действовавшей на дату счёта.

### Корректировка показания (ReplacingMergeTree)

```bash
curl -X POST localhost:8080/realty/1/readings \
  -d '{"user_id":1,"gauge_id":1,"tariff_id":1,"reading_date":"2026-10-01","day_kw":99999,"night_kw":300}'
curl 'localhost:8080/realty/1/readings?date_from=2026-10-01&date_to=2026-10-01'   # одна строка, day_kw 99999
$CH "SELECT count() FROM fact_meter_readings WHERE realty_id = 1 AND reading_date = '2026-10-01'"        # 2+
$CH "SELECT count() FROM fact_meter_readings FINAL WHERE realty_id = 1 AND reading_date = '2026-10-01'"  # 1
```

Старая строка остаётся в таблице, корректировка имеет более поздний `created_at` и побеждает в `FINAL`.

### Замена счётчика (VersionedCollapsingMergeTree)

```bash
curl -X POST localhost:8080/realty/1/gauges -d '{"gauge_id": 1001}'
$CH "SELECT gauge_id, sign, version FROM fact_gauge_bindings WHERE realty_id = 1 ORDER BY version, sign"
# 1 +1 v1, 1 -1 v1, 1001 +1 v2
$CH "SELECT gauge_id, version FROM fact_gauge_bindings FINAL WHERE realty_id = 1"
# 1001 v2: пара +1/-1 версии v1 схлопнулась
```

Строка аннулирования и новая установка пишутся одним `INSERT`, поэтому аннулирование никогда не сохранится без
новой установки. Между чтением активной установки и вставкой нет транзакции: два одновременных запроса на один
объект могут оба аннулировать одну и ту же версию.

### Ошибочная строка с лишним нулём (DELETE)

```bash
$CH "INSERT INTO fact_invoice SELECT realty_id, user_id, tariff_id, gauge_id, day_kw * 10, night_kw, price * 10, deadline, created_at
     FROM fact_invoice WHERE realty_id = 1 ORDER BY created_at DESC LIMIT 1"
$CH "SELECT count(), max(price) FROM fact_invoice WHERE realty_id = 1"   # 37 счетов, максимальная цена около 1 900 000
$CH "DELETE FROM fact_invoice WHERE realty_id = 1 AND price >= 1000000"
$CH "SELECT count(), max(price) FROM fact_invoice WHERE realty_id = 1"   # снова 36, максимальная цена меньше 200 000
```

Обычные счета объекта 1 меньше 200 000 копеек, поэтому `price >= 1000000` находит только ошибочную строку.
Лёгкий `DELETE` сразу помечает строку удалённой (она пропадает из запросов), а физически удаляет её при
последующих слияниях.
