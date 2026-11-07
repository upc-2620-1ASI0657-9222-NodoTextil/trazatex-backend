package com.nodotextil.trazatex.quality.integration;

import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cuenta las sentencias {@code SELECT} que llegan a la base (por {@code prepareStatement}), para probar que un listado no hace una consulta
 * por fila (N+1): el número de sentencias de una página tiene que ser el mismo con 3 filas que con 25.
 */
final class StatementCounter implements BeanPostProcessor {

    static final AtomicInteger SELECTS = new AtomicInteger();

    /** Cuántos SELECT hace {@code action}. */
    static int during(ThrowingRunnable action) throws Exception {
        int before = SELECTS.get();
        action.run();
        return SELECTS.get() - before;
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DataSource dataSource) || Proxy.isProxyClass(bean.getClass())) {
            return bean;
        }
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
            Object result = call(method, dataSource, args);
            return method.getName().equals("getConnection") ? counting((Connection) result) : result;
        });
    }

    private static Connection counting(Connection connection) {
        return (Connection) Proxy.newProxyInstance(StatementCounter.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql && sql.stripLeading().regionMatches(true, 0, "select", 0, 6)) {
                SELECTS.incrementAndGet();
            }
            return call(method, connection, args);
        });
    }

    private static Object call(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
