package com.filemanager.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {
    private static final String USER = Config.value("FILEMANAGER_ADMIN_USERNAME", "admin");

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean ok = req.getSession(false) != null && req.getSession(false).getAttribute("USERNAME") != null;
        String username = ok ? String.valueOf(req.getSession(false).getAttribute("USERNAME")) : "";
        ApiUtil.json(resp, 200, "{\"authenticated\":" + ok + ",\"username\":\"" + ApiUtil.esc(username) + "\"}");
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getPathInfo();
        if ("/login".equals(action)) {
            String configuredPassword = System.getProperty("FILEMANAGER_ADMIN_PASSWORD");
            if (configuredPassword == null || configuredPassword.isBlank()) configuredPassword = System.getenv("FILEMANAGER_ADMIN_PASSWORD");
            if (configuredPassword == null || configuredPassword.isBlank()) {
                ApiUtil.error(resp, 503, "FileManager admin password is not configured. Set FILEMANAGER_ADMIN_PASSWORD.");
                return;
            }
            String u = req.getParameter("login"), p = req.getParameter("password");
            if (USER.equals(u) && configuredPassword.equals(p)) {
                req.getSession(true).setAttribute("USERNAME", u);
                ApiUtil.json(resp, 200, "{\"authenticated\":true,\"username\":\"" + ApiUtil.esc(u) + "\"}");
            } else ApiUtil.error(resp, 401, "Incorrect login or password");
        } else if ("/logout".equals(action)) {
            HttpSession s = req.getSession(false); if (s != null) s.invalidate();
            ApiUtil.json(resp, 200, "{\"authenticated\":false}");
        } else ApiUtil.error(resp, 404, "Unknown auth action");
    }
}
