package com.debugsw.monitor.api;

import com.debugsw.monitor.service.NginxSnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Nginx 实例快照的上报和查询接口。 */
@RestController
public class NginxSnapshotController {
    private final NginxSnapshotService service;

    /** 注入快照服务。 */
    public NginxSnapshotController(NginxSnapshotService service) { this.service = service; }

    /** 接收当前 Agent 的采集快照。 */
    @PostMapping("/api/agent/nginx-snapshot") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(HttpServletRequest request, @RequestBody JsonNode body) throws Exception { service.report((String) request.getAttribute("agentId"), body); }

    /** 返回指定服务器的最近快照。 */
    @GetMapping("/api/admin/servers/{serverId}/nginx")
    public Map<String, Object> read(@PathVariable String serverId) throws Exception { return service.read(serverId); }
}
