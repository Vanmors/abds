package com.vanmors.abds.repository;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandResponse;

import java.util.Map;
import java.util.concurrent.ExecutionException;

final class ClickHouseCommands {

    private ClickHouseCommands() {
    }

    static void execute(Client client, String sql, Map<String, Object> params) {
        try (CommandResponse ignored = client.execute(sql, params).get()) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ClickHouse command interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("ClickHouse command failed", e.getCause());
        } catch (Exception e) {
            throw new IllegalStateException("ClickHouse command failed", e);
        }
    }
}
