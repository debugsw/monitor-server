package com.debugsw.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 保存和读取 Agent 采集的 Nginx 实例快照。 */
@Service
public class NginxSnapshotService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** 注入数据库及 JSON 处理器。 */
    public NginxSnapshotService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    /** 仅允许 Agent 更新自身服务器的快照。 */
    @Transactional
    public void report(String agentId, JsonNode body) throws Exception {
        if (!body.isObject() || !body.path("instances").isArray() || body.path("instances").size() > 100 || !body.path("collectedAt").isTextual() || !body.path("partial").isBoolean())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nginx 快照格式不正确");
        String data = mapper.writeValueAsString(body.path("instances"));
        if (data.length() > 256 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nginx 快照过大");
        Instant collected;
        try { collected = Instant.parse(body.path("collectedAt").asText()); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采集时间格式不正确"); }
        if (collected.isAfter(Instant.now().plusSeconds(60))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采集时间不能超过当前时间一分钟");
        List<String> ids = jdbc.query("SELECT server_id FROM ops_agent WHERE id = ?", (rs, n) -> rs.getString(1), agentId);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent 不存在");
        String serverId = ids.get(0);
        int updated = jdbc.update("UPDATE ops_nginx_snapshot SET collected_at=?, received_at=?, partial=?, instances_json=? WHERE server_id=?", collected.atOffset(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC), body.path("partial").asBoolean(), data, serverId);
        if (updated == 0) jdbc.update("INSERT INTO ops_nginx_snapshot(server_id,collected_at,received_at,partial,instances_json) VALUES(?,?,?,?,?)", serverId, collected.atOffset(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC), body.path("partial").asBoolean(), data);
    }

    /** 按服务器读取最近一次采集结果；从未采集时明确返回空状态。 */
    public Map<String, Object> read(String serverId) throws Exception {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ops_server WHERE id=?", Integer.class, serverId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "服务器不存在");
        List<Map<String, Object>> rows = jdbc.query("SELECT collected_at, received_at, instances_json, partial FROM ops_nginx_snapshot WHERE server_id=?", (rs, n) -> {
            Map<String, Object> result = new HashMap<>();
            result.put("collectedAt", rs.getObject(1)); result.put("receivedAt", rs.getObject(2)); result.put("instancesJson", rs.getString(3)); result.put("partial", rs.getBoolean(4));
            return result;
        }, serverId);
        if (rows.isEmpty()) { Map<String, Object> empty = new HashMap<>(); empty.put("collectedAt", null); empty.put("receivedAt", null); empty.put("instances", Collections.emptyList()); return empty; }
        Map<String, Object> row = rows.get(0);
        row.put("instances", mapper.readTree((String) row.remove("instancesJson")));
        return row;
    }
}
