package com.vanmors.abds.repository;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.vanmors.abds.model.GaugeBinding;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GaugeBindingsRepository {

    private final Client client;

    private static final String INSERT = """
            INSERT INTO fact_gauge_bindings (gauge_id, realty_id, created_at, sign, version)
            SELECT {gauge_id:UInt64}, {realty_id:UInt64}, now(), {sign:Int8}, {version:UInt64}
            """;

    private static final String INSERT_PAIR = """
            INSERT INTO fact_gauge_bindings (gauge_id, realty_id, created_at, sign, version)
            SELECT {cancel_gauge_id:UInt64}, {cancel_realty_id:UInt64}, now(), {cancel_sign:Int8}, {cancel_version:UInt64}
            UNION ALL
            SELECT {new_gauge_id:UInt64}, {new_realty_id:UInt64}, now(), {new_sign:Int8}, {new_version:UInt64}
            """;

    private static final String FIND_ACTIVE = """
            SELECT gauge_id, version
            FROM fact_gauge_bindings
            WHERE realty_id = {realty_id:UInt64}
            GROUP BY gauge_id, version
            HAVING sum(sign) > 0
            ORDER BY version DESC
            LIMIT 1
            """;

    public GaugeBindingsRepository(final Client client) {
        this.client = client;
    }

    public void insert(final GaugeBinding gaugeBinding) {
        ClickHouseCommands.execute(client, INSERT, Map.of(
                "gauge_id", gaugeBinding.gauge_id(),
                "realty_id", gaugeBinding.realty_id(),
                "sign", gaugeBinding.sign(),
                "version", gaugeBinding.version()
        ));
    }

    public void insertPair(final GaugeBinding cancel, final GaugeBinding installation) {
        ClickHouseCommands.execute(client, INSERT_PAIR, Map.of(
                "cancel_gauge_id", cancel.gauge_id(),
                "cancel_realty_id", cancel.realty_id(),
                "cancel_sign", cancel.sign(),
                "cancel_version", cancel.version(),
                "new_gauge_id", installation.gauge_id(),
                "new_realty_id", installation.realty_id(),
                "new_sign", installation.sign(),
                "new_version", installation.version()
        ));
    }

    public Optional<GaugeBinding> findActive(final long realtyId) {
        final List<GenericRecord> rows = client.queryAll(FIND_ACTIVE, Map.of("realty_id", realtyId));
        return rows.stream().findFirst().map(row -> new GaugeBinding(
                row.getLong("gauge_id"),
                realtyId,
                (byte) 1,
                row.getInteger("version")));
    }

}
