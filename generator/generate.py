#!/usr/bin/env python3
"""Generates CSV datasets for the Energosbyt lab into the output directory.

Uses only the standard library. The output is deterministic (fixed seed),
so reloading the same files into ReplacingMergeTree tables does not create
new versions of rows.
"""

import argparse
import calendar
import csv
import os
import random
import time
from datetime import date, datetime, timedelta

SEED = 42

PERIOD_START = date(2023, 11, 1)
PERIOD_END = date(2026, 10, 31)

HOUSES = [
    "г. Москва, ул. Ленина, д. 1",
    "г. Москва, ул. Гагарина, д. 15",
    "г. Москва, пр-т Мира, д. 42",
    "г. Москва, ул. Садовая, д. 7",
    "г. Москва, ул. Пушкина, д. 10",
]
UNITS_PER_HOUSE = 200
NRCP_PER_HOUSE = 10
PORCHES = 4
FLATS_PER_FLOOR = 4
REALTY_COUNT = len(HOUSES) * UNITS_PER_HOUSE

GAUGE_REPLACEMENT_SHARE = 0.05
CORRECTION_SHARE = 0.03

GAUGE_MODELS = [
    ("Меркурий 200", "Россия"),
    ("Энергомера CE102", "Россия"),
    ("Нева МТ 124", "Россия"),
    ("Милур 107", "Россия"),
    ("Itron ACE 1000", "Германия"),
    ("Landis+Gyr E350", "Швейцария"),
]

TARIFF_ID = 1
TARIFF_NAME = "День-Ночь"
OPEN_VALID_TO = datetime(2099, 1, 1)
# (valid_from, price_day_kwph, price_night_kwph), prices in kopecks.
TARIFF_VERSIONS = [
    (datetime(2023, 1, 1), 520, 260),
    (datetime(2024, 7, 1), 560, 280),
    (datetime(2025, 11, 1), 620, 310),
]

LAST_NAMES = ["Иванов", "Петров", "Сидоров", "Смирнов", "Кузнецов", "Попов",
              "Васильев", "Соколов", "Михайлов", "Новиков", "Фёдоров", "Морозов",
              "Волков", "Алексеев", "Лебедев", "Семёнов", "Егоров", "Павлов"]
MALE_NAMES = ["Иван", "Пётр", "Алексей", "Дмитрий", "Сергей", "Андрей",
              "Михаил", "Николай", "Владимир", "Артём"]
FEMALE_NAMES = ["Анна", "Мария", "Елена", "Ольга", "Татьяна", "Наталья",
                "Ирина", "Светлана", "Екатерина", "Юлия"]
PATRONYMIC_ROOTS = ["Иванович", "Петрович", "Алексеевич", "Сергеевич",
                    "Андреевич", "Михайлович", "Николаевич", "Владимирович"]


def fmt_dt(value):
    return value.strftime("%Y-%m-%d %H:%M:%S")


def month_last_day(year, month):
    return date(year, month, calendar.monthrange(year, month)[1])


def next_month_10th(day):
    year, month = (day.year + 1, 1) if day.month == 12 else (day.year, day.month + 1)
    return date(year, month, 10)


def tariff_price_at(moment):
    """Returns (price_day, price_night) of the tariff version valid at the moment (SCD 2 lookup)."""
    actual = None
    for valid_from, price_day, price_night in TARIFF_VERSIONS:
        if valid_from <= moment:
            actual = (price_day, price_night)
    if actual is None:
        raise ValueError(f"no tariff version at {moment}")
    return actual


def make_fio(rng):
    last = rng.choice(LAST_NAMES)
    patronymic = rng.choice(PATRONYMIC_ROOTS)
    if rng.random() < 0.5:
        return f"{last} {rng.choice(MALE_NAMES)} {patronymic}"
    return f"{last}а {rng.choice(FEMALE_NAMES)} {patronymic[:-2]}на"


def open_writer(out_dir, name, header):
    file = open(os.path.join(out_dir, f"{name}.csv"), "w", newline="", encoding="utf-8")
    writer = csv.writer(file, lineterminator="\n")
    writer.writerow(header)
    return file, writer


def generate_realty(rng):
    """Returns a list of realty dicts: several houses, flats and non-residential premises."""
    realty = []
    realty_id = 0
    for address in HOUSES:
        for unit in range(UNITS_PER_HOUSE):
            realty_id += 1
            created_at = datetime(2023, 10, 1) + timedelta(minutes=rng.randint(0, 20 * 24 * 60))
            if unit < NRCP_PER_HOUSE:
                rooms = rng.randint(1, 3)
                realty.append({
                    "id": realty_id, "address": address, "square": rng.randint(40, 150),
                    "type": "nrcp", "number": "", "rooms": rooms, "floor": 1,
                    "porch": unit % PORCHES + 1, "created_at": created_at,
                })
                continue
            flat = unit - NRCP_PER_HOUSE
            flats_per_porch = (UNITS_PER_HOUSE - NRCP_PER_HOUSE + PORCHES - 1) // PORCHES
            rooms = rng.randint(1, 4)
            realty.append({
                "id": realty_id, "address": address,
                "square": 18 + rooms * 14 + rng.randint(0, 12),
                "type": "flat", "number": str(flat + 1), "rooms": rooms,
                # Floor 1 is occupied by non-residential premises.
                "floor": (flat % flats_per_porch) // FLATS_PER_FLOOR + 2,
                "porch": flat // flats_per_porch + 1, "created_at": created_at,
            })
    return realty


def generate(out_dir):
    rng = random.Random(SEED)
    os.makedirs(out_dir, exist_ok=True)
    days = [PERIOD_START + timedelta(days=i) for i in range((PERIOD_END - PERIOD_START).days + 1)]
    day_strs = [d.isoformat() for d in days]

    realty = generate_realty(rng)

    file, writer = open_writer(out_dir, "dim_realty",
                               ["id", "address", "square", "type", "number", "rooms", "floor", "porch", "created_at"])
    with file:
        for r in realty:
            writer.writerow([r["id"], r["address"], r["square"], r["type"], r["number"],
                             r["rooms"], r["floor"], r["porch"], fmt_dt(r["created_at"])])

    # Users: one owner per realty, user_id == realty_id. Some owners already changed the phone once (SCD 3).
    file, writer = open_writer(out_dir, "dim_users", ["id", "fio", "phone", "phone_prev", "created_at", "updated_at"])
    with file:
        for r in realty:
            created_at = r["created_at"]
            phone = f"+7900{r['id']:07d}"
            phone_prev, updated_at = "", created_at
            if rng.random() < 0.1:
                phone_prev = phone
                phone = f"+7901{r['id']:07d}"
                updated_at = created_at + timedelta(days=rng.randint(30, 700))
            writer.writerow([r["id"], make_fio(rng), phone, phone_prev, fmt_dt(created_at), fmt_dt(updated_at)])

    # Tariff versions (SCD 2): each version is closed by the next one, the last one is open.
    file, writer = open_writer(out_dir, "dim_tariffs",
                               ["id", "name", "price_day_kwph", "price_night_kwph",
                                "valid_from", "valid_to", "created_at", "updated_at"])
    with file:
        for i, (valid_from, price_day, price_night) in enumerate(TARIFF_VERSIONS):
            valid_to = TARIFF_VERSIONS[i + 1][0] if i + 1 < len(TARIFF_VERSIONS) else OPEN_VALID_TO
            created_at = valid_from - timedelta(days=30)
            # A closed version was updated when the next version was announced.
            updated_at = valid_to - timedelta(days=30) if valid_to != OPEN_VALID_TO else created_at
            writer.writerow([TARIFF_ID, TARIFF_NAME, price_day, price_night,
                             fmt_dt(valid_from), fmt_dt(valid_to), fmt_dt(created_at), fmt_dt(updated_at)])

    # Gauge replacements: realty_id -> (replacement day, new gauge id).
    replaced_ids = sorted(rng.sample(range(1, REALTY_COUNT + 1), int(REALTY_COUNT * GAUGE_REPLACEMENT_SHARE)))
    replacement_days = (date(2024, 1, 1), date(2026, 9, 30))
    replacements = {}
    for i, realty_id in enumerate(replaced_ids):
        day = replacement_days[0] + timedelta(days=rng.randint(0, (replacement_days[1] - replacement_days[0]).days))
        replacements[realty_id] = (day, REALTY_COUNT + i + 1)

    # Gauges: the initial gauge id equals realty id, replacement gauges get ids after REALTY_COUNT.
    file, writer = open_writer(out_dir, "dim_gauges", ["id", "name", "country", "created_at"])
    with file:
        for r in realty:
            name, country = rng.choice(GAUGE_MODELS)
            writer.writerow([r["id"], name, country, fmt_dt(r["created_at"])])
        for realty_id in replaced_ids:
            day, gauge_id = replacements[realty_id]
            name, country = rng.choice(GAUGE_MODELS)
            writer.writerow([gauge_id, name, country, fmt_dt(datetime.combine(day, datetime.min.time()))])

    # Bindings: installation (+1, v1); replacement cancels it (-1, v1) and installs a new gauge (+1, v2).
    file, writer = open_writer(out_dir, "fact_gauge_bindings", ["gauge_id", "realty_id", "created_at", "sign", "version"])
    with file:
        for r in realty:
            writer.writerow([r["id"], r["id"], fmt_dt(r["created_at"]), 1, 1])
            if r["id"] in replacements:
                day, gauge_id = replacements[r["id"]]
                replaced_at = fmt_dt(datetime.combine(day, datetime.min.time()) + timedelta(hours=12))
                writer.writerow([r["id"], r["id"], replaced_at, -1, 1])
                writer.writerow([gauge_id, r["id"], replaced_at, 1, 2])

    # Readings are cumulative meter values, a new gauge starts from zero.
    # Invoices are built from the same daily consumption, so both facts are consistent.
    readings_file, readings = open_writer(out_dir, "fact_meter_readings",
                                          ["realty_id", "user_id", "tariff_id", "gauge_id", "reading_date",
                                           "day_kw", "night_kw", "created_at"])
    invoice_file, invoices = open_writer(out_dir, "fact_invoice",
                                         ["realty_id", "user_id", "tariff_id", "gauge_id", "day_kw", "night_kw",
                                          "price", "deadline", "created_at"])
    with readings_file, invoice_file:
        for r in realty:
            realty_id = r["id"]
            user_id = realty_id
            replacement_day, new_gauge_id = replacements.get(realty_id, (None, None))
            gauge_id = realty_id
            day_avg = max(2, r["square"] // 8) if r["type"] == "flat" else max(4, r["square"] // 5)
            night_avg = max(1, day_avg * 2 // 5)
            day_kw = rng.randint(1000, 20000)
            night_kw = rng.randint(500, 8000)
            month_day_kw = month_night_kw = 0
            for d, d_str in zip(days, day_strs):
                if d == replacement_day:
                    gauge_id, day_kw, night_kw = new_gauge_id, 0, 0
                day_used = rng.randint(day_avg // 2, day_avg * 3 // 2)
                night_used = rng.randint(night_avg // 2, night_avg * 3 // 2)
                day_kw += day_used
                night_kw += night_used
                month_day_kw += day_used
                month_night_kw += night_used

                submitted_at = f"{d_str} {rng.randint(18, 23):02d}:{rng.randint(0, 59):02d}:{rng.randint(0, 59):02d}"
                if rng.random() < CORRECTION_SHARE:
                    # Wrong value first, the correction with a later created_at wins in ReplacingMergeTree.
                    readings.writerow([realty_id, user_id, TARIFF_ID, gauge_id, d_str,
                                       day_kw + rng.randint(1, 500), night_kw, submitted_at])
                    corrected_at = (datetime.combine(d, datetime.min.time())
                                    + timedelta(days=1, hours=9, minutes=rng.randint(0, 600)))
                    readings.writerow([realty_id, user_id, TARIFF_ID, gauge_id, d_str,
                                       day_kw, night_kw, fmt_dt(corrected_at)])
                else:
                    readings.writerow([realty_id, user_id, TARIFF_ID, gauge_id, d_str,
                                       day_kw, night_kw, submitted_at])

                if d == month_last_day(d.year, d.month):
                    # Invoice is issued on the last day of the month, the price is taken from
                    # the tariff version valid at that moment (SCD 2).
                    issued_at = datetime.combine(d, datetime.min.time()).replace(hour=23, minute=59, second=59)
                    price_day, price_night = tariff_price_at(issued_at)
                    price = month_day_kw * price_day + month_night_kw * price_night
                    invoices.writerow([realty_id, user_id, TARIFF_ID, gauge_id, month_day_kw, month_night_kw,
                                       price, next_month_10th(d).isoformat(), fmt_dt(issued_at)])
                    month_day_kw = month_night_kw = 0


def main():
    parser = argparse.ArgumentParser(description="Generates Energosbyt CSV datasets.")
    parser.add_argument("--out", default="data", help="output directory (default: data)")
    args = parser.parse_args()
    started = time.monotonic()
    generate(args.out)
    print(f"Datasets written to {args.out}/ in {time.monotonic() - started:.1f}s")


if __name__ == "__main__":
    main()
