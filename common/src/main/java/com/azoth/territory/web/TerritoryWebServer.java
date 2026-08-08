package com.azoth.territory.web;

import com.azoth.territory.influence.InfluenceService;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.persist.PostgresTerritoryStore;
import com.azoth.territory.registry.TerritoryRegistry;
import com.azoth.territory.standing.StandingService;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedded HTTP/HTTPS server for the territory web submodule.
 * <p>
 * Uses the JDK {@link HttpServer}/{@link HttpsServer} (no extra runtime deps).
 * Designed to sit behind a reverse proxy that terminates TLS, or to serve TLS
 * directly from a PKCS12/JKS keystore.
 */
public final class TerritoryWebServer implements AutoCloseable {
    private final WebConfig config;
    private final TerritoryRegistry registry;
    private final TerritoryJson json;
    private final PostgresTerritoryStore store;
    private final Supplier<Optional<InfluenceService>> influenceSupplier;
    private final Supplier<Optional<StandingService>> standingSupplier;
    private final Logger log;

    private HttpServer server;
    private ExecutorService executor;
    private ReverseProxySupport proxy;

    public TerritoryWebServer(
            WebConfig config,
            TerritoryRegistry registry,
            TerritoryJson json,
            PostgresTerritoryStore store,
            Supplier<Optional<InfluenceService>> influenceSupplier,
            Logger log
    ) {
        this(config, registry, json, store, influenceSupplier, Optional::empty, log);
    }

    public TerritoryWebServer(
            WebConfig config,
            TerritoryRegistry registry,
            TerritoryJson json,
            PostgresTerritoryStore store,
            Supplier<Optional<InfluenceService>> influenceSupplier,
            Supplier<Optional<StandingService>> standingSupplier,
            Logger log
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.json = json == null ? new TerritoryJson() : json;
        this.store = Objects.requireNonNull(store, "store");
        this.influenceSupplier = influenceSupplier == null ? Optional::empty : influenceSupplier;
        this.standingSupplier = standingSupplier == null ? Optional::empty : standingSupplier;
        this.log = log == null ? Logger.getLogger("AzothTerritoryWeb") : log;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        this.proxy = new ReverseProxySupport(config);
        InetSocketAddress addr = new InetSocketAddress(config.bindHost(), config.port());

        if (config.https()) {
            HttpsServer https = HttpsServer.create(addr, 0);
            https.setHttpsConfigurator(createHttpsConfigurator());
            server = https;
        } else {
            server = HttpServer.create(addr, 0);
        }

        SessionStore sessions = new SessionStore(
                config.apiToken(), config.sessionTtlSeconds(), java.time.Clock.systemUTC());
        TerritoryApiHandler api = new TerritoryApiHandler(
                config, proxy, registry, json, store, influenceSupplier, standingSupplier, sessions, log
        );

        server.createContext("/api", api);
        server.createContext("/editor", new StaticFileHandler(
                "/editor",
                "com/azoth/territory/web/static/editor",
                config
        ));

        executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "azoth-territory-web");
                    t.setDaemon(true);
                    return t;
                }
        );
        server.setExecutor(executor);
        server.start();

        String scheme = config.https() ? "https" : "http";
        log.info("Territory web listening on " + scheme + "://" + config.bindHost() + ":" + config.port()
                + (config.trustProxy() ? " (trust reverse-proxy headers)" : "")
                + (config.publicBaseUrl().isEmpty() ? "" : " publicBaseUrl=" + config.publicBaseUrl())
                + " editor=/editor/");
    }

    public synchronized void stop() {
        if (server == null) {
            return;
        }
        try {
            server.stop(1);
        } catch (Exception e) {
            log.log(Level.WARNING, "Error stopping web server", e);
        }
        server = null;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        log.info("Territory web stopped");
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return server != null;
    }

    public int port() {
        return config.port();
    }

    public WebConfig config() {
        return config;
    }

    public ReverseProxySupport proxy() {
        return proxy;
    }

    private HttpsConfigurator createHttpsConfigurator() throws IOException {
        WebConfig.TlsSettings tls = config.tls();
        if (!Files.isRegularFile(tls.keystorePath())) {
            throw new IOException("TLS keystore not found: " + tls.keystorePath());
        }
        try {
            KeyStore ks = KeyStore.getInstance(tls.keystoreType());
            try (InputStream in = Files.newInputStream(tls.keystorePath())) {
                ks.load(in, tls.keystorePassword().toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, tls.keyPassword().toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            return new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    SSLContext ctx = getSSLContext();
                    SSLParameters sslParams = ctx.getDefaultSSLParameters();
                    params.setSSLParameters(sslParams);
                }
            };
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to initialize TLS: " + e.getMessage(), e);
        }
    }
}
