package com.vanmors.abds.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanmors.abds.exception.BadRequestException;
import com.vanmors.abds.exception.NotFoundException;
import io.javalin.http.Context;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public final class Requests {

    private static final long UINT32_MAX = 4_294_967_295L;
    private static final LocalDate CLICKHOUSE_DATE_MIN = LocalDate.of(1970, 1, 1);
    private static final LocalDate CLICKHOUSE_DATE_MAX = LocalDate.of(2149, 6, 6);

    private Requests() {
    }

    public static long pathId(Context ctx) {
        try {
            long id = Long.parseLong(ctx.pathParam("id"));
            if (id <= 0) {
                throw new NotFoundException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new NotFoundException();
        }
    }

    public static JsonNode jsonObject(Context ctx) {
        JsonNode body;
        try {
            body = ctx.bodyAsClass(JsonNode.class);
        } catch (Exception e) {
            throw new BadRequestException("invalid request body");
        }
        if (body == null || !body.isObject()) {
            throw new BadRequestException("invalid request body");
        }
        return body;
    }

    public static long requiredId(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            throw BadRequestException.invalidField(field);
        }
        return value.asLong();
    }

    public static long requiredUInt32(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.asLong() < 0 || value.asLong() > UINT32_MAX) {
            throw BadRequestException.invalidField(field);
        }
        return value.asLong();
    }

    public static LocalDate requiredDate(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) {
            throw BadRequestException.invalidField(field);
        }
        return parseDate(value.asText()).orElseThrow(() -> BadRequestException.invalidField(field));
    }

    public static LocalDate requiredDateParam(Context ctx, String parameter) {
        String value = ctx.queryParam(parameter);
        if (value == null) {
            throw BadRequestException.invalidParameter(parameter);
        }
        return parseDate(value).orElseThrow(() -> BadRequestException.invalidParameter(parameter));
    }

    public static long optionalNonNegativeParam(Context ctx, String parameter, long defaultValue) {
        String value = ctx.queryParam(parameter);
        if (value == null) {
            return defaultValue;
        }
        try {
            long number = Long.parseLong(value);
            if (number < 0) {
                throw BadRequestException.invalidParameter(parameter);
            }
            return number;
        } catch (NumberFormatException e) {
            throw BadRequestException.invalidParameter(parameter);
        }
    }

    private static Optional<LocalDate> parseDate(String value) {
        LocalDate date;
        try {
            date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        if (date.isBefore(CLICKHOUSE_DATE_MIN) || date.isAfter(CLICKHOUSE_DATE_MAX)) {
            return Optional.empty();
        }
        return Optional.of(date);
    }

    public static String optionalText(JsonNode body, String field) {
        if (!body.has(field)) {
            return null;
        }
        JsonNode value = body.get(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw BadRequestException.invalidField(field);
        }
        return value.asText().strip();
    }
}
