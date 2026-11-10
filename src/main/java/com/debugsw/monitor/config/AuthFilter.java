package com.debugsw.monitor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** 校验管理员及 Agent 凭据，并将 Agent 身份绑定到请求。 */
@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final String adminToken;

    /** 创建鉴权过滤器；未设置管理员凭据时拒绝启动。 */
    public AuthFilter(JdbcTemplate jdbc, @Value("${monitor.admin-token}") String adminToken) {
        if (adminToken == null || adminToken.length() < 32) {
            throw new IllegalStateException("MONITOR_ADMIN_TOKEN 至少需要 32 个字符");
        }
        this.jdbc = jdbc;
        this.adminToken = adminToken;
    }

    /** 校验接口凭据，禁止匿名访问管理接口。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // 仅允许读取前端入口和构建资源；管理接口仍需单独鉴权。
        if (("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                && ("/".equals(path) || "/index.html".equals(path)
                    || path.matches("/assets/[A-Za-z0-9_-]+\\.(js|css)"))) {
            chain.doFilter(request, response);
            return;
        }
        if (!path.startsWith("/api/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        boolean valid = false;
        if (path.startsWith("/api/admin/")) {
            valid = MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        } else if (path.startsWith("/api/agent/")) {
            List<String> ids = jdbc.query("SELECT id FROM ops_agent WHERE token_hash = ?", (rs, row) -> rs.getString(1), hash(token));
            if (!ids.isEmpty()) {
                request.setAttribute("agentId", ids.get(0));
                valid = true;
            }
        }
        if (!valid) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 计算令牌摘要，数据库不保存令牌明文。 */
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
