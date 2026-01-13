package com.blyweertboukari.sdci.utils;

public enum Metric {
    LATENCY_MS("LAT"),
    REQUESTS_PER_SECOND("RPS");

    public final String tableName;

    Metric(String tableName) {
        this.tableName = tableName;
    }
}
