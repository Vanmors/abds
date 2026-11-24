package com.vanmors.abds.model;

public record Invoice(long tariff_id, long gauge_id, long day_kw, long night_kw, long price, String deadline,
                      String created_at) {
}
