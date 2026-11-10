package com.debugsw.monitor.api;

import com.debugsw.monitor.service.PlatformService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 管理端服务器、Agent、实例和监控查询接口。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final PlatformService service;

    /** 注入基础平台服务。 */
    public AdminController(PlatformService service) { this.service = service; }

    /** 登记服务器。 */
    @PostMapping("/servers") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createServer(@Valid @RequestBody Requests.Server request) { return service.createServer(request); }

    /** 查询服务器和 Agent 状态。 */
    @GetMapping("/servers")
    public List<Map<String, Object>> servers() { return service.servers(); }

    /** 为服务器注册 Agent 并签发凭据。 */
    @PostMapping("/servers/{serverId}/agent") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> registerAgent(@PathVariable String serverId) { return service.registerAgent(serverId); }

    /** 重新签发 Agent 凭据。 */
    @PostMapping("/servers/{serverId}/agent/token")
    public Map<String, String> rotateAgentToken(@PathVariable String serverId) { return service.rotateAgentToken(serverId); }

    /** 登记受管实例。 */
    @PostMapping("/servers/{serverId}/instances") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createInstance(@PathVariable String serverId, @Valid @RequestBody Requests.Instance request) { return service.createInstance(serverId, request); }

    /** 查询服务器下的实例。 */
    @GetMapping("/servers/{serverId}/instances")
    public List<Map<String, Object>> instances(@PathVariable String serverId) { return service.instances(serverId); }

    /** 查询服务器最近的主机指标。 */
    @GetMapping("/servers/{serverId}/metrics")
    public List<Map<String, Object>> metrics(@PathVariable String serverId, @RequestParam(defaultValue = "100") int limit) { return service.metrics(serverId, limit); }
}
