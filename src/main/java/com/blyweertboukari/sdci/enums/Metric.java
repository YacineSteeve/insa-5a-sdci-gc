package com.blyweertboukari.sdci.enums;

public enum Metric {
    LATENCY_MS("LAT");

    public final String tableName;

    Metric(String tableName) {
        this.tableName = tableName;
    }
}
