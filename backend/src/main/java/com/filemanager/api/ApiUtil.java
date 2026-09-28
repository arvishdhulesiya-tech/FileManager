package com.filemanager.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ApiUtil {
    private ApiUtil() {}
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
    static void json(HttpServletResponse r, int status, String body) throws IOException {
        r.setStatus(status); r.setContentType("application/json;charset=UTF-8");
        r.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
    static void error(HttpServletResponse r, int status, String message) throws IOException {
        json(r,status,"{\"error\":\""+esc(message)+"\"}");
    }
}
