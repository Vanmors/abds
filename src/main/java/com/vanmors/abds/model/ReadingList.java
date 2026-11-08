package com.vanmors.abds.model;

import java.util.List;

public record ReadingList(List<Reading> readings, int count) {

    public static ReadingList of(final List<Reading> readings) {
        return new ReadingList(readings, readings.size());
    }
}
