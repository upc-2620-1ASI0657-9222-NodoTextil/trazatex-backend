package com.nodotextil.trazatex.apigateway.infrastructure.revocation;

import com.nodotextil.trazatex.apigateway.ApiGatewayApplication;
import com.nodotextil.trazatex.apigateway.KafkaTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class UserRevocationShutdownIT {

    private static Set<Thread> consumerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().equals(UserRevocationConsumer.THREAD_NAME) && t.isAlive()).collect(Collectors.toSet());
    }

    private static ConfigurableApplicationContext run(String bootstrapServers, String topic, String apiTimeout) {
        return new SpringApplicationBuilder(ApiGatewayApplication.class).run(
                "--server.port=0",
                "--trazatex.jwt.jwks-uri=http://localhost:1/.well-known/jwks.json",
                "--trazatex.gateway.services.organization-access-url=http://localhost:1",
                "--trazatex.revocation.bootstrap-servers=" + bootstrapServers,
                "--trazatex.revocation.topic=" + topic,
                "--trazatex.revocation.api-timeout=" + apiTimeout);
    }

    @Test
    void closingTheContextStopsTheConsumerThreadOfALoadedGateway() {
        String topic = KafkaTestSupport.uniqueTopic();
        KafkaTestSupport.createCompactedTopic(topic, 1);
        Set<Thread> before = consumerThreads();

        ConfigurableApplicationContext context = run(KafkaTestSupport.bootstrap(), topic, "PT5S");
        Set<Thread> mine = consumerThreads();
        mine.removeAll(before);
        assertThat(mine).hasSize(1);
        Thread thread = mine.iterator().next();
        await().atMost(Duration.ofSeconds(10)).until(() -> context.getBean(RevokedUsersRegistry.class).isLoaded());

        context.close();

        assertThat(thread.isAlive()).as("el hilo del consumidor tras cerrar el contexto").isFalse();
    }

    @Test
    void closingTheContextInterruptsAConsumerBlockedWaitingForAnUnreachableKafkaWithoutWaitingForItsTimeout() {
        Set<Thread> before = consumerThreads();
        ConfigurableApplicationContext context = run("localhost:" + KafkaTestSupport.closedPort(), "orgaccess.user", "PT60S");
        Set<Thread> mine = consumerThreads();
        mine.removeAll(before);
        assertThat(mine).hasSize(1);
        Thread thread = mine.iterator().next();
        assertThat(context.getBean(RevokedUsersRegistry.class).isLoaded()).isFalse();

        long started = System.nanoTime();
        context.close();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(15));
        assertThat(thread.isAlive()).isFalse();
    }
}
