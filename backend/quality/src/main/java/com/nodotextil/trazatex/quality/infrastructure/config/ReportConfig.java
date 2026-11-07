package com.nodotextil.trazatex.quality.infrastructure.config;

import com.nodotextil.trazatex.quality.application.port.out.PdfGeneratorPort;
import com.nodotextil.trazatex.quality.infrastructure.external.OpenHtmlToPdfAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;

/** El reporte PDF (US44, ADR-011): el generador local y la cabecera {@code Cache-Control: no-store} de todo lo que sale por su ruta. */
@Configuration
class ReportConfig {

    private static final Pattern REPORT_PATH = Pattern.compile("^/api/quality/controls/[^/]+/report/?$");

    @Bean
    PdfGeneratorPort pdfGenerator(@Value("${trazatex.report.timeout:PT10S}") Duration timeout, @Value("${trazatex.report.max-concurrent:4}") int maxConcurrent) {
        return new OpenHtmlToPdfAdapter(timeout, maxConcurrent);
    }

    /**
     * Lo que se ve depende de quien pide (empresa y rol) y es un documento para compartir: ningún caché compartido debe guardarlo. La cabecera se pone
     * <b>antes</b> de la cadena, así que también la llevan los errores (401, 403, 404, 409, 503).
     */
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> reportNoStoreFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                if (REPORT_PATH.matcher(request.getRequestURI()).matches()) {
                    response.setHeader("Cache-Control", "no-store");
                    response.setHeader("Pragma", "no-cache");
                }
                chain.doFilter(request, response);
            }
        });
        registration.addUrlPatterns("/api/quality/controls/*");
        registration.setName("reportNoStoreFilter");
        registration.setOrder(0);
        return registration;
    }
}
