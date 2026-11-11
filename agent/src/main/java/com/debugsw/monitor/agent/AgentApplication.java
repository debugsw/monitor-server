package com.debugsw.monitor.agent;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Linux 主机采集 Agent 入口。 */
public class AgentApplication {
    private static final Logger LOG = Logger.getLogger(AgentApplication.class.getName());
    private static final String VERSION = "0.1.0";

    /** 启动独立的心跳与采集任务。 */
    public static void main(String[] args) {
        try {
            AgentConfig config = AgentConfig.fromEnvironment(System.getenv());
            HostCollector collector = new HostCollector(config);
            collector.initialize();
            HttpClient http = HttpClient.newBuilder().connectTimeout(config.timeout()).followRedirects(HttpClient.Redirect.NEVER).build();
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                scheduler.shutdownNow();
                try { scheduler.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "agent-shutdown"));
            scheduler.scheduleWithFixedDelay(() -> sendSafely(http, config, "heartbeat", "{\"version\":\"" + VERSION + "\"}"), 0, config.interval().toSeconds(), TimeUnit.SECONDS);
            scheduler.scheduleWithFixedDelay(() -> {
                try { sendSafely(http, config, "host-metrics", collector.collect()); }
                catch (Exception e) { LOG.log(Level.WARNING, "采集主机指标失败，将在下一周期重试：{0}", e.getMessage()); }
            }, config.interval().toSeconds(), config.interval().toSeconds(), TimeUnit.SECONDS);
            LOG.info("Agent 已启动，心跳和指标采集周期为 " + config.interval().toSeconds() + " 秒");
        } catch (Exception e) {
            LOG.severe("Agent 启动失败：" + e.getMessage());
            System.exit(1);
        }
    }

    /** 上报数据，失败时不记录令牌或响应正文，下一周期继续尝试。 */
    private static void sendSafely(HttpClient client, AgentConfig config, String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(config.server().resolve("api/agent/" + path))
                    .timeout(config.timeout()).header("Authorization", "Bearer " + config.token())
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status != 204) LOG.warning("上报 " + path + " 失败，HTTP 状态码：" + status + "，将在下一周期重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "上报 " + path + " 失败，将在下一周期重试：{0}", e.getClass().getSimpleName());
        }
    }
}
