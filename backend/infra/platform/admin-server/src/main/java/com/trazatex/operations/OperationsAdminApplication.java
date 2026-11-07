package com.trazatex.operations;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import de.codecentric.boot.admin.server.services.InstanceRegistry;
import de.codecentric.boot.admin.server.domain.values.Registration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.http.client.InetAddressFilter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@SpringBootApplication
@EnableAdminServer
public class OperationsAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(OperationsAdminApplication.class, args);
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http.authorizeExchange(requests -> requests
                .pathMatchers("/actuator/health").permitAll()
                .anyExchange().authenticated())
                .httpBasic(Customizer.withDefaults()).build();
    }

    @Bean
    InetAddressFilter monitoredServices() {
        return address -> {
            for (String service : new String[]{"organization", "production", "traceability", "quality", "notification", "analytics", "gateway"}) {
                try {
                    if (Arrays.asList(InetAddress.getAllByName(service)).contains(address)) return true;
                } catch (UnknownHostException ignored) {
                    continue;
                }
            }
            return false;
        };
    }

    @Bean
    ApplicationRunner registerServices(InstanceRegistry repository) {
        return args -> {
            for (String service : new String[]{"organization", "production", "traceability", "quality", "notification", "analytics", "gateway"}) {
                String url = "http://" + service + ":18080";
                repository.register(Registration.create(service, url + "/actuator")
                        .healthUrl(url + "/actuator/health").managementUrl(url + "/actuator")
                        .serviceUrl(url).build()).block();
            }
        };
    }
}
