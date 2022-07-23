package com.ollethunberg.semihardcore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SQLHelper {
    private Connection conn;

    public SQLHelper(Connection connection) {
        conn = connection;
    }

    public Connection getConnection() {
        return conn;
    }

    public void closeConnection() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public ResultSet query(String query, Object... args) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(query);
        for (int i = 0; i < args.length; i++) {
            preparedStatement.setObject(i + 1, args[i]);
        }
        preparedStatement.executeQuery();
        return preparedStatement.getResultSet();
    }

    public void update(String query, Object... args) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(query);
        for (int i = 0; i < args.length; i++) {
            preparedStatement.setObject(i + 1, args[i]);
        }
        preparedStatement.executeUpdate();
    }

}
