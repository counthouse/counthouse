package io.github.counthouse.api.identity;

import com.sun.net.httpserver.HttpServer;
import io.github.counthouse.api.TestKeycloak;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Keycloak's key set endpoint as the API sees it, which a test can take down or make hang. */
final class KeySetStub {

    enum Mode {
        UP,
        DOWN,
        HANGING
    }

    private final HttpServer server;
    private final AtomicInteger fetches = new AtomicInteger();
    private volatile Mode mode;

    KeySetStub(Mode mode) {
        this.mode = mode;
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/certs", exchange -> {
            fetches.incrementAndGet();
            try (exchange) {
                switch (this.mode) {
                    case UP -> {
                        byte[] keys;
                        try (InputStream in = URI.create(TestKeycloak.get().jwkSetUri())
                                .toURL()
                                .openStream()) {
                            keys = in.readAllBytes();
                        }
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, keys.length);
                        exchange.getResponseBody().write(keys);
                    }
                    case DOWN -> exchange.sendResponseHeaders(503, -1);
                    case HANGING -> {
                        try {
                            Thread.sleep(10_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        });
        server.start();
    }

    String uri() {
        return "http://localhost:" + server.getAddress().getPort() + "/certs";
    }

    void mode(Mode mode) {
        this.mode = mode;
    }

    int fetches() {
        return fetches.get();
    }
}
