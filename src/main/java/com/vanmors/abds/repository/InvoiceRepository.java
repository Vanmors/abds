package com.vanmors.abds.repository;

import com.clickhouse.client.api.Client;
import com.vanmors.abds.model.Invoice;

import java.util.List;
import java.util.Map;

public class InvoiceRepository {

    final static private String FIND_BY_REALTY_ID = """
            SELECT tariff_id, gauge_id, day_kw, night_kw,
                   price, toString(deadline) AS deadline_text, toString(created_at) AS created_at_text
            FROM fact_invoice
            WHERE realty_id = {realty_id:UInt64}
            ORDER BY created_at DESC
            LIMIT {limit:UInt64} OFFSET {offset:UInt64}
            """;

    final static private String COUNT_BY_REALTY_ID = """
            SELECT count() AS total
            FROM fact_invoice
            WHERE realty_id = {realty_id:UInt64}
            """;

    private final Client client;

    public InvoiceRepository(final Client client) {
        this.client = client;
    }

    public List<Invoice> findPage(final long realtyId, final long limit, final long offset) {
        return client.queryAll(FIND_BY_REALTY_ID, Map.of("realty_id", realtyId, "limit", limit, "offset", offset))
                .stream()
                .map(row -> new Invoice(
                        row.getLong("tariff_id"),
                        row.getLong("gauge_id"),
                        row.getLong("day_kw"),
                        row.getLong("night_kw"),
                        row.getLong("price"),
                        row.getString("deadline_text"),
                        row.getString("created_at_text")))
                .toList();
    }

    public long countByRealty(final long realtyId) {
        return client.queryAll(COUNT_BY_REALTY_ID, Map.of("realty_id", realtyId)).getFirst().getLong("total");
    }
}
