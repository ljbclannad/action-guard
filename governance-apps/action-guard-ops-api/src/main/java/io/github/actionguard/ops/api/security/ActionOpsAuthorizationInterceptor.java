package io.github.actionguard.ops.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

public class ActionOpsAuthorizationInterceptor implements HandlerInterceptor {

    private final ObjectProvider<ActionOpsPrincipalResolver> principalResolverProvider;

    public ActionOpsAuthorizationInterceptor(ObjectProvider<ActionOpsPrincipalResolver> principalResolverProvider) {
        this.principalResolverProvider = principalResolverProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        ActionOpsPermission requiredPermission = requiredPermission(request);
        Optional<ActionOpsPrincipal> principal = resolvePrincipal(request);
        if (principal.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "治理 API 需要已认证身份");
            return false;
        }
        if (requiredPermission == null || !principal.get().hasPermission(requiredPermission)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "当前身份没有治理操作权限");
            return false;
        }
        request.setAttribute(ActionOpsRequestAttributes.PRINCIPAL, principal.get());
        return true;
    }

    private Optional<ActionOpsPrincipal> resolvePrincipal(HttpServletRequest request) {
        ActionOpsPrincipalResolver resolver = principalResolverProvider.getIfAvailable();
        return resolver == null ? Optional.empty() : resolver.resolve(request);
    }

    private ActionOpsPermission requiredPermission(HttpServletRequest request) {
        if (HttpMethod.GET.matches(request.getMethod())) {
            return ActionOpsPermission.READ;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.endsWith("/retry")) {
            return ActionOpsPermission.RETRY;
        }
        if (path.endsWith("/cancel")) {
            return ActionOpsPermission.CANCEL;
        }
        if (path.endsWith("/skip")) {
            return ActionOpsPermission.SKIP;
        }
        if (path.endsWith("/compensate")) {
            return ActionOpsPermission.COMPENSATE;
        }
        return null;
    }
}
