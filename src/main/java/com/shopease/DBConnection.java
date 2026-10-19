package com.shopease;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/shopease";

    private static final String USER = "root";

    // Replace with your MySQL password.
    private static final String PASSWORD =
            System.getenv("SHOPEASE_DB_PASSWORD");

    public static Connection getConnection()
            throws SQLException {

        if (PASSWORD == null || PASSWORD.isBlank()) {
            throw new SQLException(
                    "Set the SHOPEASE_DB_PASSWORD environment variable."
            );
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver class not found.", e);
        }

        return DriverManager.getConnection(
                URL, USER, PASSWORD
        );
    }
}
