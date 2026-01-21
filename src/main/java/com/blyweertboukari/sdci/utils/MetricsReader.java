package com.blyweertboukari.sdci.utils;

import com.blyweertboukari.sdci.enums.Metric;
import com.blyweertboukari.sdci.enums.Target;
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
    private static final HttpClient client = HttpClient.newHttpClient();
    private static String prometheusUrl;

    public static MetricsReader getInstance() {
        return instance;
    }

    public static void setPrometheusUrl(String url) {
        prometheusUrl = url;
    }

    public String getMetric(Target target, Metric metric) throws IOException, InterruptedException {
        String query = switch (metric) {
            case LATENCY_MS -> String.format(
                    "label_join(histogram_quantile(0.99, sum by (le, destination_workload, destination_workload_namespace, pod) (irate(istio_request_duration_milliseconds_bucket{reporter=~\"destination\",destination_workload_namespace=\"%s\",destination_workload=\"%s\"}[30s]))), \"destination_workload_var\", \".\", \"destination_workload\", \"destination_workload_namespace\")",
                    target.namespace,
                    target.deploymentName
            );
        };

        return queryPrometheus(query);
    }

    private String queryPrometheus(String query) throws IOException, InterruptedException {
        HttpResponse<String> response;

        String requestUrl = prometheusUrl + "/api/v1/query?query=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);

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
