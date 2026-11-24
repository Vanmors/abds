package com.vanmors.abds.repository;

import com.clickhouse.client.api.Client;

import java.util.Map;

public class GaugeRepository {

    private static final String EXISTS_BY_ID = """
            SELECT 1
            FROM dim_gauges
            WHERE id = {id:UInt64}
            LIMIT 1
            """;


    private final Client client;

    public GaugeRepository(final Client client) {
        this.client = client;
    }

    public boolean existsById(final long id) {
        return !client.queryAll(EXISTS_BY_ID, Map.of("id", id)).isEmpty();
    }


}
