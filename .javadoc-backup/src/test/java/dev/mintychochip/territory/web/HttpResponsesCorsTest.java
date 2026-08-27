package dev.mintychochip.territory.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpResponsesCorsTest {

    @Test
    void noCorsWhenDisabled() {
        WebConfig config = new WebConfig(false, "127.0.0.1", 8765, "",
                false, "", false, WebConfig.TlsSettings.disabled(), "", WebConfig.DEFAULT_SESSION_TTL_SECONDS);
        FakeExchange exchange = new FakeExchange();
        exchange.requestHeaders.add("Origin", "https://evil.example");
        HttpResponses.applyCors(exchange, config);
        assertNull(exchange.responseHeaders.getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void noCorsWithoutConfiguredPublicBaseUrl() {
        WebConfig config = new WebConfig(true, "127.0.0.1", 8765, "",
                false, "tok", true, WebConfig.TlsSettings.disabled(), "", WebConfig.DEFAULT_SESSION_TTL_SECONDS);
        FakeExchange exchange = new FakeExchange();
        exchange.requestHeaders.add("Origin", "https://example.com");
        HttpResponses.applyCors(exchange, config);
        assertNull(exchange.responseHeaders.getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void allowsOnlyConfiguredPublicBaseUrl() {
        WebConfig config = new WebConfig(true, "127.0.0.1", 8765, "https://example.com/editor",
                false, "tok", true, WebConfig.TlsSettings.disabled(), "", WebConfig.DEFAULT_SESSION_TTL_SECONDS);
        FakeExchange exchange = new FakeExchange();
        exchange.requestHeaders.add("Origin", "https://example.com");
        HttpResponses.applyCors(exchange, config);
        assertEquals("https://example.com", exchange.responseHeaders.getFirst("Access-Control-Allow-Origin"));
        assertEquals("Origin", exchange.responseHeaders.getFirst("Vary"));
    }

    @Test
    void rejectsMismatchedOrigin() {
        WebConfig config = new WebConfig(true, "127.0.0.1", 8765, "https://example.com/editor",
                false, "tok", true, WebConfig.TlsSettings.disabled(), "", WebConfig.DEFAULT_SESSION_TTL_SECONDS);
        FakeExchange exchange = new FakeExchange();
        exchange.requestHeaders.add("Origin", "https://evil.example");
        HttpResponses.applyCors(exchange, config);
        assertNull(exchange.responseHeaders.getFirst("Access-Control-Allow-Origin"));
    }

    /** Minimal HttpExchange stub. */
    private static final class FakeExchange extends HttpExchange {
        final Headers requestHeaders = new Headers();
        final Headers responseHeaders = new Headers();
        private int code;

        @Override
        public Headers getRequestHeaders() { return requestHeaders; }

        @Override
        public Headers getResponseHeaders() { return responseHeaders; }

        @Override
        public URI getRequestURI() { return URI.create("/"); }

        @Override
        public String getRequestMethod() { return "GET"; }

        @Override
        public HttpContext getHttpContext() { return null; }

        @Override
        public void close() { }

        @Override
        public InputStream getRequestBody() { return new ByteArrayInputStream(new byte[0]); }

        @Override
        public OutputStream getResponseBody() { return new ByteArrayOutputStream(); }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException { this.code = rCode; }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 1);
        }

        @Override
        public int getResponseCode() { return code; }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 2);
        }

        @Override
        public String getProtocol() { return "HTTP/1.1"; }

        @Override
        public Object getAttribute(String name) { return null; }

        @Override
        public void setAttribute(String name, Object value) { }

        @Override
        public void setStreams(InputStream i, OutputStream o) { }

        @Override
        public HttpPrincipal getPrincipal() { return null; }
    }
}
