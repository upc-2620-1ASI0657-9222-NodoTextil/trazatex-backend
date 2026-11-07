package com.nodotextil.trazatex.notification.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key="cucumber.glue",value="com.nodotextil.trazatex.notification.acceptance")
@ConfigurationParameter(key="cucumber.plugin",value="summary")
public class CucumberIT { }

