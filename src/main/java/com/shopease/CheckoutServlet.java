package com.shopease;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Map;

@WebServlet("/api/checkout")
public class CheckoutServlet extends HttpServlet {

    @SuppressWarnings("unchecked")
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null ||
                !(session.getAttribute("cart") instanceof Map)) {
            response.sendError(400, "Your cart is empty");
            return;
        }

        Map<Integer, Integer> cart =
                (Map<Integer, Integer>) session.getAttribute("cart");

        if (cart.isEmpty()) {
            response.sendError(400, "Your cart is empty");
            return;
        }

        JsonObject body;

        try {
            body = JsonParser.parseReader(
                    request.getReader()
            ).getAsJsonObject();
        } catch (Exception e) {
            response.sendError(400, "Invalid request");
            return;
        }

        String address = body.has("address")
                ? body.get("address").getAsString().trim()
                : "";

        if (address.isBlank() || address.length() > 1000) {
            response.sendError(400, "Enter a valid address");
            return;
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                BigDecimal total = BigDecimal.ZERO;

                // Lock stock rows while checking availability.
                String stockSql = """
                    SELECT price, stock FROM products
                    WHERE product_id = ? FOR UPDATE
                    """;

                try (PreparedStatement ps =
                             con.prepareStatement(stockSql)) {

                    for (Map.Entry<Integer, Integer> entry
                            : cart.entrySet()) {

                        ps.setInt(1, entry.getKey());

                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException(
                                        "A product no longer exists"
                                );
                            }

                            int quantity = entry.getValue();

                            if (quantity < 1 ||
                                    quantity > rs.getInt("stock")) {
                                throw new SQLException(
                                        "Insufficient stock"
                                );
                            }

                            total = total.add(
                                    rs.getBigDecimal("price")
                                            .multiply(BigDecimal.valueOf(quantity))
                            );
                        }
                    }
                }

                String orderSql = """
                    INSERT INTO orders
                    (user_id, total_amount, shipping_address,
                     payment_method, status)
                    VALUES (?, ?, ?, 'COD', 'PLACED')
                    """;

                int orderId;

                try (PreparedStatement ps = con.prepareStatement(
                        orderSql, Statement.RETURN_GENERATED_KEYS)) {

                    // Demo customer; replace with logged-in user ID.
                    ps.setInt(1, 1);
                    ps.setBigDecimal(2, total);
                    ps.setString(3, address);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Could not create order"
                            );
                        }

                        orderId = keys.getInt(1);
                    }
                }

                String itemSql = """
                    INSERT INTO order_items
                    (order_id, product_id, quantity, unit_price)
                    VALUES (?, ?, ?, ?)
                    """;

                String updateStock = """
                    UPDATE products
                    SET stock = stock - ?
                    WHERE product_id = ? AND stock >= ?
                    """;

                for (Map.Entry<Integer, Integer> entry
                        : cart.entrySet()) {

                    int productId = entry.getKey();
                    int quantity = entry.getValue();

                    BigDecimal price;

                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT price FROM products WHERE product_id=?")) {
                        ps.setInt(1, productId);

                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException(
                                        "Product unavailable"
                                );
                            }

                            price = rs.getBigDecimal("price");
                        }
                    }

                    try (PreparedStatement ps =
                                 con.prepareStatement(itemSql)) {
                        ps.setInt(1, orderId);
                        ps.setInt(2, productId);
                        ps.setInt(3, quantity);
                        ps.setBigDecimal(4, price);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps =
                                 con.prepareStatement(updateStock)) {
                        ps.setInt(1, quantity);
                        ps.setInt(2, productId);
                        ps.setInt(3, quantity);

                        if (ps.executeUpdate() != 1) {
                            throw new SQLException(
                                    "Stock changed; please try again"
                            );
                        }
                    }
                }

                con.commit();
                cart.clear();

                JsonObject result = new JsonObject();
                result.addProperty("message", "Order placed successfully");
                result.addProperty("orderId", orderId);
                result.addProperty("total", total);

                response.getWriter().write(result.toString());

            } catch (Exception e) {
                con.rollback();
                response.setStatus(400);

                JsonObject error = new JsonObject();
                error.addProperty("error", "Checkout failed");
                response.getWriter().write(error.toString());

                log("Checkout failed", e);

            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException e) {
            response.setStatus(500);
            response.getWriter().write(
                    "{\"error\":\"Database error\"}"
            );
            log("Database connection failed", e);
        }
    }
}

