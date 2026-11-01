package com.boxoffice.cli;

import com.boxoffice.core.update.Release;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub's releases API and download links, on this laptop. Files are served
 * the way GitHub does it: the download link answers with a redirect to where
 * the bytes really are.
 */
final class FakeGitHub implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final List<String> requests = new ArrayList<>();
    private String latest;
    private int latestStatus = 404;

    FakeGitHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    URI api() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    URI download(String name) {
        return api().resolve("/download/" + name);
    }

    /** Publishes a release with these files, plus a correct SHA256SUMS for them. */
    synchronized FakeGitHub publishes(String tag, String notes, Map<String, String> contents) {
        StringBuilder sums = new StringBuilder();
        contents.forEach((name, text) -> {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            files.put(name, bytes);
            sums.append(sha256(bytes)).append("  ").append(name).append('\n');
        });
        files.put(Release.CHECKSUMS, sums.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder assets = new StringBuilder();
        for (String name : files.keySet()) {
            if (!assets.isEmpty()) {
                assets.append(',');
            }
            assets.append("{\"name\":\"").append(name).append("\",\"size\":").append(files.get(name).length)
                    .append(",\"browser_download_url\":\"").append(download(name)).append("\"}");
        }
        latest = "{\"tag_name\":\"" + tag + "\",\"name\":\"Box Office\",\"draft\":false,\"prerelease\":false,"
                + "\"body\":\"" + notes.replace("\n", "\\n") + "\",\"assets\":[" + assets + "]}";
        latestStatus = 200;
        return this;
    }

    /** Swaps a file's bytes after the checksums were written, like a broken or tampered download. */
    synchronized FakeGitHub corrupts(String name) {
        files.put(name, "something else entirely".getBytes(StandardCharsets.UTF_8));
        return this;
    }

    synchronized FakeGitHub removes(String name) {
        files.remove(name);
        return this;
    }

    synchronized FakeGitHub answersLatestWith(int status, String body) {
        latestStatus = status;
        latest = body;
        return this;
    }

    synchronized List<String> requests() {
        return List.copyOf(requests);
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(exchange.getRequestMethod() + " " + path + " " + exchange.getRequestHeaders().getFirst("Accept"));
        if (path.endsWith("/releases/latest")) {
            answer(exchange, latestStatus, latest == null ? "{\"message\":\"Not Found\"}" : latest);
        } else if (path.startsWith("/download/")) {
            exchange.getResponseHeaders().set("Location", "/files/" + path.substring("/download/".length()));
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        } else if (path.startsWith("/files/") && files.containsKey(path.substring("/files/".length()))) {
            answer(exchange, 200, files.get(path.substring("/files/".length())));
        } else {
            answer(exchange, 404, "Not Found");
        }
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        answer(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void answer(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
