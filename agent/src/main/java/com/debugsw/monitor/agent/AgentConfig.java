package com.debugsw.monitor.agent;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** 从环境变量读取 Agent 配置。 */
public class AgentConfig {
    private final URI server;
    private final String token;
    private final Duration interval;
    private final Duration timeout;
    private final Path procRoot;
    private final Path diskPath;
    private final String networkInterface;

    /** 初始化 Agent 配置。 */
    private AgentConfig(URI server, String token, Duration interval, Duration timeout, Path procRoot, Path diskPath, String networkInterface) {
        this.server = server; this.token = token; this.interval = interval; this.timeout = timeout;
        this.procRoot = procRoot; this.diskPath = diskPath; this.networkInterface = networkInterface;
    }

    /** 获取服务地址。 */ public URI server() { return server; }
    /** 获取令牌。 */ public String token() { return token; }
    /** 获取采集周期。 */ public Duration interval() { return interval; }
    /** 获取超时时间。 */ public Duration timeout() { return timeout; }
    /** 获取 proc 路径。 */ public Path procRoot() { return procRoot; }
    /** 获取磁盘路径。 */ public Path diskPath() { return diskPath; }
    /** 获取网卡名称。 */ public String networkInterface() { return networkInterface; }
    /** 读取并校验配置，默认禁止向非本机地址明文传输令牌。 */
    public static AgentConfig fromEnvironment(Map<String, String> env) throws IOException {
        String address = required(env, "MONITOR_AGENT_SERVER_URL");
        URI uri = URI.create(address);
        String host = uri.getHost();
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host);
        if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null ||
                !("https".equalsIgnoreCase(uri.getScheme()) || ("http".equalsIgnoreCase(uri.getScheme()) && local))) {
            throw new IllegalArgumentException("服务地址必须为 HTTPS，HTTP 仅允许本机地址，且不能包含用户信息、查询或片段");
        }
        String file = env.getOrDefault("MONITOR_AGENT_TOKEN_FILE", "").trim();
        String token = file.isEmpty() ? required(env, "MONITOR_AGENT_TOKEN") : new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8).trim();
        if (token.trim().isEmpty() || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) throw new IllegalArgumentException("Agent 令牌为空或格式错误");
        long seconds = positive(env, "MONITOR_AGENT_INTERVAL_SECONDS", 5, 3600);
        long timeoutSeconds = positive(env, "MONITOR_AGENT_TIMEOUT_SECONDS", 5, 60);
        String base = uri.toString().replaceAll("/+$", "") + "/";
        Path proc = Paths.get(env.getOrDefault("MONITOR_AGENT_PROC_ROOT", "/proc"));
        Path disk = Paths.get(env.getOrDefault("MONITOR_AGENT_DISK_PATH", "/"));
        if (!Files.isRegularFile(proc.resolve("stat")) || !Files.isDirectory(disk)) throw new IllegalArgumentException("proc 目录或磁盘路径不可用");
        return new AgentConfig(URI.create(base), token, Duration.ofSeconds(seconds), Duration.ofSeconds(timeoutSeconds), proc, disk,
                env.getOrDefault("MONITOR_AGENT_NETWORK_INTERFACE", "").trim());
    }

    /** 获取必填参数。 */
    private static String required(Map<String, String> env, String key) {
        String value = env.getOrDefault(key, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException(key + " 不能为空");
        return value;
    }

    /** 读取范围内的正整数。 */
    private static long positive(Map<String, String> env, String key, long fallback, long max) {
        long value = Long.parseLong(env.getOrDefault(key, Long.toString(fallback)));
        if (value < 1 || value > max) throw new IllegalArgumentException(key + " 必须介于 1 和 " + max);
        return value;
    }
}
