package com.debugsw.monitor.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 管理 Nginx 配置操作任务及发布记录。 */
@Service
public class NginxTaskService {
    private final JdbcTemplate jdbc;
    /** 注入数据库访问组件。 */
    public NginxTaskService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 提交任务；操作只允许读取或发布，实例由 Agent 本机白名单指定。 */
    @Transactional
    public Map<String, String> create(String serverId, Map<String, String> body) {
        Integer servers = jdbc.queryForObject("SELECT COUNT(*) FROM ops_server WHERE id=?", Integer.class, serverId);
        if (servers == null || servers == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "服务器不存在");
        String instance = body.get("instanceKey"), operation = body.get("operation"), digest = body.get("expectedDigest"), content = body.get("content");
        if (instance == null || !instance.matches("[A-Za-z0-9_-]{1,128}") || !("READ".equals(operation) || "PUBLISH".equals(operation)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "实例标识或操作类型无效");
        if ("PUBLISH".equals(operation) && (digest == null || !digest.matches("[0-9a-f]{64}") || content == null || content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置内容或版本摘要无效");
        String id = UUID.randomUUID().toString(); OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO ops_nginx_task(id, server_id, instance_key, operation, state, expected_digest, config_content, created_at) VALUES(?,?,?,?,?,?,?,?)", id, serverId, instance, operation, "WAITING", digest, content, now);
        if ("PUBLISH".equals(operation)) jdbc.update("INSERT INTO ops_nginx_publish_record(task_id,server_id,instance_key,expected_digest,state,created_at) VALUES(?,?,?,?,?,?)", id, serverId, instance, digest, "WAITING", now);
        return Collections.singletonMap("id", id);
    }

    /** 原子领取一个任务；已领取的任务不会自动重复执行。 */
    @Transactional
    public Map<String, Object> claim(String agentId) {
        List<String> servers = jdbc.query("SELECT server_id FROM ops_agent WHERE id=?", (rs, n) -> rs.getString(1), agentId);
        if (servers.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent 不存在");
        String server = servers.get(0);
        List<String> ids = jdbc.query("SELECT id FROM ops_nginx_task WHERE server_id=? AND state='WAITING' ORDER BY created_at LIMIT 1", (rs,n)->rs.getString(1), server);
        if (ids.isEmpty()) return Collections.emptyMap();
        String id = ids.get(0);
        int count = jdbc.update("UPDATE ops_nginx_task SET state='CLAIMED', claimed_at=? WHERE id=? AND state='WAITING'", OffsetDateTime.now(ZoneOffset.UTC), id);
        if (count == 0) return Collections.emptyMap();
        jdbc.update("UPDATE ops_nginx_publish_record SET state='CLAIMED' WHERE task_id=?", id);
        return jdbc.queryForObject("SELECT id, instance_key, operation, expected_digest, config_content FROM ops_nginx_task WHERE id=?", (rs,n)-> {
            Map<String, Object> item = new HashMap<>(); item.put("id", rs.getString(1)); item.put("instanceKey", rs.getString(2)); item.put("operation", rs.getString(3)); item.put("expectedDigest", rs.getString(4)); item.put("content", rs.getString(5)); return item;
        }, id);
    }

    /** 记录所属 Agent 的执行结果，重复回报不会再次执行任务。 */
    @Transactional
    public void complete(String agentId, String taskId, Map<String, String> result) {
        String state = result.get("state"), message = result.get("message"), content = result.get("content"), digest = result.get("digest");
        if (!Arrays.asList("SUCCEEDED", "FAILED", "UNKNOWN").contains(state) || (message != null && message.length() > 8192) || (content != null && content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) || (digest != null && !digest.matches("[0-9a-f]{64}")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务结果无效");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int count = jdbc.update("UPDATE ops_nginx_task SET state=?,result_message=?,result_content=?,result_digest=?,completed_at=? WHERE id=? AND state='CLAIMED' AND server_id=(SELECT server_id FROM ops_agent WHERE id=?)", state,message,content,digest,now,taskId,agentId);
        if (count == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "任务不存在、已完成或不属于该 Agent");
        jdbc.update("UPDATE ops_nginx_publish_record SET state=?,message=?,result_digest=?,completed_at=? WHERE task_id=?", state,message,digest,now,taskId);
    }

    /** 查询指定服务器最近的配置发布记录。 */
    public List<Map<String, Object>> publications(String serverId) {
        return jdbc.query("SELECT task_id,instance_key,state,message,expected_digest,result_digest,created_at,completed_at FROM ops_nginx_publish_record WHERE server_id=? ORDER BY created_at DESC LIMIT 100", (rs,n) -> {
            Map<String, Object> row = new HashMap<>(); row.put("taskId",rs.getString(1)); row.put("instanceKey",rs.getString(2)); row.put("state",rs.getString(3)); row.put("message",rs.getString(4)); row.put("expectedDigest",rs.getString(5)); row.put("resultDigest",rs.getString(6)); row.put("createdAt",rs.getObject(7)); row.put("completedAt",rs.getObject(8)); return row;
        }, serverId);
    }

    /** 查询指定服务器的单条任务结果。 */
    public Map<String, Object> read(String serverId, String taskId) {
        try {
            return jdbc.queryForObject("SELECT id,instance_key,operation,state,result_message,result_content,result_digest,created_at,claimed_at,completed_at FROM ops_nginx_task WHERE server_id=? AND id=?", (rs,n)-> {
                Map<String,Object> item = new HashMap<>(); item.put("id",rs.getString(1));item.put("instanceKey",rs.getString(2));item.put("operation",rs.getString(3));item.put("state",rs.getString(4));item.put("message",rs.getString(5));item.put("content",rs.getString(6));item.put("digest",rs.getString(7));item.put("createdAt",rs.getObject(8));item.put("claimedAt",rs.getObject(9));item.put("completedAt",rs.getObject(10));return item;
            }, serverId, taskId);
        } catch (EmptyResultDataAccessException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"); }
    }
}
