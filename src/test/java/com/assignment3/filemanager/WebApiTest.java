package com.assignment3.filemanager;

import com.assignment3.filemanager.app.FileSystemContext;
import com.assignment3.filemanager.storage.StorageType;
import com.assignment3.filemanager.web.AuditTrail;
import com.assignment3.filemanager.web.WebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebApiTest {

    private record Reply(int status, String body, String csp) {
    }

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private AuditTrail trail;
    private WebServer server;
    private String base;

    @BeforeEach
    void start(@TempDir Path dir) throws IOException {
        trail = new AuditTrail(null);
        FileSystemContext context = FileSystemContext.open(dir, StorageType.LOCAL, trail);
        server = new WebServer(context, trail, 0);
        server.start();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private Reply call(String method, String path, String user, String form) throws Exception {
        return call(method, path, user, form, null);
    }

    private Reply call(String method, String path, String user, String form, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (user != null) {
            request.header("X-User", user);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }
        if (form != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded");
            request.method(method, HttpRequest.BodyPublishers.ofString(form));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body(),
                response.headers().firstValue("Content-Security-Policy").orElse(null));
    }

    private static String form(String... keysAndValues) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) {
                out.append('&');
            }
            out.append(URLEncoder.encode(keysAndValues[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(keysAndValues[i + 1], StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static String idOf(Reply reply) {
        Matcher matcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(reply.body());
        assertTrue(matcher.find(), "no id in " + reply.body());
        return matcher.group(1);
    }

    private String createFile(String user, String name, String content) throws Exception {
        Reply created = call("POST", "/api/files", user, form("name", name, "type", "txt", "content", content));
        assertEquals(201, created.status(), created.body());
        return idOf(created);
    }

    private String rawStatusLine(String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
        }
    }

    @Test
    @DisplayName("TC-W01 required scenario over HTTP: create, share, second user reads, denied actions change nothing")
    void scenarioOverHttp() throws Exception {
        String id = createFile("beksultan", "plan", "top secret");

        assertEquals(403, call("GET", "/api/files/" + id + "/content", "kuanysh", null).status(),
                "kuanysh has no access before sharing");

        assertEquals(200, call("POST", "/api/files/" + id + "/share", "beksultan", form("target", "ruslan")).status());

        Reply read = call("GET", "/api/files/" + id + "/content", "ruslan", null);
        assertEquals(200, read.status());
        assertTrue(read.body().contains("\"content\":\"top secret\""), read.body());

        Reply rename = call("POST", "/api/files/" + id + "/rename", "ruslan", form("name", "hacked"));
        assertEquals(403, rename.status());
        assertTrue(rename.body().contains("Access denied"), rename.body());
        assertEquals(403, call("DELETE", "/api/files/" + id, "ruslan", null).status());
        assertEquals(403, call("POST", "/api/files/" + id + "/share", "ruslan", form("target", "kuanysh")).status());

        Reply info = call("GET", "/api/files/" + id, "beksultan", null);
        assertTrue(info.body().contains("\"name\":\"plan\""), info.body());
        assertTrue(info.body().contains("\"sharedWith\":[\"ruslan\"]"), info.body());
        assertTrue(call("GET", "/api/files", "ruslan", null).body().contains("\"role\":\"VIEWER\""));
        assertTrue(call("GET", "/api/files", "kuanysh", null).body().contains("\"files\":[]"));

        assertTrue(trail.since(0).stream().anyMatch(e -> !e.granted() && e.text().contains("ruslan -> RENAME")),
                "the proxy's DENIED decision must be visible in the audit trail");
        assertTrue(trail.since(0).stream().anyMatch(e -> e.granted() && e.text().contains("ruslan -> READ")));
    }

    @Test
    @DisplayName("TC-W02 search works through the proxy: case-insensitive for the owner, nothing leaks to others")
    void search() throws Exception {
        createFile("beksultan", "Budget", "x");
        assertTrue(call("GET", "/api/files?q=budg", "beksultan", null).body().contains("\"name\":\"Budget\""));
        assertTrue(call("GET", "/api/files?q=budg", "ruslan", null).body().contains("\"files\":[]"));
    }

    @Test
    @DisplayName("TC-W03 missing login is 401; an unknown user is refused by the proxy (403) and recorded safely")
    void login() throws Exception {
        assertEquals(401, call("GET", "/api/files", null, null).status());

        Reply stranger = call("GET", "/api/files", "mallory<script>", null);
        assertEquals(403, stranger.status());
        assertTrue(trail.since(0).stream().anyMatch(e -> e.text().contains("UNKNOWN USER")));
        assertTrue(trail.since(0).stream().noneMatch(e -> e.text().contains("<")),
                "untrusted names must be sanitised before they reach the log");
    }

    @Test
    @DisplayName("TC-W04 only localhost / 127.0.0.1 Host headers are accepted (DNS-rebinding guard)")
    void hostCheck() throws Exception {
        String good = "GET /api/session HTTP/1.1\r\nHost: localhost:" + server.port() + "\r\nConnection: close\r\n\r\n";
        String evil = "GET /api/session HTTP/1.1\r\nHost: evil.example\r\nConnection: close\r\n\r\n";
        assertTrue(rawStatusLine(good).contains(" 200"), "localhost must work");
        assertTrue(rawStatusLine(evil).contains(" 403"), "a foreign Host must be refused");
    }

    @Test
    @DisplayName("TC-W05 a state-changing request from a foreign Origin is refused and changes nothing")
    void crossOrigin() throws Exception {
        Reply attack = call("POST", "/api/files", "beksultan", form("name", "evil", "type", "txt", "content", "x"),
                "http://evil.example");
        assertEquals(403, attack.status());
        assertTrue(call("GET", "/api/files", "beksultan", null).body().contains("\"files\":[]"));

        Reply same = call("POST", "/api/files", "beksultan", form("name", "fine", "type", "txt", "content", "x"),
                "http://127.0.0.1:" + server.port());
        assertEquals(201, same.status());
    }

    @Test
    @DisplayName("TC-W06 an oversized request body is rejected with 413 before it is read")
    void bodyLimit() throws Exception {
        String status = rawStatusLine("POST /api/files HTTP/1.1\r\nHost: 127.0.0.1:" + server.port()
                + "\r\nX-User: beksultan\r\nContent-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: 99999999\r\nConnection: close\r\n\r\n");
        assertTrue(status.contains(" 413"), status);
    }

    @Test
    @DisplayName("TC-W07 the page is served with a strict CSP; unknown paths and wrong methods are refused")
    void staticAndRouting() throws Exception {
        Reply page = call("GET", "/", null, null);
        assertEquals(200, page.status());
        assertTrue(page.body().contains("<title>"), "index page");
        assertTrue(page.csp() != null && page.csp().contains("default-src 'self'"), "CSP header");
        assertEquals(200, call("GET", "/app.js", null, null).status());
        assertEquals(200, call("GET", "/style.css", null, null).status());

        assertEquals(404, call("GET", "/web/index.html", null, null).status(), "no direct resource paths");
        assertEquals(404, call("GET", "/%2e%2e/pom.xml", null, null).status());
        assertEquals(404, call("GET", "/api/nothing", "beksultan", null).status());
        assertEquals(405, call("POST", "/api/files/someid", "beksultan", form("a", "b")).status());
        assertEquals(405, call("DELETE", "/", null, null).status());
    }

    @Test
    @DisplayName("TC-W08 validation, not-found and conflict errors map to 400, 404 and 409")
    void errorMapping() throws Exception {
        assertEquals(400, call("POST", "/api/files", "beksultan", form("name", "a/b", "type", "txt", "content", "x")).status());
        assertEquals(400, call("POST", "/api/files", "beksultan", form("name", "ok", "type", "exe", "content", "x")).status());
        createFile("beksultan", "same", "1");
        assertEquals(409, call("POST", "/api/files", "beksultan", form("name", "same", "type", "txt", "content", "2")).status());
        assertEquals(404, call("GET", "/api/files/doesnotexist/content", "beksultan", null).status());
        String id = createFile("beksultan", "other", "1");
        assertEquals(404, call("POST", "/api/files/" + id + "/share", "beksultan", form("target", "nobody")).status());
        assertEquals(400, call("GET", "/api/log?since=abc", "beksultan", null).status());
    }

    @Test
    @DisplayName("TC-W09 BRIDGE over HTTP: switching storage keeps content readable and is logged; strangers cannot switch")
    void switchStorage() throws Exception {
        String id = createFile("beksultan", "keep", "persist me");
        assertTrue(call("GET", "/api/session", null, null).body().contains("\"storage\":\"LOCAL\""));

        assertEquals(403, call("POST", "/api/storage", "mallory", form("type", "database")).status());
        assertEquals(200, call("POST", "/api/storage", "beksultan", form("type", "database")).status());

        assertTrue(call("GET", "/api/session", null, null).body().contains("\"storage\":\"DATABASE\""));
        assertTrue(call("GET", "/api/files/" + id + "/content", "beksultan", null).body().contains("persist me"));
        assertTrue(trail.since(0).stream().anyMatch(e -> e.text().startsWith("[BRIDGE]")));
        assertEquals(400, call("POST", "/api/storage", "beksultan", form("type", "floppy")).status());
    }

    @Test
    @DisplayName("TC-W14 non-ASCII text (Cyrillic, accents, emoji) survives a round trip over HTTP unchanged")
    void unicodeRoundTrip() throws Exception {
        String text = "Привет, ёжик! café 😀";
        String id = createFile("beksultan", "unicode", text);
        Reply read = call("GET", "/api/files/" + id + "/content", "beksultan", null);
        assertEquals(200, read.status());
        assertTrue(read.body().contains(text), read.body());
        assertTrue(call("GET", "/api/files/" + id, "beksultan", null).body()
                .contains("\"size\":" + text.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    @DisplayName("TC-W10 folders over HTTP: non-empty folder cannot be deleted; only its owner may delete it")
    void folders() throws Exception {
        String folder = idOf(call("POST", "/api/folders", "beksultan", form("name", "Work")));
        String file = createFile("beksultan", "doc", "x");
        assertEquals(200, call("POST", "/api/files/" + file + "/move", "beksultan", form("folderId", folder)).status());

        assertEquals(409, call("DELETE", "/api/folders/" + folder, "beksultan", null).status(), "non-empty folder");
        assertEquals(403, call("DELETE", "/api/folders/" + folder, "ruslan", null).status(), "not the owner");

        assertEquals(200, call("POST", "/api/files/" + file + "/move", "beksultan", form("folderId", "")).status());
        assertEquals(200, call("DELETE", "/api/folders/" + folder, "beksultan", null).status());
        assertFalse(call("GET", "/api/folders", "beksultan", null).body().contains(folder));
    }
}
