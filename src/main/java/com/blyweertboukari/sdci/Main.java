package com.blyweertboukari.sdci;

import com.blyweertboukari.sdci.managers.Analyze;
import com.blyweertboukari.sdci.managers.Execute;
import com.blyweertboukari.sdci.managers.Monitor;
import com.blyweertboukari.sdci.managers.Plan;
import com.blyweertboukari.sdci.managers.Knowledge;
import org.apache.logging.log4j.Level;
import org.apache.log4j.Logger;

import java.util.logging.LogManager;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    static final Monitor monitor = new Monitor();
    static final Analyze analyze = new Analyze();
    static final Plan plan = new Plan();
    private static final Execute execute = new Execute();
    static final Knowledge shared_knowledge = new Knowledge();

    public static void main(String[] args) throws Exception {
        shared_knowledge.start();
        Thread.sleep(3000);

        Thread thread_m = new Thread(() -> {
            try {
                monitor.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });


        Thread thread_a = new Thread(() -> {
            try {
                analyze.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread thread_p = new Thread(() -> {
            try {
                plan.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread thread_e = new Thread(() -> {
            try {
                execute.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        thread_m.start();
        thread_a.start();
        thread_p.start();
        thread_e.start();
    }
}
