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
    private final boolean readConfig;

    /** 指定 proc 目录。 */
    public NginxCollector(Path proc, boolean readConfig) { this.proc = proc; this.readConfig = readConfig; }

    /** 采集进程快照，单个进程不可读取时保留不完整标记。 */
    public String collect() throws IOException {
        StringBuilder items = new StringBuilder();
        boolean partial = false;
        int count = 0;
        int configBudget = 128 * 1024;
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
                    String configPath = explicitConfigPath(cmd);
                    String configContent = "";
                    String configError = "";
                    if (readConfig && !configPath.isEmpty()) {
                        try {
                            Path config = java.nio.file.Paths.get(configPath);
                            if (!config.isAbsolute()) throw new IOException("配置路径不是绝对路径");
                            if (!Files.isRegularFile(config)) throw new IOException("配置文件不可读取");
                            try (java.io.InputStream input = Files.newInputStream(config); java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                                byte[] chunk = new byte[4096]; int n; int total = 0;
                                while ((n = input.read(chunk)) != -1) {
                                    total += n; if (total > 65536) throw new IOException("配置文件超过 64KB");
                                    buffer.write(chunk, 0, n);
                                }
                                configContent = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(buffer.toByteArray())).toString();
                            }
                            int length = quote(configContent).length();
                            if (length > configBudget) { configContent = ""; throw new IOException("配置快照超过上报大小限制"); }
                            configBudget -= length;
                        } catch (IOException e) { configError = "配置文件读取失败：" + e.getMessage(); }
                    } else if (readConfig) configError = "启动命令未明确指定绝对配置路径";
                    items.append("{\"pid\":").append(pid).append(",\"state\":\"RUNNING\",\"executable\":").append(quote(exe))
                         .append(",\"command\":").append(quote(cmd)).append(",\"configPath\":").append(quote(configPath))
                         .append(",\"configReadEnabled\":").append(readConfig)
                         .append(",\"configContent\":").append(quote(configContent)).append(",\"configError\":").append(quote(configError)).append('}');
                } catch (java.nio.file.NoSuchFileException e) {
                    // 进程可能在扫描过程中结束。
                } catch (IOException e) { partial = true; }
            }
        }
        return "{\"collectedAt\":" + quote(Instant.now().toString()) + ",\"instances\":[" + items + "],\"partial\":" + partial + "}";
    }

    /** 仅识别启动参数中明确指定的绝对配置路径，不猜测默认路径。 */
    private String explicitConfigPath(String command) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?:^|\\s)-c\\s+(/[A-Za-z0-9_./-]+)(?:\\s|$)").matcher(command);
        return match.find() ? match.group(1) : "";
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
