package com.smartpark.server.security;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets a controller method simply declare an {@link AuthenticatedOperator} parameter:
 * <pre>
 * public ParkingSessionDto entry(AuthenticatedOperator operator, @Valid @RequestBody ParkingEntryRequest body)
 * </pre>
 * If the request carries no valid credentials, the method is never called and the client
 * gets 401 UNAUTHENTICATED.
 */
@Component
public class CurrentOperatorArgumentResolver implements HandlerMethodArgumentResolver {

    private final RequestAuthenticator authenticator;

    public CurrentOperatorArgumentResolver(RequestAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedOperator.class.equals(parameter.getParameterType());
    }

    @Override
    public AuthenticatedOperator resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                 NativeWebRequest request, WebDataBinderFactory binderFactory) {
        return authenticator.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION),
                request.getHeader(RequestAuthenticator.OPERATOR_HEADER));
    }
}
