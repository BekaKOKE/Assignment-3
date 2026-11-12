package com.assignment3.filemanager.web;

import com.assignment3.filemanager.app.FileSystemContext;
import com.assignment3.filemanager.exception.AccessDeniedException;
import com.assignment3.filemanager.exception.ConflictException;
import com.assignment3.filemanager.exception.FileManagerException;
import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.exception.StorageException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 8421;

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; style-src 'self'; "
            + "script-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    private static final Map<String, String> PAGES = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/app.js", "app.js",
            "/style.css", "style.css");

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8");

    private final HttpServer server;
    private final ExecutorService workers;
    private final ApiRouter api;

    public WebServer(FileSystemContext context, AuditTrail trail, int port) throws IOException {
        this.api = new ApiRouter(context, trail);
        this.workers = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "web-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(workers);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            send(exchange, respond(exchange));
        }
    }

    private Response respond(HttpExchange exchange) throws IOException {
        try {
            checkHost(exchange);
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(Request.API_PREFIX)) {
                return page(exchange, path);
            }
            checkOrigin(exchange);
            return api.handle(Request.from(exchange));
        } catch (HttpError e) {
            return Response.error(e.status(), e.getMessage());
        } catch (FileManagerException e) {
            return Response.error(statusOf(e), e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            return Response.error(500, "Internal error.");
        }
    }

    private Response page(HttpExchange exchange, String path) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw HttpError.methodNotAllowed();
        }
        if (path.equals("/favicon.ico")) {
            return Response.noContent();
        }
        String file = PAGES.get(path);
        if (file == null) {
            throw new HttpError(404, "Not found.");
        }
        try (InputStream resource = WebServer.class.getResourceAsStream("/web/" + file)) {
            if (resource == null) {
                throw new HttpError(500, "Missing resource " + file);
            }
            String extension = file.substring(file.lastIndexOf('.') + 1);
            return new Response(200, CONTENT_TYPES.get(extension), resource.readAllBytes());
        }
    }

    // only requests addressed to this machine are served (protects against DNS rebinding)
    private void checkHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        boolean local = ("localhost:" + port()).equalsIgnoreCase(host) || ("127.0.0.1:" + port()).equals(host);
        if (!local) {
            throw new HttpError(403, "Request refused: unexpected Host header.");
        }
    }

    private void checkOrigin(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        String origin = headers.getFirst("Origin");
        boolean changesState = !exchange.getRequestMethod().equals("GET");
        if (changesState && origin != null && !origin.equals("http://" + headers.getFirst("Host"))) {
            throw new HttpError(403, "Cross-origin request refused.");
        }
    }

    private static int statusOf(FileManagerException e) {
        if (e instanceof AccessDeniedException) {
            return 403;
        }
        if (e instanceof NotFoundException) {
            return 404;
        }
        if (e instanceof ConflictException) {
            return 409;
        }
        return e instanceof StorageException ? 500 : 400;
    }

    private void send(HttpExchange exchange, Response response) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        if (response.contentType() != null) {
            headers.set("Content-Type", response.contentType());
        }

        byte[] body = response.body();
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
    }
}
