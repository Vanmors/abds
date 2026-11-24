package com.vanmors.abds.web;

import com.vanmors.abds.exception.BadRequestException;
import com.vanmors.abds.exception.NotFoundException;
import io.javalin.config.RoutesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class ErrorHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandlers.class);

    private ErrorHandlers() {
    }

    public static void register(RoutesConfig routes) {
        routes.exception(BadRequestException.class, (e, ctx) ->
                ctx.status(400).json(message(e.getMessage())));
        routes.exception(NotFoundException.class, (e, ctx) ->
                ctx.status(404).json(message(e.getMessage())));
        routes.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unhandled error on {} {}", ctx.method(), ctx.path(), e);
            ctx.status(500).json(message("Internal server error"));
        });
        routes.error(404, ctx -> ctx.json(message("Not found")));
    }

    private static Map<String, String> message(String text) {
        return Map.of("message", text);
    }
}
