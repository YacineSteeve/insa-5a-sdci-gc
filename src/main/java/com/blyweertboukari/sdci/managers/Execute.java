package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

@SuppressWarnings({"SameParameterValue", "SynchronizeOnNonFinalField"})
public class Execute {
    private static Execute instance;

    private static final Logger logger = LogManager.getLogger(Execute.class);

    public static Execute getInstance() {
        if (instance == null) {
            instance = new Execute();
        }
        return instance;
    }

    private static List<String> workflow_lists;

    public void start() throws InterruptedException {
        logger.info("Start Execution");
        workflow_lists = Knowledge.getInstance().get_worklow_lists();

        while (Main.run.get()) {
            String current_plan = get_plan();

            // logger("Received Plan : " + current_plan);
            String[] workflow = workflow_generator(current_plan);
            for (int i = 0; i < workflow.length; i++) {
                logger.info("workflow [{}] : {}", i, workflow[i]);

            }

            for (String w : workflow) {
                logger.info("UC : {}", w);
                Thread.sleep(2000);
            }

        }
    }

    //Plan Receiver
    private String get_plan() {
        synchronized (Plan.getInstance().gw_PLAN) {
            try {
                Plan.getInstance().gw_PLAN.wait();
            } catch (InterruptedException e) {
                logger.error("Error in getting Plan: ", e);
            }
        }
        return Plan.getInstance().gw_PLAN;
    }

    //Rule-based Workflow Generator
    private String[] workflow_generator(String plan) {
        List<String> plans = Knowledge.getInstance().get_plans();
        if (plan.contentEquals(plans.get(0))) {
            return workflow_lists.get(0).split("/");
        } else if (plan.contentEquals(plans.get(1))) {
            return workflow_lists.get(1).split("/");
        } else if (plan.contentEquals(plans.get(2))) {
            return workflow_lists.get(2).split("/");
        } else
            return null;
    }
}
