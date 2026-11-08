package com.vanmors.abds.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanmors.abds.exception.BadRequestException;
import com.vanmors.abds.model.UserPatch;
import com.vanmors.abds.service.UserService;
import com.vanmors.abds.web.Requests;
import io.javalin.http.Context;

import java.util.regex.Pattern;

public class UserController {

    private static final int FIO_MAX_LENGTH = 255;
    private static final Pattern PHONE = Pattern.compile("\\+?\\d{10,15}");

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    public void get(Context ctx) {
        ctx.json(service.get(Requests.pathId(ctx)));
    }

    public void patch(Context ctx) {
        long id = Requests.pathId(ctx);
        service.update(id, parsePatch(Requests.jsonObject(ctx)));
        ctx.status(204);
    }

    private static UserPatch parsePatch(JsonNode body) {
        String fio = Requests.optionalText(body, "fio");
        if (fio != null && fio.length() > FIO_MAX_LENGTH) {
            throw BadRequestException.invalidField("fio");
        }
        String phone = Requests.optionalText(body, "phone");
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw BadRequestException.invalidField("phone");
        }
        if (fio == null && phone == null) {
            throw new BadRequestException("\"fio\" or \"phone\" field is required");
        }
        return new UserPatch(fio, phone);
    }
}
