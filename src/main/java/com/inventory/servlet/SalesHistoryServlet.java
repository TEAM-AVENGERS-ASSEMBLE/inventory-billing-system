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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * SalesHistoryServlet - Searchable Sales Log & Historical Receipt Access
 * Implements: Slide 3 - "Every past receipt searchable by date or item"
 */
@WebServlet("/api/sales")
public class SalesHistoryServlet extends HttpServlet {

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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            String idParam = req.getParameter("id");
            if (idParam != null && !idParam.isEmpty()) {
                Sale sale = saleDAO.getSaleById(Integer.parseInt(idParam));
                if (sale != null) {
                    out.print(gson.toJson(sale));
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.print("{\"error\":\"Receipt not found\"}");
                }
                return;
            }

            String fromDate = req.getParameter("fromDate");
            String toDate = req.getParameter("toDate");
            String itemKeyword = req.getParameter("item");

            List<Sale> history = saleDAO.searchSales(fromDate, toDate, itemKeyword);
            out.print(gson.toJson(history));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
