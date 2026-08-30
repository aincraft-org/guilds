package org.aincraft.guilds.territory.persist;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Presents the connection factory owned by Jdbi as the legacy JDBC {@link DataSource} contract.
 *
 * <p>The Guilds service layer still uses JDBC statements and callbacks.  The adapter keeps that
 * contract intact while ensuring every borrowed connection closes its Jdbi handle, and therefore
 * returns the underlying connection to the single utility-managed Hikari pool.</p>
 */
final class JdbiDataSource implements DataSource {
    private final Jdbi jdbi;
    private volatile PrintWriter logWriter;
    private volatile int loginTimeout;

    JdbiDataSource(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    @Override
    public Connection getConnection() throws SQLException {
        final Handle handle;
        try {
            handle = jdbi.open();
        } catch (RuntimeException exception) {
            throw asSqlException("Failed to open SQL connection", exception);
        }
        try {
            Connection connection = handle.getConnection();
            InvocationHandler handler = new HandleConnection(handle, connection);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
        } catch (RuntimeException exception) {
            handle.close();
            throw asSqlException("Failed to open SQL connection", exception);
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // Credentials are configured on the shared Hikari pool; opening a second credentialed
        // connection would bypass the pool and violate the one-pool lifecycle.
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds must not be negative");
        }
        loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(JdbiDataSource.class.getName());
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        Objects.requireNonNull(iface, "iface");
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Jdbi data source does not wrap " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface != null && iface.isInstance(this);
    }

    private static SQLException asSqlException(String message, RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException;
            }
            cause = cause.getCause();
        }
        return new SQLException(message, exception);
    }

    private static final class HandleConnection implements InvocationHandler {
        private final Handle handle;
        private final Connection delegate;
        private boolean closed;

        private HandleConnection(Handle handle, Connection delegate) {
            this.handle = handle;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("close") && method.getParameterCount() == 0) {
                close();
                return null;
            }
            if (name.equals("isClosed") && method.getParameterCount() == 0) {
                return closed || handle.isClosed() || delegate.isClosed();
            }
            if (name.equals("abort") && method.getParameterCount() == 1) {
                Throwable failure = null;
                try {
                    method.invoke(delegate, args);
                } catch (InvocationTargetException exception) {
                    failure = exception.getCause();
                } catch (Throwable exception) {
                    failure = exception;
                }
                try {
                    close();
                } catch (Throwable closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
                if (failure != null) {
                    throw failure;
                }
                return null;
            }
            if (name.equals("unwrap") && method.getParameterCount() == 1) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    return proxy;
                }
            }
            if (name.equals("isWrapperFor") && method.getParameterCount() == 1) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    return true;
                }
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }

        private void close() throws SQLException {
            if (!closed) {
                closed = true;
                try {
                    handle.close();
                } catch (RuntimeException exception) {
                    throw asSqlException("Failed to close SQL connection", exception);
                }
            }
        }
    }
}
