package com.vanmors.abds.model;

public record Tariff(long id, String name, long priceDayKwph, long priceNightKwph, String validFrom, String validTo) {
}
