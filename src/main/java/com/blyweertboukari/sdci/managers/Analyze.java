package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

@SuppressWarnings({"SynchronizeOnNonFinalField"})
public class Analyze {
    private static Analyze instance;
    private static final Logger logger = LogManager.getLogger(Analyze.class);
    private static int i;
    public static Map<Knowledge.Target, Knowledge.Rfc> currentRfc = Map.ofEntries(
            Map.entry(Knowledge.Target.GATEWAY, Knowledge.Rfc.GATEWAY_DO_NOTHING),
            Map.entry(Knowledge.Target.SERVER, Knowledge.Rfc.SERVER_DO_NOTHING)
    );

    public static Analyze getInstance() {
        if (instance == null) {
            instance = new Analyze();
        }
        return instance;
    }

    public void start() {
        logger.info("Start Analyzing");

        while (Main.run.get()) {

            String current_symptom = get_symptom();

            update_rfc(rfc_generator(current_symptom));
        }
    }

    //Symptom Receiver
    private String get_symptom() {
        synchronized (Monitor.getInstance().gw_current_SYMP) {
            try {
                Monitor.getInstance().gw_current_SYMP.wait();
            } catch (InterruptedException e) {
                logger.error("Error in getting Symptom: ", e);
            }
        }
        return Monitor.getInstance().gw_current_SYMP;
    }

    //Rule-based RFC Generator
    private String rfc_generator(String symptom) {
        List<String> symptoms = Knowledge.getInstance().get_symptoms();
        List<String> rfcs = Knowledge.getInstance().get_rfc();

        if (symptom.contentEquals(symptoms.get(0)) || symptom.contentEquals(symptoms.get(2))) {
            logger.info("RFC --> To plan : {}", rfcs.get(0));
            i = 0;
            return rfcs.get(0);
        } else if (symptom.contentEquals(symptoms.get(1))) {
            i++;
            if (i < 3) {
                logger.info("RFC --> To plan : {}", rfcs.get(1));
                return rfcs.get(1);
            } else {
                logger.info("RFC --> To plan : YourPlansDoNotWork");
                return "YourPlansDoNotWork";
            }
        } else
            return null;

    }

    private void update_rfc(String rfc) {

        synchronized (gw_current_RFC) {
            gw_current_RFC.notify();
            gw_current_RFC = rfc;

        }
    }
}
