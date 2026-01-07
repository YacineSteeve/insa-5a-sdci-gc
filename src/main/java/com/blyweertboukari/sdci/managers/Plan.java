package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

@SuppressWarnings({"SynchronizeOnNonFinalField"})
public class Plan {
    private static Plan instance;

    private static final Logger logger = LogManager.getLogger(Plan.class);

    public static Plan getInstance() {
        if (instance == null) {
            instance = new Plan();
        }
        return instance;
    }

    private static int i;
    public String gw_PLAN = "";

    public void start() {
        logger.info("Start Planning");

        while (true) {
            String current_rfc = get_rfc();
            //logger.info("Received RFC : " + current_rfc);
            update_plan(plan_generator(current_rfc));

        }
    }

    //RFC Receiver
    private String get_rfc() {
        synchronized (Analyze.getInstance().gw_current_RFC) {
            try {
                Analyze.getInstance().gw_current_RFC.wait();
            } catch (InterruptedException e) {
                logger.error("Error in getting RFC: ", e);
            }
        }
        return Analyze.getInstance().gw_current_RFC;
    }


    //Rule-based Plan Generator
    private String plan_generator(String rfc) {
        List<String> rfcs = Knowledge.getInstance().get_rfc();
        List<String> plans = Knowledge.getInstance().get_plans();

        if ("YourPlansDoNotWork".contentEquals(rfc)) {
            // Thread.sleep(2000);
            Main.run.set(false);
            logger.info("All the Plans were executed without success. \n \t\t The loop will stop!");
            // Terminate JVM
            System.exit(0);
        } else if (rfc.contentEquals(rfcs.get(0))) {
            logger.info("Plan --> To Execute : {}", plans.get(0));
            i = 0;
            return plans.get(0);
        } else if (rfc.contentEquals(rfcs.get(1))) {
            if (i == 0) {
                logger.info("Plan --> To Execute : {}", plans.get(1));
                i++;
                return plans.get(1);
            } else if (i == 1) {
                logger.info("Plan --> To Execute : {}", plans.get(2));
                i++;
                return plans.get(2);
            }
        }
        return null;
    }


    private void update_plan(String plan) {
        synchronized (gw_PLAN) {
            gw_PLAN.notify();
            gw_PLAN = plan;
        }
    }
}
