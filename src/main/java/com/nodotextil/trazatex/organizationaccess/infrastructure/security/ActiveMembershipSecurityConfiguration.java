package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ActiveMembershipSecurityConfiguration {

	public static final String FILTER_BEAN_NAME = "activeMembershipFilter";

	@Bean(FILTER_BEAN_NAME)
	@Qualifier(FILTER_BEAN_NAME)
	Filter activeMembershipFilter(OrganizationAccess organizationAccess) {
		return new ActiveMembershipFilter(organizationAccess);
	}

	
	@Bean
	FilterRegistrationBean<Filter> activeMembershipFilterRegistration(
			@Qualifier(FILTER_BEAN_NAME) Filter filter) {
		FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}
}
