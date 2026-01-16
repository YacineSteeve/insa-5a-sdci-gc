package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import com.blyweertboukari.sdci.enums.Metric;
import com.blyweertboukari.sdci.enums.Target;
import jdk.dynalink.StandardNamespace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Analyze {
    private static final Analyze instance = new Analyze();
    private static final Logger logger = LogManager.getLogger(Analyze.class);
    private static int i;
    public final Map<Target, Map<Metric, Knowledge.Rfc>> currentRfc = new ConcurrentHashMap<>();

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

            Map<Target, Knowledge.Symptom> currentSymptom = getSymptom();

            update_rfc(rfcGenerator(currentSymptom));
        }
    }

    //Symptom Receiver
    private Map<Target, Knowledge.Symptom> getSymptom() {
        synchronized (Monitor.getInstance().currentSymptom) {
            try {
                Monitor.getInstance().currentSymptom.wait();
            } catch (InterruptedException e) {
                logger.error("Error in getting Symptom: ", e);
            }
        }
        return Monitor.getInstance().currentSymptom;
    }

    //Rule-based RFC Generator
    private String rfcGenerator( Map<Target, Knowledge.Symptom> symptom) {
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

        // FAIRE UNE ANALYSE DE POURQUOI ON A NOK (Différente en fonction de la métrique)  OU ALORS SI ON A OK OU NA NE RIEN FAIRE

    }

    private void update_rfc(String rfc) {

        synchronized (gw_current_RFC) {
            gw_current_RFC.notify();
            gw_current_RFC = rfc;

        }
    }
}
