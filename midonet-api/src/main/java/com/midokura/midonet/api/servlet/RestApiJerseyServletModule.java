/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.servlet;

import com.midokura.midonet.api.auth.AuthFilter;
import com.midokura.midonet.api.auth.AuthModule;
import com.midokura.midonet.api.auth.cors.CrossOriginResourceSharingFilter;
import com.midokura.midonet.api.config.ConfigurationModule;
import com.midokura.midonet.api.error.ExceptionFilter;
import com.midokura.midonet.api.filter.FilterModule;
import com.midokura.midonet.api.network.NetworkModule;
import com.midokura.midonet.api.rest_api.RestApiModule;
import com.midokura.midonet.api.serialization.SerializationModule;
import com.midokura.midolman.guice.MonitoringStoreModule;
import com.midokura.midolman.guice.cluster.DataClusterClientModule;
import com.midokura.midolman.guice.reactor.ReactorModule;
import com.midokura.midonet.api.auth.AuthContainerRequestFilter;
import com.midokura.midonet.api.error.ErrorModule;
import com.midokura.midonet.api.validation.ValidationModule;
import com.midokura.midonet.api.zookeeper.ZookeeperModule;
import com.sun.jersey.api.container.filter.RolesAllowedResourceFilterFactory;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Jersey servlet module for MidoNet REST API application.
 */
public class RestApiJerseyServletModule extends JerseyServletModule {

    private final static Logger log = LoggerFactory
            .getLogger(RestApiJerseyServletModule.class);

    private final ServletContext servletContext;
    private final static Map<String, String> servletParams = new
            HashMap<String, String>();
    static {
        servletParams.put(ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS,
                AuthContainerRequestFilter.class.getName());
        servletParams.put(ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS,
                ExceptionFilter.class.getName());
        servletParams.put(ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES,
                RolesAllowedResourceFilterFactory.class.getName());
    }

    public RestApiJerseyServletModule(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    protected void configureServlets() {
        log.debug("configureServlets: entered");

        install(new ConfigurationModule(servletContext));
        install(new AuthModule());
        install(new ErrorModule());
        install(new RestApiModule());
        install(new SerializationModule());
        install(new ValidationModule());

        // Install Zookeeper module until Cluster Client makes it unnecessary
        install(new ReactorModule()); // Need this for DataClient
        install(new ZookeeperModule());
        install(new DataClusterClientModule());
        install(new MonitoringStoreModule());

        install(new NetworkModule());
        install(new FilterModule());

        // Register filters - the order matters here.  Make sure that CORS
        // filter is registered before Auth because Auth would reject OPTION
        // requests without a token in the header.
        filter("/v1/*").through(CrossOriginResourceSharingFilter.class);
        filter("/*").through(CrossOriginResourceSharingFilter.class);
        filter("/v1/*").through(AuthFilter.class);
        filter("/*").through(AuthFilter.class);

        // Register servlet
        serve("/v1/*").with(GuiceContainer.class, servletParams);
        serve("/*").with(GuiceContainer.class, servletParams);

        log.debug("configureServlets: exiting");
    }

}