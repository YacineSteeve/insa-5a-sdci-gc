package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import com.blyweertboukari.sdci.enums.Metric;
import com.blyweertboukari.sdci.enums.Target;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Plan {
    private static final Plan instance = new Plan();
    private static final Logger logger = LogManager.getLogger(Plan.class);
    public final Map<Target, Map<Metric, Knowledge.Plan>> currentPlan = new ConcurrentHashMap<>();

    private Plan() {
        currentPlan.put(Target.GATEWAY, Map.of(
                Metric.LATENCY_MS, Knowledge.Plan.GATEWAY_NO_ACTION
        ));
        currentPlan.put(Target.SERVER, Map.of(
                Metric.LATENCY_MS, Knowledge.Plan.SERVER_NO_ACTION
        ));
    }

    public static Plan getInstance() {
        return instance;
    }

    public void start() {
        logger.info("Start Plan");

        while (Main.run.get()) {
            Map<Target, Map<Metric, Knowledge.Rfc>> rfc = getRfc();
            Map<Target, Map<Metric, Knowledge.Plan>> nextPlan = generatePlan(rfc);
            updateCurrentPlan(nextPlan);
        }
    }

    private Map<Target, Map<Metric, Knowledge.Rfc>> getRfc() {
        synchronized (Analyze.getInstance().currentRfc) {
            try {
                Analyze.getInstance().currentRfc.wait();
            } catch (InterruptedException e) {
                logger.error("Error while getting RFC: ", e);
            }
        }
        return Analyze.getInstance().currentRfc;
    }

    private Map<Target, Map<Metric, Knowledge.Plan>> generatePlan(Map<Target, Map<Metric, Knowledge.Rfc>> rfc) {
        Map<Target, Map<Metric, Knowledge.Plan>> plan = new HashMap<>();

        for (Map.Entry<Target, Map<Metric, Knowledge.Rfc>> rfcEntry : rfc.entrySet()) {
            Target target = rfcEntry.getKey();
            Map<Metric, Knowledge.Rfc> rfcsForTarget = rfcEntry.getValue();

            for (Map.Entry<Metric, Knowledge.Rfc> rfcValueEntry : rfcsForTarget.entrySet()) {
                Metric metric = rfcValueEntry.getKey();
                Knowledge.Rfc rfcValue = rfcValueEntry.getValue();

                Knowledge.Plan currentPlanValue = currentPlan.get(target).get(metric);

                Knowledge.Plan planValue = switch (target) {
                    case GATEWAY -> switch (metric) {
                        case LATENCY_MS -> switch (rfcValue) {
                            case GATEWAY_DO_NOTHING -> currentPlanValue == Knowledge.Plan.GATEWAY_SCALE_DOWN
                                // The previous scaling down did not negatively affect, so we try reducing again
                                ? Knowledge.Plan.GATEWAY_SCALE_DOWN
                                // In other cases, we do nothing
                                : Knowledge.Plan.GATEWAY_NO_ACTION;
                            case GATEWAY_DECREASE_LAT -> Knowledge.Plan.GATEWAY_SCALE_UP;
                            case GATEWAY_KEEP_LAT -> Knowledge.Plan.GATEWAY_SCALE_DOWN;
                            default -> null;
                        };
                    };
                    case SERVER -> switch (metric) {
                        case LATENCY_MS -> switch (rfcValue) {
                            case SERVER_DO_NOTHING -> currentPlanValue == Knowledge.Plan.SERVER_SCALE_DOWN
                                ? Knowledge.Plan.SERVER_SCALE_DOWN
                                : Knowledge.Plan.SERVER_NO_ACTION;
                            case SERVER_DECREASE_LAT -> Knowledge.Plan.SERVER_SCALE_UP;
                            case SERVER_KEEP_LAT -> Knowledge.Plan.SERVER_SCALE_DOWN;
                            default -> null;
                        };
                    };
                };

                if (planValue == null) {
                    logger.warn("Plan for RFC {} not handled for target {} and metric {}", rfcValue, target, metric);
                } else {
                    plan.computeIfAbsent(target, k -> new HashMap<>()).put(metric, planValue);
                    logger.info("Planned {} for target {} and metric {} based on RFC {}", planValue, target, metric, rfcValue);
                }
            }
        }

        return plan;
    }

    private void updateCurrentPlan(Map<Target, Map<Metric, Knowledge.Plan>> plan) {
        synchronized (currentPlan) {
            this.currentPlan.putAll(plan);
            currentPlan.notifyAll();
        }
    }
}
