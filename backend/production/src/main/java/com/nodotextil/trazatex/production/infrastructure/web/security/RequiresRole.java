package com.nodotextil.trazatex.production.infrastructure.web.security;

import com.nodotextil.trazatex.production.application.dto.Role;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresRole {

    Role[] value();
}
