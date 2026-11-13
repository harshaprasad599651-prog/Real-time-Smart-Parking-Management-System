package com.smartpark.server.config;

import com.smartpark.server.security.CurrentOperatorArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Spring MVC customisation: injects the authenticated operator into controller methods. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final CurrentOperatorArgumentResolver currentOperatorResolver;

    public WebConfig(CurrentOperatorArgumentResolver currentOperatorResolver) {
        this.currentOperatorResolver = currentOperatorResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentOperatorResolver);
    }
}
