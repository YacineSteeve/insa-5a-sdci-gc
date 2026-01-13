package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import com.blyweertboukari.sdci.utils.Metric;
import com.blyweertboukari.sdci.utils.MetricsReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@SuppressWarnings({"SynchronizeOnNonFinalField"})
public class Monitor {
    private static Monitor instance;
    private static final Logger logger = LogManager.getLogger(Monitor.class);
    private static final int period = 2000;
    private static List<String> symptom;
    public String gw_current_SYMP = "N/A";

    public static Monitor getInstance() {
        if (instance == null) {
            instance = new Monitor();
        }
        return instance;
    }

    public void start() {
        logger.info("Start monitoring");
        symptom = Knowledge.getInstance().get_symptoms();
        data_collector();
        symptom_generator();
    }

    //Symptom Generator (can be modified)
    private void symptom_generator() {
        while (Main.run.get())
            try {
                Thread.sleep(period * 5);
                ResultSet rs = Knowledge.getInstance().select_from_tab();
                double[] prediction = analyse_metrics(rs);
                boolean isOk = true;
                for (int j = 0; j < Knowledge.horizon; j++) {
                    if (prediction[j] > Knowledge.gw_lat_threshold) {
                        logger.info("Symptom --> To Analyse : {}", symptom.get(1));
                        update_symptom(symptom.get(1));
                        isOk = false;
                        break;
                    } else if (prediction[j] < .0) {
                        logger.info(" Symptom --> To Analyse : {}", symptom.get(0));
                        update_symptom(symptom.get(0));
                        isOk = false;
                        break;
                    }
                }
                if (isOk) {
                    logger.info("Symptom --> To Analyse : {}", symptom.get(2));
                    update_symptom(symptom.get(2));
                }
            } catch (SQLException | InterruptedException e) {
                logger.error("Symptom generator error: ", e);
            }
    }

    //Data Collector
    private void data_collector() {
        new Thread(() -> {
            logger.info("Reading metrics...");
            while (Main.run.get()) {
                try {
                    Knowledge.getInstance().insert_in_tab(new Timestamp(new Date().getTime()), get_data());
                } catch (InterruptedException | IOException e) {
                    logger.error("Data collector error: ", e);
                }
            }
        }

        ).start();
    }

    private double get_data() throws IOException, InterruptedException {
        String metric = MetricsReader.getMetric(Metric.REQUESTS_PER_SECOND);
        return Double.parseDouble(metric);
    }

    //TODO : implementer fonction anaylse thresholds...
    private double[] analyse_metrics(ResultSet rs) throws SQLException {
        rs.first();
        return new double[Knowledge.horizon];
    }

    private void update_symptom(String symptom) {

        synchronized (gw_current_SYMP) {
            gw_current_SYMP.notify();
            gw_current_SYMP = symptom;

        }
    }
}

