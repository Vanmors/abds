package com.vanmors.abds.config;

import com.clickhouse.client.api.Client;

public final class ClickHouseFactory {

    private ClickHouseFactory() {
    }

    public static Client create(AppConfig config) {
        return new Client.Builder()
                .addEndpoint("http://" + config.clickhouseHost() + ":" + config.clickhouseHttpPort())
                .setUsername(config.clickhouseUser())
                .setPassword(config.clickhousePassword())
                .setDefaultDatabase(config.clickhouseDatabase())
                .build();
    }
}
