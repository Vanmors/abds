package com.vanmors.abds.model;

import java.time.LocalDate;

public record NewReading(long userId, long gaugeId, long tariffId, LocalDate readingDate, long dayKw, long nightKw) {
}
