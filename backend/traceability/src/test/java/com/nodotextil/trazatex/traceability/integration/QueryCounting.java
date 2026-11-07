package com.nodotextil.trazatex.traceability.integration;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cuenta las consultas que se hacen a Neo4j: envuelve el {@code Neo4jClient} y suma cada llamada a {@code query(...)} (cada una es una consulta). Se activa con
 * {@code @Import(QueryCounting.Config.class)}; la cuenta es global a la JVM, así que se usa en pruebas que hacen una cosa a la vez.
 */
final class QueryCounting {

    /** Las consultas hechas a Neo4j desde el último {@link #reset()}. */
    static final AtomicLong QUERIES = new AtomicLong();

    private QueryCounting() {
    }

    static void reset() {
        QUERIES.set(0);
    }

    static long count() {
        return QUERIES.get();
    }

    @TestConfiguration
    static class Config {
        @Bean
        static BeanPostProcessor neo4jClientQueryCounter() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof Neo4jClient)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(Neo4jClient.class.getClassLoader(), new Class<?>[]{Neo4jClient.class}, (proxy, method, args) -> {
                        if (method.getName().equals("query")) {
                            QUERIES.incrementAndGet();
                        }
                        try {
                            return method.invoke(bean, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
                }
            };
        }
    }
}
