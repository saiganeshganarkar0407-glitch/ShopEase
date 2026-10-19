package com.shopease;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

@WebServlet("/api/cart")
public class CartServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @SuppressWarnings("unchecked")
    private Map<Integer, Integer> getCart(
            HttpServletRequest request) {

        HttpSession session = request.getSession();

        Map<Integer, Integer> cart =
                (Map<Integer, Integer>) session.getAttribute("cart");

        if (cart == null) {
            cart = new HashMap<>();
            session.setAttribute("cart", cart);
        }

        return cart;
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<Integer, Integer> cart = getCart(request);
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        String sql = """
            SELECT product_id, name, price, stock
            FROM products WHERE product_id = ?
            """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            Iterator<Map.Entry<Integer, Integer>> iterator =
                    cart.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Integer, Integer> entry = iterator.next();

                ps.setInt(1, entry.getKey());

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        iterator.remove();
                        continue;
                    }

                    int quantity = entry.getValue();
                    BigDecimal price = rs.getBigDecimal("price");
                    BigDecimal subtotal = price.multiply(
                            BigDecimal.valueOf(quantity)
                    );

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("product_id"));
                    item.put("name", rs.getString("name"));
                    item.put("price", price);
                    item.put("quantity", quantity);
                    item.put("subtotal", subtotal);

                    items.add(item);
                    total = total.add(subtotal);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("items", items);
            result.put("total", total);

            response.getWriter().write(gson.toJson(result));

        } catch (SQLException e) {
            response.setStatus(500);
            response.getWriter().write(
                    "{\"error\":\"Unable to load cart\"}"
            );
            log("Cart retrieval failed", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            JsonObject body = JsonParser.parseReader(
                    request.getReader()
            ).getAsJsonObject();

            int id = body.get("id").getAsInt();
            int quantity = body.get("quantity").getAsInt();

            if (id <= 0 || quantity < 1 || quantity > 20) {
                response.sendError(400, "Invalid product or quantity");
                return;
            }

            String sql =
                    "SELECT stock FROM products WHERE product_id = ?";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setInt(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        response.sendError(404, "Product not found");
                        return;
                    }

                    int stock = rs.getInt("stock");

                    Map<Integer, Integer> cart = getCart(request);
                    int oldQuantity = cart.getOrDefault(id, 0);

                    if (quantity + oldQuantity > stock) {
                        response.sendError(400, "Not enough stock");
                        return;
                    }

                    cart.put(id, oldQuantity + quantity);
                }
            }

            response.getWriter().write(
                    "{\"message\":\"Product added to cart\"}"
            );

        } catch (Exception e) {
            response.sendError(400, "Invalid cart request");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request,
                            HttpServletResponse response)
            throws IOException {

        String value = request.getParameter("id");

        try {
            int id = Integer.parseInt(value);

            if (id <= 0) {
                response.sendError(400, "Invalid product ID");
                return;
            }

            getCart(request).remove(id);
            response.setContentType("application/json");

            response.getWriter().write(
                    "{\"message\":\"Product removed\"}"
            );

        } catch (NumberFormatException e) {
            response.sendError(400, "Invalid product ID");
        }
    }
}

