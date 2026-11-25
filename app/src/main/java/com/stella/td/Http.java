package com.stella.td;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny blocking HTTPS helper (always call from a background thread). */
final class Http {
    private Http() {}

    static final class Res {
        final int code;
        final String body;
        Res(int code, String body) { this.code = code; this.body = body; }
        boolean ok() { return code >= 200 && code < 300; }
    }

    /** used by the offline test harness to point the game at a fake server */
    static String overrideBase;

    static Res request(String method, String url, String body, String contentType) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        String m = method;
        if ("PATCH".equals(method)) {
            // HttpURLConnection has no PATCH; Firebase accepts this override
            m = "POST";
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        }
        c.setRequestMethod(m);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType == null ? "application/json; charset=utf-8" : contentType);
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
        c.disconnect();
        return new Res(code, text);
    }
}
