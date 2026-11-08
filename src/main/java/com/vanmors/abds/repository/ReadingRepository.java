package com.vanmors.abds.repository;

import com.clickhouse.client.api.Client;
import com.vanmors.abds.model.NewReading;
import com.vanmors.abds.model.Reading;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ReadingRepository {
    private final Client client;
    private static final String INSERT = """
            INSERT INTO fact_meter_readings (realty_id, user_id, tariff_id, gauge_id, reading_date, day_kw, night_kw, created_at)
            SELECT {realty_id:UInt64}, {user_id:UInt64}, {tariff_id:UInt64}, {gauge_id:UInt64},
                             {reading_date:Date}, {day_kw:UInt32}, {night_kw:UInt32}, now()
            """;

    private static final String FIND_BY_PERIOD = """
            SELECT toString(reading_date) AS reading_date_text,
                   user_id, tariff_id, gauge_id,
                   day_kw, night_kw
            FROM fact_meter_readings FINAL
            WHERE realty_id = {realty_id:UInt64}
              AND reading_date BETWEEN {date_from:Date} AND {date_to:Date}
            ORDER BY reading_date
            """;

    public ReadingRepository(final Client client) {
        this.client = client;
    }

    public void insert(final long realtyId, final NewReading reading) {
        ClickHouseCommands.execute(client, INSERT, Map.of(
                "realty_id", realtyId,
                "user_id", reading.userId(),
                "tariff_id", reading.tariffId(),
                "gauge_id", reading.gaugeId(),
                "reading_date", reading.readingDate().toString(),
                "day_kw", reading.dayKw(),
                "night_kw", reading.nightKw()
        ));
    }

    public List<Reading> findByPeriod(final long realtyId, final LocalDate dateFrom, final LocalDate dateTo) {
        return client.queryAll(FIND_BY_PERIOD, Map.of(
                        "realty_id", realtyId,
                        "date_from", dateFrom.toString(),
                        "date_to", dateTo.toString()))
                .stream()
                .map(row -> new Reading(
                        row.getString("reading_date_text"),
                        row.getLong("user_id"),
                        row.getLong("tariff_id"),
                        row.getLong("gauge_id"),
                        row.getLong("day_kw"),
                        row.getLong("night_kw")))
                .toList();
    }
}
