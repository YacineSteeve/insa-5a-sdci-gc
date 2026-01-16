package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import com.blyweertboukari.sdci.enums.Metric;
import com.blyweertboukari.sdci.utils.MetricsReader;
import com.blyweertboukari.sdci.enums.Target;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Monitor {
    private static final Monitor instance = new Monitor();
    private static final Logger logger = LogManager.getLogger(Monitor.class);
    private static final int PERIOD = 2000;
    public final Map<Target, Knowledge.Symptom> currentSymptom = new ConcurrentHashMap<>();

    private Monitor() {
        currentSymptom.put(Target.GATEWAY, Knowledge.Symptom.GATEWAY_NA);
        currentSymptom.put(Target.SERVER, Knowledge.Symptom.SERVER_NA);
    }

    public static Monitor getInstance() {
        return instance;
    }

    public void start() {
        logger.info("Start monitoring");
        symptomGenerator();
    }

    private void symptomGenerator() {
        while (Main.run.get())
            try {
                Thread.sleep(PERIOD);
                for (Target target : Target.values()){
                    Knowledge.Symptom symptom = switch (target) {
                        case GATEWAY -> Knowledge.Symptom.GATEWAY_OK;
                        case SERVER -> Knowledge.Symptom.SERVER_OK;
                    };
                    for (Metric metric : Metric.values()){
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
                                        :  Knowledge.Symptom.SERVER_OK;
                                case LATENCY_MS ->  value > Knowledge.SERVER_LATENCY_THRESHOLD
                                        ? Knowledge.Symptom.SERVER_NOK
                                        : Knowledge.Symptom.SERVER_OK;
                            };
                        };
                        Knowledge.getInstance().addValue(target, metric, value);
                        if ((metricSymptom == Knowledge.Symptom.GATEWAY_NOK
                                || metricSymptom == Knowledge.Symptom.SERVER_NOK)
                                && metricSymptom != symptom) {
                            symptom = metricSymptom;
                        }
                    }
                    updateSymptom(target, symptom);
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Symptom generator error: ", e);
            }
    }

    private double getData(Target target, Metric metric) throws IOException, InterruptedException {
        String readValue = MetricsReader.getInstance().getMetric(target, metric);
        return Double.parseDouble(readValue);
    }

    private void updateSymptom(Target target, Knowledge.Symptom symptom) {
        synchronized (currentSymptom) {
            this.currentSymptom.put(target, symptom);
            currentSymptom.notifyAll();
        }
    }
}

