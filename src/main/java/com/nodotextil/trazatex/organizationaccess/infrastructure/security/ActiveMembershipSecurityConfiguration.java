package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the membership filter as a qualified bean, so {@code SecurityConfig} can add it to
 * the security chain without importing this module's classes.
 */
@Configuration
public class ActiveMembershipSecurityConfiguration {

	public static final String FILTER_BEAN_NAME = "activeMembershipFilter";

	@Bean(FILTER_BEAN_NAME)
	@Qualifier(FILTER_BEAN_NAME)
	Filter activeMembershipFilter(OrganizationAccess organizationAccess) {
		return new ActiveMembershipFilter(organizationAccess);
	}

	/** The filter belongs to the security chain only; keep Boot from registering it twice. */
	@Bean
	FilterRegistrationBean<Filter> activeMembershipFilterRegistration(
			@Qualifier(FILTER_BEAN_NAME) Filter filter) {
		FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}
}
