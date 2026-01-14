package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
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
            Map<Target, Knowledge.Plan> plan = getPlan();
            Map<Target, Knowledge.Workflow> workflow = generateWorkflow(plan);
            executeWorkflow(workflow);
        }
    }

    private Map<Target, Knowledge.Plan> getPlan() {
        synchronized (Plan.getInstance().currentPlan) {
            try {
                Plan.getInstance().currentPlan.wait();
            } catch (InterruptedException e) {
                logger.error("Error while getting Plan: ", e);
            }
        }
        return Plan.getInstance().currentPlan;
    }

    private Map<Target, Knowledge.Workflow> generateWorkflow(Map<Target, Knowledge.Plan> plan) {
        Map<Target, Knowledge.Workflow> workflow = new HashMap<>();

        for (Map.Entry<Target, Knowledge.Plan> planEntry : plan.entrySet()) {
            Target target = planEntry.getKey();
            Knowledge.Plan planValue = planEntry.getValue();

            Knowledge.Workflow workflowValue = switch (target) {
                case GATEWAY -> switch (planValue) {
                    case GATEWAY_SCALE_UP_CPU -> Knowledge.Workflow.GATEWAY_INCREASE_CPU;
                    case GATEWAY_SCALE_UP_RAM -> Knowledge.Workflow.GATEWAY_INCREASE_RAM;
                    case GATEWAY_SCALE_DOWN_CPU -> Knowledge.Workflow.GATEWAY_DECREASE_CPU;
                    case GATEWAY_SCALE_DOWN_RAM -> Knowledge.Workflow.GATEWAY_DECREASE_RAM;
                    default -> null;
                };
                case SERVER -> switch (planValue) {
                    case SERVER_SCALE_UP_CPU -> Knowledge.Workflow.SERVER_INCREASE_CPU;
                    case SERVER_SCALE_UP_RAM -> Knowledge.Workflow.SERVER_INCREASE_RAM;
                    case SERVER_SCALE_DOWN_CPU -> Knowledge.Workflow.SERVER_DECREASE_CPU;
                    case SERVER_SCALE_DOWN_RAM -> Knowledge.Workflow.SERVER_DECREASE_RAM;
                    default -> null;
                };
            };

            if (workflowValue == null) {
                logger.warn("Workflow for plan {} not handled for target {}", planValue, target);
            } else {
                workflow.put(target, workflowValue);
                logger.info("Generated workflow {} for target {} based on plan {}", workflowValue, target, planValue);
            }
        }

        return workflow;
    }

    private void executeWorkflow(Map<Target, Knowledge.Workflow> workflow) {
        for (Map.Entry<Target, Knowledge.Workflow> workflowEntry : workflow.entrySet()) {
            Target target = workflowEntry.getKey();
            Knowledge.Workflow workflowValue = workflow.get(target);
            logger.info("Executing workflow {} for target {}", workflowValue, target);

            KubernetesClient.Resource resource;
            double valueDelta;

            switch (workflowValue) {
                case GATEWAY_INCREASE_CPU, SERVER_INCREASE_CPU -> {
                    resource = KubernetesClient.Resource.CPU;
                    valueDelta = CPU_STEP;
                }
                case GATEWAY_INCREASE_RAM, SERVER_INCREASE_RAM -> {
                    resource = KubernetesClient.Resource.RAM;
                    valueDelta = RAM_STEP;
                }
                case GATEWAY_DECREASE_CPU, SERVER_DECREASE_CPU -> {
                    resource = KubernetesClient.Resource.CPU;
                    valueDelta = -CPU_STEP;
                }
                case GATEWAY_DECREASE_RAM, SERVER_DECREASE_RAM -> {
                    resource = KubernetesClient.Resource.RAM;
                    valueDelta = -RAM_STEP;
                }
                default -> {
                    logger.warn("Workflow {} not handled for target {}", workflowValue, target);
                    continue;
                }
            }

            KubernetesClient.getInstance().updateResourceLimits(target, resource, valueDelta);
        }
    }
}
