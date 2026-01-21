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
                            case GATEWAY_SCALE_UP -> Knowledge.Workflow.GATEWAY_INCREASE_LIMITS;
                            case GATEWAY_SCALE_DOWN -> Knowledge.Workflow.GATEWAY_DECREASE_LIMITS;
                            case GATEWAY_NO_ACTION -> Knowledge.Workflow.GATEWAY_NO_ACTION;
                            default -> null;
                        };
                    };
                    case SERVER -> switch (metric) {
                        case LATENCY_MS -> switch (planValue) {
                            case SERVER_SCALE_UP -> Knowledge.Workflow.SERVER_INCREASE_LIMITS;
                            case SERVER_SCALE_DOWN -> Knowledge.Workflow.SERVER_DECREASE_LIMITS;
                            case SERVER_NO_ACTION -> Knowledge.Workflow.SERVER_NO_ACTION;
                            default -> null;
                        };
                    };
                };

                if (workflowValue == null) {
                    logger.warn("Workflow for plan {} not handled for target {} and metric {}", planValue, target, metric);
                } else {
                    workflow.computeIfAbsent(target, k -> new HashMap<>()).put(metric, workflowValue);
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

            Map<KubernetesClient.Resource, Integer> resourcesUpdateDeltas = new HashMap<>();
            resourcesUpdateDeltas.put(KubernetesClient.Resource.CPU, 0);
            resourcesUpdateDeltas.put(KubernetesClient.Resource.RAM, 0);

            for (Map.Entry<Metric, Knowledge.Workflow> workflowValueEntry : workflowsForTarget.entrySet()) {
                Metric metric = workflowValueEntry.getKey();
                Knowledge.Workflow workflowValue = workflowValueEntry.getValue();

                KubernetesClient.Resource resource = switch (metric) {
                    case LATENCY_MS -> KubernetesClient.Resource.CPU;
                };

                switch (workflowValue) {
                    case GATEWAY_INCREASE_LIMITS, SERVER_INCREASE_LIMITS -> {
                        resourcesUpdateDeltas.put(
                                resource,
                                resourcesUpdateDeltas.get(KubernetesClient.Resource.CPU) + Knowledge.CPU_CHANGE_STEP_M
                        );
                        resourcesUpdateDeltas.put(
                                KubernetesClient.Resource.RAM,
                                resourcesUpdateDeltas.get(KubernetesClient.Resource.RAM) + Knowledge.RAM_CHANGE_STEP_MI
                        );
                    }
                    case GATEWAY_DECREASE_LIMITS, SERVER_DECREASE_LIMITS -> {
                        resourcesUpdateDeltas.put(
                                resource,
                                resourcesUpdateDeltas.get(KubernetesClient.Resource.CPU) - Knowledge.CPU_CHANGE_STEP_M
                        );
                        resourcesUpdateDeltas.put(
                                KubernetesClient.Resource.RAM,
                                resourcesUpdateDeltas.get(KubernetesClient.Resource.RAM) - Knowledge.RAM_CHANGE_STEP_MI
                        );
                    }
                }
            }

            resourcesUpdateDeltas.entrySet().removeIf(entry -> entry.getValue() == 0);

            if (resourcesUpdateDeltas.isEmpty()) {
                logger.info("No resource updates needed for target {}", target);
            } else {
                KubernetesClient.getInstance().updateResourceLimits(target, resourcesUpdateDeltas);
            }
        }
    }
}
