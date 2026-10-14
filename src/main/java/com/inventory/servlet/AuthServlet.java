package com.inventory.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.inventory.config.DBConnection;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * AuthServlet - Handles User Authentication (/api/auth)
 * Supports Admin and Cashier roles with MySQL persistence
 * and fallback default credentials for zero-setup demo environments.
 */
@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String action = req.getParameter("action");
        if ("logout".equalsIgnoreCase(action)) {
            if (req.getSession(false) != null) {
                req.getSession().invalidate();
            }
            out.print("{\"success\":true,\"message\":\"Logged out successfully\"}");
            return;
        }

        try {
            String body = readRequestBody(req);
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json == null || !json.has("username") || !json.has("password")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Username and password are required\"}");
                return;
            }

            String username = json.get("username").getAsString().trim();
            String password = json.get("password").getAsString().trim();
            String requestedRole = json.has("role") ? json.get("role").getAsString() : "cashier";

            // 1. Try checking MySQL database if available
            JsonObject userObj = checkDatabaseCredentials(username, password);

            // 2. If not found in DB or DB offline, verify against standard demo accounts
            if (userObj == null) {
                userObj = checkFallbackCredentials(username, password, requestedRole);
            }

            if (userObj != null) {
                req.getSession(true).setAttribute("user", username);
                resp.setStatus(HttpServletResponse.SC_OK);
                out.print(userObj.toString());
            } else {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"error\":\"Invalid username or password. Demo: admin / admin123\"}");
            }

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Authentication server error: " + e.getMessage() + "\"}");
        }
    }

    private JsonObject checkDatabaseCredentials(String username, String password) {
        String sql = "SELECT username, full_name, role FROM users WHERE LOWER(username) = LOWER(?) AND password_hash = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    JsonObject user = new JsonObject();
                    user.addProperty("username", rs.getString("username"));
                    user.addProperty("fullName", rs.getString("full_name"));
                    user.addProperty("role", rs.getString("role"));
                    user.addProperty("authenticated", true);
                    return user;
                }
            }
        } catch (Exception ignored) {
            // Database might not have users table or is offline; fallback seamlessly
        }
        return null;
    }

    private JsonObject checkFallbackCredentials(String username, String password, String requestedRole) {
        String lowerUser = username.toLowerCase();
        if ("admin".equals(lowerUser) && "admin123".equals(password)) {
            JsonObject user = new JsonObject();
            user.addProperty("username", "admin");
            user.addProperty("fullName", "Suraj Gusain (Store Admin)");
            user.addProperty("role", "admin");
            user.addProperty("authenticated", true);
            return user;
        } else if ("cashier".equals(lowerUser) && "cashier123".equals(password)) {
            JsonObject user = new JsonObject();
            user.addProperty("username", "cashier");
            user.addProperty("fullName", "Saurabh Mishra (POS Cashier)");
            user.addProperty("role", "cashier");
            user.addProperty("authenticated", true);
            return user;
        } else if ("siddhesh".equals(lowerUser) && "password".equals(password)) {
            JsonObject user = new JsonObject();
            user.addProperty("username", "siddhesh");
            user.addProperty("fullName", "Siddhesh Jagtap (Database Architect)");
            user.addProperty("role", "admin");
            user.addProperty("authenticated", true);
            return user;
        }
        return null;
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
}
