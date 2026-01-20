package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import com.blyweertboukari.sdci.enums.Metric;
import com.blyweertboukari.sdci.enums.Target;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Analyze {
    private static final Analyze instance = new Analyze();
    private static final Logger logger = LogManager.getLogger(Analyze.class);
    public final Map<Target, Map<Metric, Knowledge.Rfc>> currentRfc = new ConcurrentHashMap<>();
    private static final Double FLAT_TREND_THRESHOLD = 0.005;
    private static enum Trend {
        ASCENDING,
        DESCENDING,
        FLAT,
        VERTICAL,
        UNKNOWN
    }

    private Analyze() {
        currentRfc.put(Target.GATEWAY, Map.of(
                Metric.LATENCY_MS, Knowledge.Rfc.GATEWAY_DO_NOTHING,
                Metric.REQUESTS_PER_SECOND, Knowledge.Rfc.GATEWAY_DO_NOTHING
        ));
        currentRfc.put(Target.SERVER, Map.of(
                Metric.LATENCY_MS, Knowledge.Rfc.SERVER_DO_NOTHING,
                Metric.REQUESTS_PER_SECOND, Knowledge.Rfc.SERVER_DO_NOTHING
        ));
    }

    public static Analyze getInstance() {
        return instance;
    }

    public void start() {
        logger.info("Start Analyzing");

        while (Main.run.get()) {
            Map<Target, Map<Metric, Knowledge.Symptom>> symptom = getSymptom();
            Map<Target, Map<Metric, Knowledge.Rfc>> nextRfc = generateRfc(symptom);
            updateCurrentRfc(nextRfc);
        }
    }

    private Map<Target, Map<Metric, Knowledge.Symptom>> getSymptom() {
        synchronized (Monitor.getInstance().currentSymptom) {
            try {
                Monitor.getInstance().currentSymptom.wait();
            } catch (InterruptedException e) {
                logger.error("Error in getting Symptom: ", e);
            }
        }
        return Monitor.getInstance().currentSymptom;
    }

    private Map<Target, Map<Metric, Knowledge.Rfc>> generateRfc(Map<Target, Map<Metric, Knowledge.Symptom>> symptom) {
        Map<Target, Map<Metric, Knowledge.Rfc>> rfc = new HashMap<>();

        for (Map.Entry<Target, Map<Metric, Knowledge.Symptom>> symptomEntry : symptom.entrySet()) {
            Target target = symptomEntry.getKey();
            Map<Metric, Knowledge.Symptom> symptomsForTarget = symptomEntry.getValue();

            for (Map.Entry<Metric, Knowledge.Symptom> symptomValueEntry : symptomsForTarget.entrySet()) {
                Metric metric = symptomValueEntry.getKey();
                Knowledge.Symptom symptomValue = symptomValueEntry.getValue();

                Trend trend = getTrend(Knowledge.getInstance().getLastValues(target, metric));

                Knowledge.Rfc rfcValue = switch (target) {
                    case GATEWAY -> trend != Trend.DESCENDING && symptomValue == Knowledge.Symptom.GATEWAY_NOK
                            ? switch (metric) {
                                case LATENCY_MS -> Knowledge.Rfc.GATEWAY_DECREASE_LAT;
                                case REQUESTS_PER_SECOND -> Knowledge.Rfc.GATEWAY_DECREASE_RPS;
                            }
                            : Knowledge.Rfc.GATEWAY_DO_NOTHING;
                    case SERVER -> trend != Trend.DESCENDING && symptomValue == Knowledge.Symptom.SERVER_NOK
                            ? switch (metric) {
                                case LATENCY_MS -> Knowledge.Rfc.SERVER_DECREASE_LAT;
                                case REQUESTS_PER_SECOND -> Knowledge.Rfc.SERVER_DECREASE_RPS;
                            }
                            : Knowledge.Rfc.SERVER_DO_NOTHING;
                };

                logger.info("Defined RFC {} for metric {} on target {}", rfcValue, metric, target);
                rfc.computeIfAbsent(target, k -> new HashMap<>()).put(metric, rfcValue);
            }
        }

        return rfc;
    }

    private static Trend getTrend(List<Double> data) {
        if (data == null || data.size() < 2) return Trend.UNKNOWN;

        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;
        int n = data.size();

        for (int x = 0; x < n; x++) {
            double y = data.get(x); // The value is the Y-axis

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // Formula for Slope (m)
        double numerator = (n * sumXY) - (sumX * sumY);
        double denominator = (n * sumX2) - (sumX * sumX);

        if (denominator == 0) return Trend.VERTICAL; // Should not happen with indices as X

        double slope = numerator / denominator;

        if (slope > FLAT_TREND_THRESHOLD) return Trend.ASCENDING;
        if (slope < -FLAT_TREND_THRESHOLD) return Trend.DESCENDING;

        return Trend.FLAT;
    }

    private void updateCurrentRfc(Map<Target, Map<Metric, Knowledge.Rfc>> nextRfc) {
        synchronized (currentRfc) {
            currentRfc.putAll(nextRfc);
            currentRfc.notifyAll();
        }
    }
}
