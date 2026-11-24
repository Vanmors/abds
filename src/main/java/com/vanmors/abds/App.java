package com.vanmors.abds;

import com.clickhouse.client.api.Client;
import com.vanmors.abds.config.AppConfig;
import com.vanmors.abds.config.ClickHouseFactory;
import com.vanmors.abds.controller.RealtyController;
import com.vanmors.abds.controller.TariffController;
import com.vanmors.abds.controller.UserController;
import com.vanmors.abds.repository.*;
import com.vanmors.abds.service.GaugeBindingsService;
import com.vanmors.abds.service.InvoiceService;
import com.vanmors.abds.service.RealtyService;
import com.vanmors.abds.service.TariffService;
import com.vanmors.abds.service.UserService;
import com.vanmors.abds.web.ErrorHandlers;
import com.vanmors.abds.web.JsonMapperFactory;
import com.vanmors.abds.web.Routes;
import io.javalin.Javalin;

public final class App {

    private final Javalin javalin;
    private final int port;

    private App(final Javalin javalin, final int port) {
        this.javalin = javalin;
        this.port = port;
    }

    public static App create(final AppConfig config) {
        final Client client = ClickHouseFactory.create(config);

        final UserController users = new UserController(new UserService(new UserRepository(client)));
        final TariffRepository tariffRepository = new TariffRepository(client);
        final TariffController tariffs = new TariffController(new TariffService(tariffRepository));
        final RealtyRepository realtyRepository = new RealtyRepository(client);
        final GaugeRepository gaugeRepository = new GaugeRepository(client);
        final RealtyController realty = new RealtyController(
                new RealtyService(realtyRepository, new ReadingRepository(client),
                        tariffRepository, gaugeRepository, new UserRepository(client)),
                new GaugeBindingsService(new GaugeBindingsRepository(client), realtyRepository, gaugeRepository),
                new InvoiceService(new InvoiceRepository(client), realtyRepository));

        final Javalin javalin = Javalin.create(c -> {
            c.concurrency.useVirtualThreads = true;
            c.jsonMapper(JsonMapperFactory.create());
            Routes.register(c.routes, users, realty, tariffs);
            ErrorHandlers.register(c.routes);
            c.events.serverStopped(client::close);
        });
        return new App(javalin, config.appPort());
    }

    public void start() {
        javalin.start(port);
    }
}
