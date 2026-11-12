package com.debugsw.monitor.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** 通过 Linux proc 发现 Nginx master 进程，不修改 Nginx 配置。 */
public class NginxCollector {
    private final Path proc;

    /** 指定 proc 目录。 */
    public NginxCollector(Path proc) { this.proc = proc; }

    /** 采集进程快照，单个进程不可读取时保留不完整标记。 */
    public String collect() throws IOException {
        StringBuilder items = new StringBuilder();
        boolean partial = false;
        int count = 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(proc)) {
            for (Path dir : dirs) {
                String pid = dir.getFileName().toString();
                if (!pid.matches("[0-9]+")) continue;
                try {
                    String comm = new String(Files.readAllBytes(dir.resolve("comm")), StandardCharsets.UTF_8).trim();
                    if (!comm.equals("nginx")) continue;
                    String cmd = new String(Files.readAllBytes(dir.resolve("cmdline")), StandardCharsets.UTF_8).replace('\0', ' ').trim();
                    if (!cmd.startsWith("nginx: master process")) continue;
                    if (count++ >= 100) { partial = true; break; }
                    if (items.length() > 0) items.append(',');
                    String exe = "";
                    try { exe = Files.readSymbolicLink(dir.resolve("exe")).toString(); } catch (IOException e) { partial = true; }
                    items.append("{\"pid\":").append(pid).append(",\"state\":\"RUNNING\",\"executable\":").append(quote(exe))
                         .append(",\"command\":").append(quote(cmd)).append('}');
                } catch (java.nio.file.NoSuchFileException e) {
                    // 进程可能在扫描过程中结束。
                } catch (IOException e) { partial = true; }
            }
        }
        return "{\"collectedAt\":" + quote(Instant.now().toString()) + ",\"instances\":[" + items + "],\"partial\":" + partial + "}";
    }

    /** 转义 JSON 字符串，防止进程参数破坏报文格式。 */
    private String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format("\\u%04x", (int)c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
