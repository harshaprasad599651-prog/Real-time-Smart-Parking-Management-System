package com.smartpark.server.config;

import com.smartpark.server.security.CurrentOperatorArgumentResolver;
import com.smartpark.server.security.RoleCheckInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC customisation:
 * <ul>
 *     <li>role-based access control on every {@code /api/**} endpoint ({@link RoleCheckInterceptor});</li>
 *     <li>injection of the authenticated operator into controller methods.</li>
 * </ul>
 * Swagger UI and the OpenAPI document are outside {@code /api} and stay readable.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final CurrentOperatorArgumentResolver currentOperatorResolver;
    private final RoleCheckInterceptor roleCheckInterceptor;

    public WebConfig(CurrentOperatorArgumentResolver currentOperatorResolver, RoleCheckInterceptor roleCheckInterceptor) {
        this.currentOperatorResolver = currentOperatorResolver;
        this.roleCheckInterceptor = roleCheckInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentOperatorResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleCheckInterceptor).addPathPatterns("/api/**");
    }
}
