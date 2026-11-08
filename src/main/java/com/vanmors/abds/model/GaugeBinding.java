package com.vanmors.abds.model;

public record GaugeBinding(long gauge_id, long realty_id, byte sign, int version) {
}
