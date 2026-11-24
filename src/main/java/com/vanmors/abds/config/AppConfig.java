package com.vanmors.abds.config;

public record AppConfig(int appPort, String clickhouseHost, int clickhouseHttpPort,
                        String clickhouseUser, String clickhousePassword, String clickhouseDatabase) {

    public static AppConfig fromEnv() {
        return new AppConfig(
                Integer.parseInt(required("APP_PORT")),
                required("CLICKHOUSE_HOST"),
                Integer.parseInt(required("CLICKHOUSE_HTTP_PORT")),
                required("CLICKHOUSE_USER"),
                System.getenv().getOrDefault("CLICKHOUSE_PASSWORD", ""),
                required("CLICKHOUSE_DATABASE"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
