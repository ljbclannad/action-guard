package io.github.actionguard.ops.api.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 将接入应用已验证的 HTTP 身份转换为治理操作身份。
 *
 * <p>实现可以桥接 Spring Security、JWT、网关可信身份或企业 SSO。返回空表示当前请求未认证。</p>
 */
public interface ActionOpsPrincipalResolver {

    Optional<ActionOpsPrincipal> resolve(HttpServletRequest request);
}
