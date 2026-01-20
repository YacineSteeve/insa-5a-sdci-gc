package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import com.blyweertboukari.sdci.enums.Metric;
import com.blyweertboukari.sdci.utils.MetricsReader;
import com.blyweertboukari.sdci.enums.Target;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Monitor {
    private static final Monitor instance = new Monitor();
    private static final Logger logger = LogManager.getLogger(Monitor.class);
    public final Map<Target, Map<Metric, Knowledge.Symptom>> currentSymptom = new ConcurrentHashMap<>();

    private Monitor() {
        currentSymptom.put(Target.GATEWAY, Map.of(
                Metric.LATENCY_MS, Knowledge.Symptom.GATEWAY_NA,
                Metric.REQUESTS_PER_SECOND, Knowledge.Symptom.GATEWAY_NA
        ));
        currentSymptom.put(Target.SERVER, Map.of(
                Metric.LATENCY_MS, Knowledge.Symptom.SERVER_NA,
                Metric.REQUESTS_PER_SECOND, Knowledge.Symptom.SERVER_NA
        ));
    }

    public static Monitor getInstance() {
        return instance;
    }

    public void start() {
        logger.info("Start Monitoring");
        symptomGenerator();
    }

    private void symptomGenerator() {
        while (Main.run.get()) {
            try {
                Thread.sleep(Knowledge.MONITORING_INTERVAL_MS);
            } catch (InterruptedException e) {
                logger.error("Symptom generator sleep error: ", e);
            }

            Map<Target, Map<Metric, Knowledge.Symptom>> symptom = new HashMap<>();

            Map<Metric, Knowledge.Symptom> gatewayMap = new HashMap<>();
            gatewayMap.put(Metric.LATENCY_MS, Knowledge.Symptom.GATEWAY_NA);
            gatewayMap.put(Metric.REQUESTS_PER_SECOND, Knowledge.Symptom.GATEWAY_NA);

            Map<Metric, Knowledge.Symptom> serverMap = new HashMap<>();
            serverMap.put(Metric.LATENCY_MS, Knowledge.Symptom.SERVER_NA);
            serverMap.put(Metric.REQUESTS_PER_SECOND, Knowledge.Symptom.SERVER_NA);

            symptom.put(Target.GATEWAY, gatewayMap);
            symptom.put(Target.SERVER, serverMap);

            for (Target target : Target.values()) {
                Knowledge.Symptom targetSymptom = switch (target) {
                    case GATEWAY -> Knowledge.Symptom.GATEWAY_OK;
                    case SERVER -> Knowledge.Symptom.SERVER_OK;
                };
                for (Metric metric : Metric.values()) {
                    try {
                        double value = getData(target, metric);
                        Knowledge.Symptom metricSymptom = switch (target) {
                            case GATEWAY -> switch (metric) {
                                case REQUESTS_PER_SECOND -> value > Knowledge.GATEWAY_RPS_THRESHOLD
                                        ? Knowledge.Symptom.GATEWAY_NOK
                                        : Knowledge.Symptom.GATEWAY_OK;
                                case LATENCY_MS -> value > Knowledge.GATEWAY_LATENCY_THRESHOLD
                                        ? Knowledge.Symptom.GATEWAY_NOK
                                        : Knowledge.Symptom.GATEWAY_OK;
                            };
                            case SERVER -> switch (metric) {
                                case REQUESTS_PER_SECOND -> value > Knowledge.SERVER_RPS_THRESHOLD
                                        ? Knowledge.Symptom.SERVER_NOK
                                        : Knowledge.Symptom.SERVER_OK;
                                case LATENCY_MS -> value > Knowledge.SERVER_LATENCY_THRESHOLD
                                        ? Knowledge.Symptom.SERVER_NOK
                                        : Knowledge.Symptom.SERVER_OK;
                            };
                        };
                        Knowledge.getInstance().addValue(target, metric, value);
                        if ((metricSymptom == Knowledge.Symptom.GATEWAY_NOK
                                || metricSymptom == Knowledge.Symptom.SERVER_NOK)
                                && metricSymptom != targetSymptom) {
                            targetSymptom = metricSymptom;
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Received non-numeric data for {} on {}: {}", metric, target, e.getMessage());
                        targetSymptom = switch (target) {
                            case GATEWAY -> Knowledge.Symptom.GATEWAY_NA;
                            case SERVER -> Knowledge.Symptom.SERVER_NA;
                        };
                    } catch (IOException | InterruptedException e) {
                        logger.error("Error while getting {} for {}: ", metric, target, e);
                        targetSymptom = switch (target) {
                            case GATEWAY -> Knowledge.Symptom.GATEWAY_NA;
                            case SERVER -> Knowledge.Symptom.SERVER_NA;
                        };
                    }

                    logger.info("Computed symptom {} on metric {} for target {}", targetSymptom, metric, target);
                    symptom.get(target).put(metric, targetSymptom);
                }
            }
            updateSymptom(symptom);
        }
    }

    private Double getData(Target target, Metric metric) throws IOException, InterruptedException, NumberFormatException {
        String readValue = MetricsReader.getInstance().getMetric(target, metric);
        return Double.parseDouble(readValue);
    }

    private void updateSymptom(Map<Target, Map<Metric, Knowledge.Symptom>> symptom) {
        synchronized (currentSymptom) {
            this.currentSymptom.putAll(symptom);
            currentSymptom.notifyAll();
        }
    }
}

