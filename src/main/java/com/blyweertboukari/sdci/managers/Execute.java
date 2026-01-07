package com.blyweertboukari.sdci.managers;

import java.util.List;

@SuppressWarnings({"SameParameterValue", "SynchronizeOnNonFinalField"})
public class Execute {
    private static List<String> workflow_lists;

    void start() throws InterruptedException {
        Main.logger(this.getClass().getSimpleName(), "Start Execution");
        workflow_lists = Main.shared_knowledge.get_worklow_lists();

        while (Main.run) {
            String current_plan = get_plan();

            // Main.logger(this.getClass().getSimpleName(), "Received Plan : " + current_plan);
            String[] workflow = workflow_generator(current_plan);
            for (int i = 0; i < workflow.length; i++) {
                Main.logger(this.getClass().getSimpleName(), "workflow [" + i + "] : " + workflow[i]);

            }

            for (String w : workflow) {
                Main.logger(this.getClass().getSimpleName(), "UC : " + w);
                Thread.sleep(2000);
            }

        }
    }

    //Plan Receiver
    private String get_plan() {
        synchronized (Main.plan.gw_PLAN) {
            try {
                Main.plan.gw_PLAN.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return Main.plan.gw_PLAN;
    }

    //Rule-based Workflow Generator
    private String[] workflow_generator(String plan) {
        List<String> plans = Main.shared_knowledge.get_plans();
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
