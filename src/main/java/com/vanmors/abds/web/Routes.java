package com.vanmors.abds.web;

import com.vanmors.abds.controller.RealtyController;
import com.vanmors.abds.controller.TariffController;
import com.vanmors.abds.controller.UserController;
import io.javalin.config.RoutesConfig;

public final class Routes {

    private Routes() {
    }

    public static void register(final RoutesConfig routes, final UserController users, final RealtyController realty,
                                final TariffController tariffs) {
        routes.get("/ping", ctx -> ctx.status(200));

        routes.get("/users/{id}", users::get);
        routes.patch("/users/{id}", users::patch);
        routes.get("/realty/{id}", realty::get);
        routes.get("/realty/{id}/readings", realty::getReadings);
        routes.post("/realty/{id}/readings", realty::addReading);
        routes.get("/realty/{id}/invoices", realty::getInvoices);
        routes.post("/realty/{id}/gauges", realty::bindGauge);
        routes.get("/tariffs", tariffs::getValidAt);
    }
}
