package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import com.blyweertboukari.sdci.enums.Metric;
import com.blyweertboukari.sdci.enums.Target;
import com.blyweertboukari.sdci.utils.KubernetesClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class Execute {
    private static final Execute instance = new Execute();
    private static final Logger logger = LogManager.getLogger(Execute.class);
    private static final double CPU_STEP = .25;
    private static final double RAM_STEP = 5;
    /* TODO: Find best step values */

    public static Execute getInstance() {
        return instance;
    }

    public void start() {
        logger.info("Start Execute");

        while (Main.run.get()) {
            Map<Target, Map<Metric, Knowledge.Plan>> plan = getPlan();
            Map<Target, Map<Metric, Knowledge.Workflow>> workflow = generateWorkflow(plan);
            executeWorkflow(workflow);
        }
    }

    private Map<Target, Map<Metric, Knowledge.Plan>> getPlan() {
        synchronized (Plan.getInstance().currentPlan) {
            try {
                Plan.getInstance().currentPlan.wait();
            } catch (InterruptedException e) {
                logger.error("Error while getting Plan: ", e);
            }
        }
        return Plan.getInstance().currentPlan;
    }

    private Map<Target, Map<Metric, Knowledge.Workflow>> generateWorkflow(Map<Target, Map<Metric, Knowledge.Plan>> plan) {
        Map<Target, Map<Metric, Knowledge.Workflow>> workflow = new HashMap<>();

        for (Map.Entry<Target, Map<Metric, Knowledge.Plan>> planEntry : plan.entrySet()) {
            Target target = planEntry.getKey();
            Map<Metric, Knowledge.Plan> plansForTarget = planEntry.getValue();

            for (Map.Entry<Metric, Knowledge.Plan> planValueEntry : plansForTarget.entrySet()) {
                Metric metric = planValueEntry.getKey();
                Knowledge.Plan planValue = planValueEntry.getValue();

                Knowledge.Workflow workflowValue = switch (target) {
                    case GATEWAY -> switch (metric) {
                        case LATENCY_MS -> switch (planValue) {
                            case GATEWAY_SCALE_UP_RAM -> Knowledge.Workflow.GATEWAY_INCREASE_RAM;
                            case GATEWAY_SCALE_DOWN_RAM -> Knowledge.Workflow.GATEWAY_DECREASE_RAM;
                            default -> null;
                        };
                        case REQUESTS_PER_SECOND -> switch (planValue) {
                            case GATEWAY_SCALE_UP_CPU -> Knowledge.Workflow.GATEWAY_INCREASE_CPU;
                            case GATEWAY_SCALE_DOWN_CPU -> Knowledge.Workflow.GATEWAY_DECREASE_CPU;
                            default -> null;
                        };
                    };
                    case SERVER -> switch (metric) {
                        case LATENCY_MS -> switch (planValue) {
                            case SERVER_SCALE_UP_RAM -> Knowledge.Workflow.SERVER_INCREASE_RAM;
                            case SERVER_SCALE_DOWN_RAM -> Knowledge.Workflow.SERVER_DECREASE_RAM;
                            default -> null;
                        };
                        case REQUESTS_PER_SECOND -> switch (planValue) {
                            case SERVER_SCALE_UP_CPU -> Knowledge.Workflow.SERVER_INCREASE_CPU;
                            case SERVER_SCALE_DOWN_CPU -> Knowledge.Workflow.SERVER_DECREASE_CPU;
                            default -> null;
                        };
                    };
                };

                if (workflowValue == null) {
                    logger.warn("Workflow for plan {} not handled for target {} and metric {}", planValue, target, metric);
                } else {
                    workflow.get(target).put(metric, workflowValue);
                    logger.info("Generated workflow {} for target {} and metric {} based on plan {}", workflowValue, target, metric, planValue);
                }
            }
        }

        return workflow;
    }

    private void executeWorkflow(Map<Target, Map<Metric, Knowledge.Workflow>> workflow) {
        for (Map.Entry<Target, Map<Metric, Knowledge.Workflow>> workflowEntry : workflow.entrySet()) {
            Target target = workflowEntry.getKey();
            Map<Metric, Knowledge.Workflow> workflowsForTarget = workflow.get(target);
            logger.info("Executing workflows {} for target {}", workflowsForTarget.values(), target);

            Map<KubernetesClient.Resource, Double> resourcesUpdateDeltas = new HashMap<>();

            for (Map.Entry<Metric, Knowledge.Workflow> workflowValueEntry : workflowsForTarget.entrySet()) {
                Metric metric = workflowValueEntry.getKey();
                Knowledge.Workflow workflowValue = workflowValueEntry.getValue();

                KubernetesClient.Resource resource = switch (metric) {
                    case LATENCY_MS -> KubernetesClient.Resource.CPU;
                    case REQUESTS_PER_SECOND -> KubernetesClient.Resource.RAM;
                };

                double valueDelta = switch (workflowValue) {
                    case GATEWAY_INCREASE_CPU, SERVER_INCREASE_CPU -> CPU_STEP;
                    case GATEWAY_DECREASE_CPU, SERVER_DECREASE_CPU -> -CPU_STEP;
                    case GATEWAY_INCREASE_RAM, SERVER_INCREASE_RAM -> RAM_STEP;
                    case GATEWAY_DECREASE_RAM, SERVER_DECREASE_RAM -> -RAM_STEP;
                };

                resourcesUpdateDeltas.put(resource, valueDelta);
            }

            KubernetesClient.getInstance().updateResourceLimits(target, resourcesUpdateDeltas);
        }
    }
}
