package com.nodotextil.trazatex.organizationaccess.infrastructure.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.function.Predicate;

public class PublicEndpointMatcher implements Predicate<HttpServletRequest> {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;

    public PublicEndpointMatcher(ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public boolean test(HttpServletRequest request) {
        boolean parsedHere = !ServletRequestPathUtils.hasParsedRequestPath(request);
        try {
            if (parsedHere) {
                ServletRequestPathUtils.parseAndCache(request);
            }
            HandlerExecutionChain chain = handlerMapping.getObject().getHandler(request);
            return chain != null && chain.getHandler() instanceof HandlerMethod method
                    && method.hasMethodAnnotation(PublicEndpoint.class);
        } catch (Exception e) {
            return false;
        } finally {
            if (parsedHere) {
                ServletRequestPathUtils.clearParsedRequestPath(request);
            }
        }
    }
}
