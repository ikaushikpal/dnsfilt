package com.dnsfilt.dnsadmin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SpaWebMvcConfig
 * 
 * Enables dnsfilt-admin-backend to serve compiled Angular SSR / SPA front-end bundles
 * directly from 'classpath:/static/' alongside the REST APIs.
 * 
 * Routing Behavior:
 * 1. Physical Assets (.js, .css, .ico, .png, .svg, .jpg, prerendered html): Served directly with appropriate caching.
 * 2. API Routes (/api/**, /actuator/**): Passed straight through to @RestController without interception.
 * 3. Client-Side SPA Routes (/dashboard, /rules, /clients, /resolvers, /threats, /login):
 *    Transparently forwarded to 'classpath:/static/index.html' (or index.csr.html fallback) so browser refreshes load smoothly.
 */
@Configuration
public class SpaWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        // Never forward API or Actuator requests to index.html
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("api") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }

                        // Forward client-side SPA route navigations to index.html, index.csr.html, or home/index.html
                        Resource index = new ClassPathResource("/static/index.html");
                        if (index.exists() && index.isReadable()) {
                            return index;
                        }
                        Resource csr = new ClassPathResource("/static/index.csr.html");
                        if (csr.exists() && csr.isReadable()) {
                            return csr;
                        }
                        Resource home = new ClassPathResource("/static/home/index.html");
                        if (home.exists() && home.isReadable()) {
                            return home;
                        }

                        return null;
                    }
                });
    }
}
