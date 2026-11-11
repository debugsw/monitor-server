package com.debugsw.monitor.agent;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 从 Linux proc 文件和指定挂载点采集主机指标。 */
public class HostCollector {
    private final AgentConfig config;
    private CpuTicks previous;

    /** 初始化采集器。 */
    public HostCollector(AgentConfig config) { this.config = config; }

    /** 初始化 CPU 基线，后续使用两次累计值计算使用率。 */
    public void initialize() throws IOException { previous = cpuTicks(); }

    /** 采集一次完整指标，任一指标不可用时不发送不完整数据。 */
    public String collect() throws IOException {
        CpuTicks current = cpuTicks();
        CpuTicks old = previous;
        previous = current;
        long totalDelta = current.total - old.total;
        long idleDelta = current.idle - old.idle;
        if (totalDelta <= 0 || idleDelta < 0 || idleDelta > totalDelta) throw new IOException("CPU 计数器变化无效，等待下一采样周期");
        double cpu = (totalDelta - idleDelta) * 100.0 / totalDelta;
        Map<String, Long> memory = memory();
        long total = memory.getOrDefault("MemTotal", 0L);
        long available = memory.getOrDefault("MemAvailable", -1L);
        if (total <= 0 || available < 0 || available > total) throw new IOException("无法读取有效的 MemTotal 或 MemAvailable");
        FileStore store = Files.getFileStore(config.diskPath());
        long diskTotal = store.getTotalSpace(), diskFree = store.getUnallocatedSpace();
        if (diskTotal <= 0 || diskFree < 0 || diskFree > diskTotal) throw new IOException("无法读取有效的磁盘容量");
        double load = Double.parseDouble(new String(Files.readAllBytes(config.procRoot().resolve("loadavg")), java.nio.charset.StandardCharsets.UTF_8).trim().split("\\s+")[0]);
        if (!Double.isFinite(load) || load < 0) throw new IOException("系统负载无效");
        long[] network = network();
        return String.format(Locale.ROOT, "{\"collectedAt\":\"%s\",\"cpuPercent\":%.3f,\"memoryTotalBytes\":%d,\"memoryUsedBytes\":%d,\"diskTotalBytes\":%d,\"diskUsedBytes\":%d,\"loadOne\":%.3f,\"networkRxBytes\":%d,\"networkTxBytes\":%d}",
                Instant.now(), cpu, total, total - available, diskTotal, diskTotal - diskFree, load, network[0], network[1]);
    }

    /** 读取 CPU 累计时间，避免重复计入 guest 时间。 */
    private CpuTicks cpuTicks() throws IOException {
        String[] fields = Files.readAllLines(config.procRoot().resolve("stat")).get(0).trim().split("\\s+");
        if (fields.length < 5 || !"cpu".equals(fields[0])) throw new IOException("无法解析 CPU 数据");
        long total = 0;
        for (int i = 1; i < Math.min(fields.length, 9); i++) total = Math.addExact(total, Long.parseLong(fields[i]));
        long idle = Long.parseLong(fields[4]) + (fields.length > 5 ? Long.parseLong(fields[5]) : 0);
        return new CpuTicks(total, idle);
    }

    /** 读取内存数据并换算为字节。 */
    private Map<String, Long> memory() throws IOException {
        Map<String, Long> values = new HashMap<>();
        for (String line : Files.readAllLines(config.procRoot().resolve("meminfo"))) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 3 && (parts[0].equals("MemTotal:") || parts[0].equals("MemAvailable:"))) {
                if (!parts[2].equals("kB")) throw new IOException("内存单位不受支持");
                values.put(parts[0].substring(0, parts[0].length() - 1), Math.multiplyExact(Long.parseLong(parts[1]), 1024));
            }
        }
        return values;
    }

    /** 读取指定网卡累计流量；未配置时按默认路由自动选择。 */
    private long[] network() throws IOException {
        String name = config.networkInterface();
        if (name.isEmpty()) {
            List<String> routes = Files.readAllLines(config.procRoot().resolve("net/route"));
            for (int i = 1; i < routes.size(); i++) {
                String[] parts = routes.get(i).trim().split("\\s+");
                if (parts.length >= 4 && "00000000".equals(parts[1]) && (Integer.parseInt(parts[3], 16) & 1) != 0) { name = parts[0]; break; }
            }
        }
        if (name.isEmpty()) throw new IOException("未找到默认路由网卡，请配置 MONITOR_AGENT_NETWORK_INTERFACE");
        for (String line : Files.readAllLines(config.procRoot().resolve("net/dev"))) {
            int split = line.indexOf(':');
            if (split < 0 || !line.substring(0, split).trim().equals(name)) continue;
            String[] fields = line.substring(split + 1).trim().split("\\s+");
            if (fields.length < 16) throw new IOException("网卡数据不完整");
            long rx = Long.parseLong(fields[0]), tx = Long.parseLong(fields[8]);
            if (rx < 0 || tx < 0) throw new IOException("网卡累计流量无效");
            return new long[]{rx, tx};
        }
        throw new IOException("找不到指定网卡：" + name);
    }

    /** CPU 累计时间快照。 */
    private static class CpuTicks {
        private final long total;
        private final long idle;
        /** 保存 CPU 时间快照。 */
        private CpuTicks(long total, long idle) { this.total = total; this.idle = idle; }
    }
}
