package com.blyweertboukari.sdci.managers;

import com.blyweertboukari.sdci.enums.Metric;
import com.blyweertboukari.sdci.enums.Target;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.List;

public class Knowledge {
    private static final Knowledge instance = new Knowledge();
    private static final Logger logger = LogManager.getLogger(Knowledge.class);
    private static final String DB_DRIVER = "org.h2.Driver";
    private static final String DB_PATH = System.getProperty("db.path", "./src/main/resources/knowledge");
    private static final String DB_CONNECTION = "jdbc:h2:" + DB_PATH;
    private static final String DB_USER = "";
    private static final String DB_PASSWORD = "";

    static final int MONITORING_INTERVAL_MS = 5000;
    static final int MOVING_WINDOW_SIZE = 20;
    static final double GATEWAY_LATENCY_THRESHOLD = 20;
    static final double GATEWAY_RPS_THRESHOLD = 20;
    static final double SERVER_LATENCY_THRESHOLD = 20;
    static final double SERVER_RPS_THRESHOLD = 20;
    static final int CPU_CHANGE_STEP_M = 10; // 10m
    static final int RAM_CHANGE_STEP_MI = 32; // 32Mi

    public enum Symptom {
        GATEWAY_NA,
        GATEWAY_NOK,
        GATEWAY_OK,

        SERVER_NA,
        SERVER_NOK,
        SERVER_OK
    }

    public enum Rfc {
        GATEWAY_DO_NOTHING,
        GATEWAY_DECREASE_LAT,
        GATEWAY_DECREASE_RPS,

        SERVER_DO_NOTHING,
        SERVER_DECREASE_LAT,
        SERVER_DECREASE_RPS
    }

    public enum Plan {
        GATEWAY_NO_ACTION,
        GATEWAY_SCALE_UP_CPU,
        GATEWAY_SCALE_UP_RAM,
        GATEWAY_SCALE_DOWN_CPU,
        GATEWAY_SCALE_DOWN_RAM,

        SERVER_NO_ACTION,
        SERVER_SCALE_UP_CPU,
        SERVER_SCALE_UP_RAM,
        SERVER_SCALE_DOWN_CPU,
        SERVER_SCALE_DOWN_RAM
    }

    public enum Workflow {
        GATEWAY_NO_ACTION,
        GATEWAY_INCREASE_RAM,
        GATEWAY_INCREASE_CPU,
        GATEWAY_DECREASE_RAM,
        GATEWAY_DECREASE_CPU,

        SERVER_NO_ACTION,
        SERVER_INCREASE_RAM,
        SERVER_INCREASE_CPU,
        SERVER_DECREASE_RAM,
        SERVER_DECREASE_CPU
    }

    public static Knowledge getInstance() {
        return instance;
    }

    public void start() {
        logger.info("Knowledge Starting");
        createTables();
    }

    public void addValue(Target target, Metric metric, double value) {
        try (Connection connection = getDatabaseConnection()) {
            String insertQuery = "INSERT INTO " + metric.tableName + " (id, target, value) VALUES (?, ?, ?)";

            PreparedStatement preparedStatement = connection.prepareStatement(insertQuery);
            preparedStatement.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            preparedStatement.setString(2, target.name());
            preparedStatement.setDouble(3, value);

            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException e) {
            logger.error("Failed to execute query: ", e);
        } catch (Exception e) {
            logger.error("Failed to add value: ", e);
        }
    }

    public List<Double> getLastValues(Metric metric, Target target) {
        try (Connection connection = getDatabaseConnection()) {
            String query = "SELECT metric_value FROM " + metric.tableName + " WHERE target = ? ORDER BY id DESC LIMIT ?";

            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, target.name());
            preparedStatement.setInt(2, MOVING_WINDOW_SIZE);

            ResultSet resultSet = preparedStatement.executeQuery();

            List<Double> values = new java.util.ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getDouble("value"));
            }
            resultSet.close();
            preparedStatement.close();

            return values;
        } catch (SQLException e) {
            logger.error("Failed to execute query: ", e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            logger.error("Failed to get last values: ", e);
            throw new RuntimeException(e);
        }
    }

    private void createTables() {
        try (Connection connection = getDatabaseConnection()) {
            connection.setAutoCommit(false);

            for (Metric metric : Metric.values()) {
                Statement create;
                create = connection.createStatement();
                create.execute("CREATE TABLE IF NOT EXISTS " + metric.tableName + " (id TIMESTAMP PRIMARY KEY, target VARCHAR(50), metric_value DOUBLE)");
                create.close();

                PreparedStatement update = connection.prepareStatement("TRUNCATE TABLE " + metric.tableName);
                update.executeUpdate();
                update.close();

                logger.info("Table {} created", metric.tableName);
            }

            connection.commit();

            logger.info("All tables Created");
        } catch (SQLException e) {
            logger.error("Failed to execute query: ", e);
        } catch (Exception e) {
            logger.error("Failed to create tables: ", e);
        }
    }

    private Connection getDatabaseConnection() {
        try {
            Class.forName(DB_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load JDBC driver", e);
        }

        try {
            return DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to the database", e);
        }
    }
}
