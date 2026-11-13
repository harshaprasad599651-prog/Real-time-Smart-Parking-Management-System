package com.smartpark.server.security;

import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.EnumSet;
import java.util.Set;

/**
 * Server-side role-based access control for every {@code /api/**} endpoint.
 * <p>
 * Runs before each controller method:
 * <ol>
 *     <li>{@link PublicEndpoint}: allowed without sign-in;</li>
 *     <li>otherwise the bearer token is checked (missing, expired or revoked: <b>401</b>);</li>
 *     <li>the user is loaded from MongoDB: an inactive account, or a role not listed in
 *         {@link RequireRole}, is refused with <b>403</b>. Because the role is read from the
 *         database on every request, a role change or deactivation takes effect immediately.</li>
 * </ol>
 * The authenticated operator is stored on the request for {@link CurrentOperatorArgumentResolver}.
 * Hiding buttons in the JavaFX client is only a convenience; this interceptor is the real protection.
 */
@Component
public class RoleCheckInterceptor implements HandlerInterceptor {

    static final String ATTRIBUTE = RoleCheckInterceptor.class.getName() + ".operator";
    private static final Set<UserRole> ANY_ROLE = EnumSet.allOf(UserRole.class);

    private final RequestAuthenticator authenticator;
    private final UserService userService;

    public RoleCheckInterceptor(RequestAuthenticator authenticator, UserService userService) {
        this.authenticator = authenticator;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || isPublic(method)) {
            return true;
        }
        AuthenticatedOperator caller = authenticator.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION),
                request.getHeader(RequestAuthenticator.OPERATOR_HEADER));          // 401
        User user = userService.requireOperator(caller.username(), requiredRoles(method)); // 403
        request.setAttribute(ATTRIBUTE, new AuthenticatedOperator(user.getUsername(), user.getRole()));
        return true;
    }

    private static boolean isPublic(HandlerMethod m) {
        return m.hasMethodAnnotation(PublicEndpoint.class)
                || AnnotatedElementUtils.hasAnnotation(m.getBeanType(), PublicEndpoint.class);
    }

    private static Set<UserRole> requiredRoles(HandlerMethod m) {
        RequireRole onMethod = m.getMethodAnnotation(RequireRole.class);
        RequireRole annotation = onMethod != null ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(m.getBeanType(), RequireRole.class);
        if (annotation == null || annotation.value().length == 0) {
            return ANY_ROLE;
        }
        return EnumSet.of(annotation.value()[0], annotation.value());
    }
}
