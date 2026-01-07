package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.Main;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@SuppressWarnings({"SynchronizeOnNonFinalField"})
public class Monitor {
    private static Monitor instance;

    private static final Logger logger = LogManager.getLogger(Monitor.class);

    public static Monitor getInstance() {
        if (instance == null) {
            instance = new Monitor();
        }
        return instance;
    }

    private static List<String> symptom;
    private static final int period = 2000;
    private static double i = 0;
    public String gw_current_SYMP = "N/A";

    public void start() {
        logger.info("Start monitoring of " + Knowledge.gw);
        symptom = Knowledge.getInstance().get_symptoms();
        Knowledge.getInstance().create_lat_tab();
        data_collector(); //in bg
        symptom_generator();
    }

    //Symptom Generator  (can be modified)
    private void symptom_generator() {
        while (Main.run.get())
            try {
                Thread.sleep(period * 5);
                ResultSet rs = Knowledge.getInstance().select_from_tab();
                //print_nice_rs(rs);
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
            while (Main.run.get())
                try {
                    //TODO: Remove this
                    Thread.sleep(period);
                    Knowledge.getInstance().insert_in_tab(new java.sql.Timestamp(new java.util.Date().getTime()), get_fake_data());
                } catch (InterruptedException e) {
                    logger.error("Data collector error: ", e);
                }

        }

        ).start();
    }

    private int get_data() {
        //Call Sensors
        /*TODO*/
        return 0;
    }

    private double get_fake_data() {
        //return new Random().nextInt();
        return i += 2.5;
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
        //ArimaOrder modelOrder = ArimaOrder.order(0, 0, 0, 1, 1, 1);
        Arima model = Arima.model(timeSeries, modelOrder);
        Forecast forecast = model.forecast(Knowledge.moving_wind);
        System.out.print("Point Estimates : ");
        for (int k = 0; k < Knowledge.horizon; k++) {
            p[k] = forecast.pointEstimates().at(k);
            System.out.print(p[k] + "; ");
        }
        System.out.println();
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

