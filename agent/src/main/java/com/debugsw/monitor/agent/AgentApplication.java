package com.debugsw.monitor.agent;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
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
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                scheduler.shutdownNow();
                try { scheduler.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "agent-shutdown"));
            scheduler.scheduleWithFixedDelay(() -> sendSafely(config, "heartbeat", "{\"version\":\"" + VERSION + "\"}"), 0, config.interval().getSeconds(), TimeUnit.SECONDS);
            scheduler.scheduleWithFixedDelay(() -> {
                try { sendSafely(config, "host-metrics", collector.collect()); }
                catch (Exception e) { LOG.log(Level.WARNING, "采集主机指标失败，将在下一周期重试：{0}", e.getMessage()); }
            }, config.interval().getSeconds(), config.interval().getSeconds(), TimeUnit.SECONDS);
            LOG.info("Agent 已启动，心跳和指标采集周期为 " + config.interval().getSeconds() + " 秒");
        } catch (Exception e) {
            LOG.severe("Agent 启动失败：" + e.getMessage());
            System.exit(1);
        }
    }

    /** 上报数据，失败时不记录令牌或响应正文，下一周期继续尝试。 */
    private static void sendSafely(AgentConfig config, String path, String body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) config.server().resolve("api/agent/" + path).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout((int) config.timeout().toMillis());
            connection.setReadTimeout((int) config.timeout().toMillis());
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + config.token());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            if (status != 204) LOG.warning("上报 " + path + " 失败，HTTP 状态码：" + status + "，将在下一周期重试");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "上报 " + path + " 失败，将在下一周期重试：{0}", e.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
