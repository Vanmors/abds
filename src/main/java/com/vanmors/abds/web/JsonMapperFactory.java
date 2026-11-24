package com.vanmors.abds.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.javalin.json.JavalinJackson;
import io.javalin.json.JsonMapper;

public final class JsonMapperFactory {

    private JsonMapperFactory() {
    }

    public static JsonMapper create() {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new JavalinJackson(mapper, false);
    }
}
