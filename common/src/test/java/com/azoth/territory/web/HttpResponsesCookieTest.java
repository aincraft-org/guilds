package com.azoth.territory.web;

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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponsesCookieTest {

    @Test
    void cookieParsesNamedValueFromHeader() {
        FakeExchange exchange = new FakeExchange();
        exchange.requestHeaders.add("Cookie", "foo=bar; AZOTH_SESSION=abc123; other=1");
        Optional<String> v = HttpResponses.cookie(exchange, SessionStore.COOKIE_NAME);
        assertEquals(Optional.of("abc123"), v);
    }

    @Test
    void setCookieWritesHttpOnlySameSite() {
        FakeExchange exchange = new FakeExchange();
        HttpResponses.setCookie(exchange, SessionStore.COOKIE_NAME, "sid", 3600, true);
        List<String> set = exchange.responseHeaders.get("Set-Cookie");
        assertEquals(1, set.size());
        String c = set.get(0);
        assertTrue(c.startsWith("AZOTH_SESSION=sid"));
        assertTrue(c.contains("HttpOnly"));
        assertTrue(c.contains("SameSite=Strict"));
        assertTrue(c.contains("Max-Age=3600"));
        assertTrue(c.contains("Secure"));
        assertTrue(c.contains("Path=/"));
    }

    /** Minimal HttpExchange stub for cookie helpers. */
    private static final class FakeExchange extends HttpExchange {
        final Headers requestHeaders = new Headers();
        final Headers responseHeaders = new Headers();
        private int code;

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return URI.create("/");
        }

        @Override
        public String getRequestMethod() {
            return "GET";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return new ByteArrayOutputStream();
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
            this.code = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 1);
        }

        @Override
        public int getResponseCode() {
            return code;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 2);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
