package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
import com.blyweertboukari.sdci.utils.Metric;
import com.blyweertboukari.sdci.utils.MetricsReader;
import com.github.signaflo.math.operations.DoubleFunctions;
import com.github.signaflo.timeseries.TimeSeries;
import com.github.signaflo.timeseries.forecast.Forecast;
import com.github.signaflo.timeseries.model.arima.Arima;
import com.github.signaflo.timeseries.model.arima.ArimaOrder;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import de.vandermeer.asciithemes.a7.A7_Grids;
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
        logger.info("Start monitoring of " + Knowledge.gw);
        symptom = Knowledge.getInstance().get_symptoms();
        Knowledge.getInstance().create_lat_tab();
        data_collector();
        symptom_generator();
    }

    //Symptom Generator (can be modified)
    private void symptom_generator() {
        while (Main.run.get())
            try {
                Thread.sleep(period * 5);
                ResultSet rs = Knowledge.getInstance().select_from_tab();
                double[] prediction = predict_next_lat(rs);
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

    //Data Collector TODO : modify
    private void data_collector() {
        new Thread(() -> {
            logger.info("Filling db with latencies");
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

    //ARIMA-based Forecasting
    private double[] predict_next_lat(ResultSet rs) throws SQLException {
        rs.first();
        double[] history = new double[Knowledge.moving_wind];
        double[] p = new double[Knowledge.horizon];
        int j = Knowledge.moving_wind - 1;
        while (rs.next()) {
            history[j] = Double.parseDouble(rs.getString("latency"));
            j--;
        }
        TimeSeries timeSeries = TimeSeries.from(DoubleFunctions.arrayFrom(history));
        ArimaOrder modelOrder = ArimaOrder.order(0, 1, 1, 0, 1, 1);
        Arima model = Arima.model(timeSeries, modelOrder);
        Forecast forecast = model.forecast(Knowledge.moving_wind);
        StringBuilder sb = new StringBuilder();
        sb.append("Point Estimates : ");
        for (int k = 0; k < Knowledge.horizon; k++) {
            p[k] = forecast.pointEstimates().at(k);
            sb.append(p[k]).append("; ");
        }
        logger.trace(sb.toString());
        return p;
    }

    private void print_nice_rs(ResultSet rs) throws SQLException {
        rs.first();
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow("Timestamp", "Latency_in_" + Knowledge.gw);
        at.addRule();
        while (rs.next()) {
            at.addRow(rs.getTimestamp("id").getTime(), rs.getString("latency"));
            at.addRule();
        }
        at.getContext().setGrid(A7_Grids.minusBarPlusEquals());
        at.getRenderer().setCWC(new CWC_LongestWord());
        logger.info(at.render());
    }

    private void update_symptom(String symptom) {

        synchronized (gw_current_SYMP) {
            gw_current_SYMP.notify();
            gw_current_SYMP = symptom;

        }
    }
}

