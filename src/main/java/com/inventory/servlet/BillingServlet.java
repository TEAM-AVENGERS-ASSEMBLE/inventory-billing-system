package com.inventory.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.inventory.dao.SaleDAO;
import com.inventory.model.Sale;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * BillingServlet - Receipt Generator & Instant Stock Deductor
 * Implements the core flow from Slide 5:
 * "Each sale runs through one flow: the Servlet reads the order, JDBC updates MySQL,
 *  stock is deducted, and a receipt is generated and sent back — all in a single request."
 */
@WebServlet("/api/billing")
public class BillingServlet extends HttpServlet {

    private final SaleDAO saleDAO = new SaleDAO();
    private final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

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

        try {
            // 1. Read the order from the billing screen request body
            String body = readRequestBody(req);
            Sale saleRequest = gson.fromJson(body, Sale.class);

            if (saleRequest == null || saleRequest.getItems() == null || saleRequest.getItems().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Cart is empty. Please select at least one item.\"}");
                return;
            }

            // 2. Execute single atomic transaction:
            //    - Validate stock
            //    - Deduct quantity via JDBC in MySQL
            //    - Persist invoice & line items
            //    - Generate receipt
            Sale completedSale = saleDAO.processSaleTransaction(saleRequest);

            // 3. Return completed instant receipt JSON
            resp.setStatus(HttpServletResponse.SC_CREATED);
            out.print(gson.toJson(completedSale));

        } catch (IllegalStateException e) {
            // Stock shortage or validation failure
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            out.print("{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Transaction failed: " + escape(e.getMessage()) + "\"}");
        }
    }

    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
