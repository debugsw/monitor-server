package com.debugsw.monitor.api;

import com.debugsw.monitor.service.NginxTaskService;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Nginx 配置任务的管理端与 Agent 接口。 */
@RestController
public class NginxTaskController {
    private final NginxTaskService service;
    /** 注入任务服务。 */
    public NginxTaskController(NginxTaskService service) { this.service = service; }
    /** 创建读取或发布任务。 */
    @PostMapping("/api/admin/servers/{serverId}/nginx/tasks")
    public Map<String, String> create(@PathVariable String serverId, @RequestBody Map<String, String> body) { return service.create(serverId, body); }
    /** 查询任务状态和结果。 */
    @GetMapping("/api/admin/servers/{serverId}/nginx/tasks/{taskId}")
    public Map<String, Object> read(@PathVariable String serverId, @PathVariable String taskId) { return service.read(serverId, taskId); }
    /** 查询最近的配置发布记录。 */
    @GetMapping("/api/admin/servers/{serverId}/nginx/publications")
    public List<Map<String, Object>> publications(@PathVariable String serverId) { return service.publications(serverId); }
    /** 领取属于当前 Agent 的任务。 */
    @PostMapping("/api/agent/nginx-tasks/claim")
    public Map<String, Object> claim(HttpServletRequest request) { return service.claim((String)request.getAttribute("agentId")); }
    /** 保存当前 Agent 的任务结果。 */
    @PostMapping("/api/agent/nginx-tasks/{taskId}/result")
    public void complete(HttpServletRequest request, @PathVariable String taskId, @RequestBody Map<String, String> body) { service.complete((String)request.getAttribute("agentId"), taskId, body); }
}
