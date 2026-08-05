package com.azoth.territory.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReverseProxySupportTest {

    @Test
    void trustsForwardedProtoHostAndClientIp() {
        WebConfig cfg = new WebConfig(
                true, "127.0.0.1", 8765, "", true, "", true,
                WebConfig.TlsSettings.disabled()
        );
        ReverseProxySupport proxy = new ReverseProxySupport(cfg);
        FakeExchange ex = new FakeExchange();
        ex.headers.add("X-Forwarded-Proto", "https");
        ex.headers.add("X-Forwarded-Host", "map.example.com");
        ex.headers.add("X-Forwarded-For", "203.0.113.9, 10.0.0.1");

        assertEquals("https", proxy.scheme(ex));
        assertEquals("map.example.com", proxy.host(ex));
        assertEquals("https://map.example.com", proxy.publicOrigin(ex));
        assertTrue(proxy.isSecure(ex));
        assertEquals("203.0.113.9", proxy.clientIp(ex).orElseThrow());
    }

    @Test
    void publicBaseUrlOverridesHostWhenSet() {
        WebConfig cfg = new WebConfig(
                true, "0.0.0.0", 8765, "https://cdn.example.org/map", true, "", true,
                WebConfig.TlsSettings.disabled()
        );
        ReverseProxySupport proxy = new ReverseProxySupport(cfg);
        FakeExchange ex = new FakeExchange();
        ex.headers.add("Host", "127.0.0.1:8765");

        assertEquals("https://cdn.example.org/map", proxy.publicOrigin(ex));
        assertEquals("https", proxy.scheme(ex));
        assertEquals("cdn.example.org", proxy.host(ex));
    }

    @Test
    void withoutTrustProxy_ignoresForwardedHeaders() {
        WebConfig cfg = new WebConfig(
                true, "0.0.0.0", 8765, "", false, "", true,
                WebConfig.TlsSettings.disabled()
        );
        ReverseProxySupport proxy = new ReverseProxySupport(cfg);
        FakeExchange ex = new FakeExchange();
        ex.headers.add("X-Forwarded-Proto", "https");
        ex.headers.add("Host", "localhost:8765");

        assertEquals("http", proxy.scheme(ex));
        assertFalse(proxy.isSecure(ex));
        assertEquals("localhost:8765", proxy.host(ex));
    }

    /** Minimal HttpExchange stub for header tests. */
    static final class FakeExchange extends HttpExchange {
        final Headers headers = new Headers();
        final Headers responseHeaders = new Headers();

        @Override
        public Headers getRequestHeaders() {
            return headers;
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
            return InputStream.nullInputStream();
        }

        @Override
        public OutputStream getResponseBody() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public int getResponseCode() {
            return 200;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8765);
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
