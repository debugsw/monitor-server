package com.debugsw.monitor.api;

import com.debugsw.monitor.service.PlatformService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Agent 心跳和主机指标上报接口。 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final PlatformService service;

    /** 注入基础平台服务。 */
    public AgentController(PlatformService service) { this.service = service; }

    /** 更新当前 Agent 的心跳。 */
    @PostMapping("/heartbeat") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(HttpServletRequest request, @Valid @RequestBody Requests.Heartbeat body) {
        service.heartbeat((String) request.getAttribute("agentId"), body);
    }

    /** 上报当前 Agent 所属服务器的主机指标。 */
    @PostMapping("/host-metrics") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void metric(HttpServletRequest request, @Valid @RequestBody Requests.HostMetric body) {
        service.recordMetric((String) request.getAttribute("agentId"), body);
    }
}
