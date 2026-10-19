
package com.shopease;

import java.sql.Connection;

public class DBTest {
    public static void main(String[] args) {
        try (Connection connection =
                     DBConnection.getConnection()) {
            System.out.println(
                    "SUCCESS: Connected to ShopEase database!"
            );
        } catch (Exception e) {
            System.out.println("Connection failed:");
            e.printStackTrace();
        }
    }
}
