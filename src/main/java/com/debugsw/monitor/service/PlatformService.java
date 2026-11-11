package com.debugsw.monitor.service;

import com.debugsw.monitor.api.Requests;
import com.debugsw.monitor.config.AuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 管理服务器、Agent、受管实例和主机指标。 */
@Service
public class PlatformService {
    private final JdbcTemplate jdbc;
    private final long onlineSeconds;
    private final SecureRandom random = new SecureRandom();

    /** 初始化持久化组件和心跳超时时间。 */
    public PlatformService(JdbcTemplate jdbc, @Value("${monitor.agent-online-seconds}") long onlineSeconds) {
        if (onlineSeconds < 1) throw new IllegalArgumentException("心跳超时时间必须大于零");
        this.jdbc = jdbc;
        this.onlineSeconds = onlineSeconds;
    }

    /** 新增服务器。 */
    public Map<String, String> createServer(Requests.Server request) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ops_server(id, name, address, environment, tags, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, request.name(), request.address(), request.environment(), request.tags() == null ? "" : request.tags(), now());
        return java.util.Collections.singletonMap("id", id);
    }

    /** 查询服务器及 Agent 在线状态。 */
    public List<Map<String, Object>> servers() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT s.id AS \"id\", s.name AS \"name\", s.address AS \"address\", s.environment AS \"environment\", s.tags AS \"tags\", s.created_at AS \"createdAt\", a.id AS \"agentId\", a.version AS \"agentVersion\", a.last_heartbeat_at AS \"lastHeartbeatAt\" FROM ops_server s LEFT JOIN ops_agent a ON a.server_id = s.id ORDER BY s.created_at DESC");
        for (Map<String, Object> row : rows) {
            OffsetDateTime heartbeat = (OffsetDateTime) row.get("lastHeartbeatAt");
            row.put("agentStatus", row.get("agentId") == null ? "UNREGISTERED" : heartbeat != null && heartbeat.toInstant().isAfter(Instant.now().minusSeconds(onlineSeconds)) ? "ONLINE" : "OFFLINE");
        }
        return rows;
    }

    /** 由管理员注册 Agent，返回仅展示一次的令牌。 */
    @Transactional
    public Map<String, String> registerAgent(String serverId) {
        requireServer(serverId);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ops_agent(id, server_id, token_hash, created_at) VALUES (?, ?, ?, ?)", id, serverId, AuthFilter.hash(token), now());
        return agentCredentials(id, token);
    }

    /** 生成包含 Agent 标识和凭据的响应。 */
    private Map<String, String> agentCredentials(String id, String token) {
        Map<String, String> result = new java.util.HashMap<>();
        result.put("agentId", id);
        result.put("token", token);
        return result;
    }

    /** 重新签发 Agent 凭据并立即使旧凭据失效。 */
    public Map<String, String> rotateAgentToken(String serverId) {
        requireServer(serverId);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int count = jdbc.update("UPDATE ops_agent SET token_hash = ?, last_heartbeat_at = NULL WHERE server_id = ?", AuthFilter.hash(token), serverId);
        if (count != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 不存在");
        return java.util.Collections.singletonMap("token", token);
    }

    /** 保存已认证 Agent 的心跳。 */
    public void heartbeat(String agentId, Requests.Heartbeat request) {
        if (jdbc.update("UPDATE ops_agent SET version = ?, last_heartbeat_at = ? WHERE id = ?", request.version(), now(), agentId) != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent 不存在");
        }
    }

    /** 登记指定服务器的受管实例。 */
    public Map<String, String> createInstance(String serverId, Requests.Instance request) {
        requireServer(serverId);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ops_instance(id, server_id, name, type, description, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, serverId, request.name(), request.type(), request.description() == null ? "" : request.description(), now());
        return java.util.Collections.singletonMap("id", id);
    }

    /** 查询指定服务器的受管实例。 */
    public List<Map<String, Object>> instances(String serverId) {
        requireServer(serverId);
        return jdbc.queryForList("SELECT id AS \"id\", server_id AS \"serverId\", name AS \"name\", type AS \"type\", description AS \"description\", created_at AS \"createdAt\" FROM ops_instance WHERE server_id = ? ORDER BY created_at DESC", serverId);
    }

    /** 接收 Agent 的主机指标并使用服务端时间记录接收时间。 */
    public void recordMetric(String agentId, Requests.HostMetric request) {
        if (request.memoryUsedBytes() > request.memoryTotalBytes() || request.diskUsedBytes() > request.diskTotalBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已用量不能超过总量");
        }
        if (request.collectedAt().isAfter(Instant.now().plusSeconds(60))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采集时间不能超过当前时间一分钟");
        }
        int count = jdbc.update("INSERT INTO ops_host_metric(id, server_id, collected_at, received_at, cpu_percent, memory_total_bytes, memory_used_bytes, disk_total_bytes, disk_used_bytes, load_one, network_rx_bytes, network_tx_bytes) SELECT ?, server_id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? FROM ops_agent WHERE id = ?",
                UUID.randomUUID().toString(), request.collectedAt().atOffset(ZoneOffset.UTC), now(), request.cpuPercent(), request.memoryTotalBytes(), request.memoryUsedBytes(), request.diskTotalBytes(), request.diskUsedBytes(), request.loadOne(), request.networkRxBytes(), request.networkTxBytes(), agentId);
        if (count != 1) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent 不存在");
    }

    /** 查询最近的主机指标，按采集时间倒序。 */
    public List<Map<String, Object>> metrics(String serverId, int limit) {
        requireServer(serverId);
        if (limit < 1 || limit > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 500 之间");
        return jdbc.queryForList("SELECT collected_at AS \"collectedAt\", received_at AS \"receivedAt\", cpu_percent AS \"cpuPercent\", memory_total_bytes AS \"memoryTotalBytes\", memory_used_bytes AS \"memoryUsedBytes\", disk_total_bytes AS \"diskTotalBytes\", disk_used_bytes AS \"diskUsedBytes\", load_one AS \"loadOne\", network_rx_bytes AS \"networkRxBytes\", network_tx_bytes AS \"networkTxBytes\" FROM ops_host_metric WHERE server_id = ? ORDER BY collected_at DESC LIMIT ?", serverId, limit);
    }

    /** 校验服务器是否存在。 */
    private void requireServer(String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ops_server WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "服务器不存在");
    }

    /** 获取 UTC 时间。 */
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
