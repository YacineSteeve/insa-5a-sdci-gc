package com.blyweertboukari.sdci;

import com.blyweertboukari.sdci.managers.Analyze;
import com.blyweertboukari.sdci.managers.Execute;
import com.blyweertboukari.sdci.managers.Monitor;
import com.blyweertboukari.sdci.managers.Plan;
import com.blyweertboukari.sdci.managers.Knowledge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static AtomicBoolean run = new AtomicBoolean(true);

    public static void main(String[] args) throws Exception {
        logger.info("Starting...");

        Knowledge.getInstance().start();

        Thread.sleep(3000);

        Thread thread_m = new Thread(() -> {
            try {
                Monitor.getInstance().start();
            } catch (Exception e) {
                logger.error("Monitoring error: ", e);
            }
        });

        Thread thread_a = new Thread(() -> {
            try {
                Analyze.getInstance().start();
            } catch (Exception e) {
                logger.error("Analysis error: ", e);
            }
        });

        Thread thread_p = new Thread(() -> {
            try {
                Plan.getInstance().start();
            } catch (Exception e) {
                logger.error("Planning error: ", e);
            }
        });

        Thread thread_e = new Thread(() -> {
            try {
                Execute.getInstance().start();
            } catch (Exception e) {
                logger.error("Execution error: ", e);
            }
        });

        thread_m.start();
        thread_a.start();
        thread_p.start();
        thread_e.start();
    }
}
