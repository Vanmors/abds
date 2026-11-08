package com.vanmors.abds.repository;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.vanmors.abds.model.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UserRepository {

    private static final String FIND_BY_ID = """
            SELECT fio, phone, phone_prev
            FROM dim_users FINAL
            WHERE id = {id:UInt64}
            """;

    // updated_at + 1 keeps versions distinct when two updates arrive within the same second.
    private static final String SAVE = """
            INSERT INTO dim_users (id, fio, phone, phone_prev, created_at, updated_at)
            SELECT id, {fio:String}, {phone:String}, {phone_prev:String}, created_at, greatest(now(), updated_at + 1)
            FROM dim_users FINAL
            WHERE id = {id:UInt64}
            """;

    private static final String EXISTS_BY_ID = """
            SELECT 1
            FROM dim_users
            WHERE id = {id:UInt64}
            LIMIT 1
            """;

    private final Client client;

    public UserRepository(Client client) {
        this.client = client;
    }

    public boolean existsById(final long id) {
        return !client.queryAll(EXISTS_BY_ID, Map.of("id", id)).isEmpty();
    }

    public Optional<User> findById(long id) {
        List<GenericRecord> rows = client.queryAll(FIND_BY_ID, Map.of("id", id));
        return rows.stream().findFirst().map(row -> new User(
                id,
                row.getString("fio"),
                row.getString("phone"),
                row.getString("phone_prev")));
    }

    public void save(User user) {
        ClickHouseCommands.execute(client, SAVE, Map.of(
                "id", user.id(),
                "fio", user.fio(),
                "phone", user.phone(),
                "phone_prev", user.phonePrev()));
    }
}
