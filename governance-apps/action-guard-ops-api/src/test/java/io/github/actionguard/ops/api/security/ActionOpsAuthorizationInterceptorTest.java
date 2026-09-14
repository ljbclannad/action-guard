package io.github.actionguard.ops.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActionOpsAuthorizationInterceptorTest {

    @Test
    void shouldRejectRequestWhenPrincipalResolverIsMissing() throws Exception {
        ActionOpsAuthorizationInterceptor interceptor = interceptor(null);

        MockHttpServletResponse response = invoke(interceptor, "GET", "/api/actions");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldRejectReadWithoutReadPermission() throws Exception {
        ActionOpsAuthorizationInterceptor interceptor = interceptor(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.RETRY)));

        MockHttpServletResponse response = invoke(interceptor, "GET", "/api/actions");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldAllowCommandWithMatchingPermissionAndExposePrincipal() throws Exception {
        ActionOpsPrincipal principal = new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.SKIP));
        ActionOpsAuthorizationInterceptor interceptor = interceptor(principal);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/actions/action-1/skip");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getAttribute(ActionOpsRequestAttributes.PRINCIPAL)).isEqualTo(principal);
    }

    @Test
    void shouldRejectUnknownWriteRoute() throws Exception {
        ActionOpsAuthorizationInterceptor interceptor = interceptor(new ActionOpsPrincipal("operator-1", Set.of(ActionOpsPermission.READ)));

        MockHttpServletResponse response = invoke(interceptor, "POST", "/api/actions/action-1/unknown");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private ActionOpsAuthorizationInterceptor interceptor(ActionOpsPrincipal principal) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (principal != null) {
            beanFactory.registerBeanDefinition("principalResolver", new RootBeanDefinition(ActionOpsPrincipalResolver.class,
                    () -> request -> Optional.of(principal)));
        }
        return new ActionOpsAuthorizationInterceptor(beanFactory.getBeanProvider(ActionOpsPrincipalResolver.class));
    }

    private MockHttpServletResponse invoke(ActionOpsAuthorizationInterceptor interceptor, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        return response;
    }
}
