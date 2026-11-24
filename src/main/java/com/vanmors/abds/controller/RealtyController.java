package com.vanmors.abds.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.vanmors.abds.exception.BadRequestException;
import com.vanmors.abds.model.NewReading;
import com.vanmors.abds.service.GaugeBindingsService;
import com.vanmors.abds.service.InvoiceService;
import com.vanmors.abds.service.RealtyService;
import com.vanmors.abds.web.Requests;
import io.javalin.http.Context;

import java.time.LocalDate;

public class RealtyController {

    private static final long DEFAULT_LIMIT = 10;
    private static final long DEFAULT_OFFSET = 0;

    private final RealtyService service;
    private final GaugeBindingsService gaugeBindingsService;
    private final InvoiceService invoiceService;

    public RealtyController(final RealtyService realtyService, final GaugeBindingsService gaugeBindingsService,
                            final InvoiceService invoiceService) {
        this.service = realtyService;
        this.gaugeBindingsService = gaugeBindingsService;
        this.invoiceService = invoiceService;
    }

    public void get(final Context ctx) {
        ctx.json(service.get(Requests.pathId(ctx)));
    }

    public void getReadings(final Context ctx) {
        final long id = Requests.pathId(ctx);
        final LocalDate dateFrom = Requests.requiredDateParam(ctx, "date_from");
        final LocalDate dateTo = Requests.requiredDateParam(ctx, "date_to");
        if (dateFrom.isAfter(dateTo)) {
            throw BadRequestException.invalidParameter("date_from");
        }
        ctx.json(service.getReadings(id, dateFrom, dateTo));
    }

    public void addReading(final Context ctx) {
        final long id = Requests.pathId(ctx);
        service.addReading(id, parseReading(Requests.jsonObject(ctx)));
        ctx.status(201);
    }

    public void getInvoices(final Context ctx) {
        final long id = Requests.pathId(ctx);
        final long limit = Requests.optionalNonNegativeParam(ctx, "limit", DEFAULT_LIMIT);
        final long offset = Requests.optionalNonNegativeParam(ctx, "offset", DEFAULT_OFFSET);
        ctx.json(invoiceService.getPage(id, limit, offset));
    }

    public void bindGauge(final Context ctx) {
        final long id = Requests.pathId(ctx);
        final long gaugeId = Requests.requiredId(Requests.jsonObject(ctx), "gauge_id");
        gaugeBindingsService.bind(id, gaugeId);
        ctx.status(201);
    }

    private static NewReading parseReading(final JsonNode node) {
        return new NewReading(
                Requests.requiredId(node, "user_id"),
                Requests.requiredId(node, "gauge_id"),
                Requests.requiredId(node, "tariff_id"),
                Requests.requiredDate(node, "reading_date"),
                Requests.requiredUInt32(node, "day_kw"),
                Requests.requiredUInt32(node, "night_kw"));
    }


}
