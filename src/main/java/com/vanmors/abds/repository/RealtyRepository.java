package com.vanmors.abds.repository;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.vanmors.abds.model.Realty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RealtyRepository {

    private static final String FIND_BY_ID = """
        SELECT address, square, type, number, rooms, floor, porch
        FROM dim_realty
        WHERE id = {id:UInt64}
        LIMIT 1
    """;

    private final Client client;
    public RealtyRepository(final Client client) {
        this.client = client;
    }

    public Optional<Realty> findById(final long id) {
        final List<GenericRecord> rows = client.queryAll(FIND_BY_ID, Map.of("id", id));

        return rows.stream().findFirst().map(row -> new Realty(
                id,
                row.getString("address"),
                row.getInteger("square"),
                row.getString("type"),
                row.getString("number"),
                row.getInteger("rooms"),
                row.getInteger("floor"),
                row.getInteger("porch")));
    }
}
