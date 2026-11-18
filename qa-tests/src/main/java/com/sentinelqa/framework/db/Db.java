package com.sentinelqa.framework.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sentinelqa.framework.Config;

import io.qameta.allure.Allure;

/**
 * Plain JDBC helper for database assertions. Always parameterised SQL; each query shows up as an
 * Allure step so the report reads "what did we check in the DB".
 * A connection per call is deliberate: tests stay independent and there is no pool to tune.
 */
public final class Db {

    private Db() {}

    public static List<Map<String, Object>> query(String sql, Object... params) {
        return run(sql, params, statement -> {
            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                int columns = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns; i++) row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        });
    }

    public static <T> T scalar(String sql, Class<T> type, Object... params) {
        return run(sql, params, statement -> {
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No rows for: " + sql);
                return rs.getObject(1, type);
            }
        });
    }

    public static int update(String sql, Object... params) {
        return run(sql, params, PreparedStatement::executeUpdate);
    }

    private interface Work<T> {
        T apply(PreparedStatement statement) throws SQLException;
    }

    private static <T> T run(String sql, Object[] params, Work<T> work) {
        return Allure.step("SQL: " + sql.replaceAll("\\s+", " "), () -> {
            try (Connection connection = DriverManager.getConnection(
                    Config.get("db.url", "jdbc:postgresql://localhost:5432/shoplite"),
                    Config.get("db.user", "shoplite"),
                    Config.get("db.password", "shoplite"));
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
                return work.apply(statement);
            } catch (SQLException e) {
                throw new DbException(e);
            }
        });
    }

    /** Unchecked wrapper that keeps the SQLState so tests can assert on constraint violations. */
    public static final class DbException extends RuntimeException {
        public DbException(SQLException cause) {
            super(cause.getMessage(), cause);
        }

        public String sqlState() {
            return ((SQLException) getCause()).getSQLState();
        }
    }
}
