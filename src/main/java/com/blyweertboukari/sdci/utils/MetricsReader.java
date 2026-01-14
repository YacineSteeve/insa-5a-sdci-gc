package com.blyweertboukari.sdci.utils;

import com.blyweertboukari.sdci.enums.Metric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MetricsReader {
    private static final MetricsReader instance = new MetricsReader();
    private static final String PROMETHEUS_URL = "http://localhost:9090/api/v1/query";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static MetricsReader getInstance() {
        return instance;
    }

    public String getMetric(Metric metric) throws IOException, InterruptedException {
        String query = switch (metric) {
            case LATENCY_MS -> "histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket[5m])) by (le))";
            case REQUESTS_PER_SECOND -> "round(sum (rate(istio_requests_total{reporter=~\"source|waypoint\"}[5m])), 0.01)";
        };

        return queryPrometheus(query);
    }

    private String queryPrometheus(String query) throws IOException, InterruptedException {
        HttpResponse<String> response;

        String requestUrl = PROMETHEUS_URL + "?query=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to query Prometheus: " + response.statusCode());
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode results = root.path("data").path("result");
        String value = "";

        if (results.isArray()) {
            for (JsonNode result : results) {
                value = result.path("value").get(1).asText();
            }
        } else {
            value = results.path("value").get(1).asText();
        }

        return value;
    }
}
