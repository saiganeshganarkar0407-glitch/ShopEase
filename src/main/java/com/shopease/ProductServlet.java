package com.shopease;

import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/products")
public class ProductServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        List<Map<String, Object>> products =
                new ArrayList<>();

        String sql = """
            SELECT product_id, name, description,
                   price, category, image_url, stock
            FROM products
            ORDER BY product_id DESC
            """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> p = new LinkedHashMap<>();

                p.put("id", rs.getInt("product_id"));
                p.put("name", rs.getString("name"));
                p.put("description", rs.getString("description"));
                p.put("price", rs.getBigDecimal("price"));
                p.put("category", rs.getString("category"));
                p.put("image", rs.getString("image_url"));
                p.put("stock", rs.getInt("stock"));

                products.add(p);
            }

            response.getWriter().write(gson.toJson(products));

        } catch (SQLException e) {
            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().write(
                    "{\"error\":\"Unable to load products\"}"
            );

            log("Product retrieval failed", e);
        }
    }
}

