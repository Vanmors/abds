package com.vanmors.abds.model;

public record Reading(String readingDate, long userId, long tariffId, long gaugeId, long dayKw, long nightKw) {
}
