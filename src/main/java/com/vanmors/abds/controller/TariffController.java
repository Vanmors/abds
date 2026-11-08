package com.vanmors.abds.controller;

import com.vanmors.abds.service.TariffService;
import com.vanmors.abds.web.Requests;
import io.javalin.http.Context;

public class TariffController {

    private final TariffService service;

    public TariffController(final TariffService service) {
        this.service = service;
    }

    public void getValidAt(final Context ctx) {
        ctx.json(service.getValidAt(Requests.requiredDateParam(ctx, "date")));
    }
}
