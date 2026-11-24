# Сценарии SCD

Ниже `CH` это `docker compose exec -T clickhouse clickhouse-client --port 9000 -d energosbyt --query`
(запускать из корня репозитория, после `make run` и `make setup`).

## Собственник сменил фамилию (SCD 1)

```bash
curl -X PATCH localhost:8080/users/1 -d '{"fio": "Петрова Анна Сергеевна"}'
curl localhost:8080/users/1
$CH "SELECT id, fio, updated_at FROM dim_users WHERE id = 1"        # старая и новая строка до слияния
$CH "SELECT id, fio, updated_at FROM dim_users FINAL WHERE id = 1"  # только новое ФИО
$CH "OPTIMIZE TABLE dim_users FINAL"                                 # принудительное слияние: старое ФИО удалено физически
```

`fio` перезаписывается, предыдущее значение нигде не сохраняется. Старая строка физически существует только
до ближайшего слияния.

## Собственник сменил телефон дважды (SCD 3)

```bash
curl -X PATCH localhost:8080/users/1 -d '{"phone": "+79111111111"}'
curl -X PATCH localhost:8080/users/1 -d '{"phone": "+79222222222"}'
curl localhost:8080/users/1   # phone +79222222222, phone_prev +79111111111
$CH "SELECT phone, phone_prev, updated_at FROM dim_users WHERE id = 1 ORDER BY updated_at"
```

Хранится только одно предыдущее значение. Каждое изменение пишет `updated_at = greatest(now(), updated_at + 1)`,
поэтому два изменения в одну секунду всё равно получают разные версии.

## Тариф изменился с 1 ноября (SCD 2)

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

## Пересчёт счёта за старый месяц (SCD 2)

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

## Корректировка показания (ReplacingMergeTree)

```bash
curl -X POST localhost:8080/realty/1/readings \
  -d '{"user_id":1,"gauge_id":1,"tariff_id":1,"reading_date":"2026-10-01","day_kw":99999,"night_kw":300}'
curl 'localhost:8080/realty/1/readings?date_from=2026-10-01&date_to=2026-10-01'   # одна строка, day_kw 99999
$CH "SELECT count() FROM fact_meter_readings WHERE realty_id = 1 AND reading_date = '2026-10-01'"        # 2+
$CH "SELECT count() FROM fact_meter_readings FINAL WHERE realty_id = 1 AND reading_date = '2026-10-01'"  # 1
```

Старая строка остаётся в таблице, корректировка имеет более поздний `created_at` и побеждает в `FINAL`.

## Замена счётчика (VersionedCollapsingMergeTree)

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

## Ошибочная строка с лишним нулём (DELETE)

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
