package com.vanmors.abds.repository;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.vanmors.abds.model.Tariff;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TariffRepository {

    private static final String EXISTS_BY_ID = """
            SELECT 1
            FROM dim_tariffs
            WHERE id = {id:UInt64}
            LIMIT 1
            """;

    private static final String FIND_VALID_AT = """
            SELECT id, name, price_day_kwph, price_night_kwph,
                   toString(valid_from) AS valid_from_text, toString(valid_to) AS valid_to_text
            FROM dim_tariffs FINAL
            WHERE valid_from <= toDateTime({date:Date}) AND toDateTime({date:Date}) < valid_to
            ORDER BY valid_from DESC
            LIMIT 1
            """;

    private final Client client;

    public TariffRepository(final Client client) {
        this.client = client;
    }

    public boolean existsById(final long id) {
        return !client.queryAll(EXISTS_BY_ID, Map.of("id", id)).isEmpty();
    }

    public Optional<Tariff> findValidAt(final LocalDate date) {
        final List<GenericRecord> rows = client.queryAll(FIND_VALID_AT, Map.of("date", date.toString()));
        return rows.stream().findFirst().map(row -> new Tariff(
                row.getLong("id"),
                row.getString("name"),
                row.getLong("price_day_kwph"),
                row.getLong("price_night_kwph"),
                row.getString("valid_from_text"),
                row.getString("valid_to_text")));
    }
}
