package com.debugsw.monitor.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** 按本机白名单执行 Nginx 配置任务，不接受远程指定命令或路径。 */
public class NginxTaskWorker {
    private static final Logger LOG = Logger.getLogger(NginxTaskWorker.class.getName());
    private final AgentConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path manifest;

    /** 初始化任务执行器；未指定清单时不领取任务。 */
    public NginxTaskWorker(AgentConfig config, String manifest) { this.config = config; this.manifest = manifest == null || manifest.trim().isEmpty() ? null : Paths.get(manifest); }

    /** 领取并执行一个任务，网络中断时已领取任务不会自动重试。 */
    public void poll() {
        if (manifest == null) return;
        try {
            Map<String, Object> task = mapper.readValue(post("nginx-tasks/claim", "{}"), new TypeReference<Map<String, Object>>() {});
            if (task.isEmpty()) return;
            String id = (String)task.get("id");
            Map<String, String> result;
            try { result = execute(task); }
            catch (Exception e) { result = result("FAILED", "任务执行失败：" + e.getMessage(), null, null); }
            post("nginx-tasks/" + id + "/result", mapper.writeValueAsString(result));
        } catch (Exception e) { LOG.warning("Nginx 任务通信失败：" + e.getClass().getSimpleName()); }
    }

    /** 加载白名单并限制只管理该实例配置的单个文件。 */
    private Map<String, String> execute(Map<String, Object> task) throws Exception {
        String key = (String)task.get("instanceKey"), op = (String)task.get("operation");
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,128}")) throw new IOException("实例标识无效");
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(manifest)) { p.load(in); }
        Path binary = absolute(p.getProperty(key + ".binary"));
        Path main = absolute(p.getProperty(key + ".main"));
        Path target = absolute(p.getProperty(key + ".target", main.toString()));
        Path prefix = absolute(p.getProperty(key + ".prefix"));
        String pid = p.getProperty(key + ".masterPid", "");
        if (!pid.matches("[1-9][0-9]*")) throw new IOException("白名单未配置有效 masterPid");
        Path proc = config.procRoot().resolve(pid);
        if (!Files.readSymbolicLink(proc.resolve("exe")).toRealPath().equals(binary.toRealPath())) throw new IOException("master 可执行文件不匹配");
        String cmd = new String(Files.readAllBytes(proc.resolve("cmdline")), StandardCharsets.UTF_8).replace('\0', ' ');
        if (!cmd.startsWith("nginx: master process") || !hasArgument(cmd, "-c", main.toString()) || !hasArgument(cmd, "-p", prefix.toString()) || hasGlobalOption(cmd))
            throw new IOException("master 启动参数不匹配；要求明确的 -c 和 -p，暂不支持 -g");
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("目标必须是普通文件且不能是符号链接");
        if (((Number)Files.getAttribute(target, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1) throw new IOException("不支持硬链接文件");
        byte[] original = readBounded(target);
        String digest = sha(original);
        if ("READ".equals(op)) return result("SUCCEEDED", "读取成功", StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(original)).toString(), digest);
        if (!"PUBLISH".equals(op) || !"true".equalsIgnoreCase(System.getenv("MONITOR_NGINX_WRITE_ENABLED"))) throw new IOException("配置写入未启用");
        if (!digest.equals(task.get("expectedDigest"))) throw new IOException("配置已被其他操作修改，请重新读取");
        byte[] next = ((String)task.get("content")).getBytes(StandardCharsets.UTF_8);
        if (next.length > 65536) throw new IOException("配置超过 64KB");
        return publish(binary, main, target, prefix, original, next, digest);
    }

    /** 精确匹配启动参数，避免路径前缀被误识别。 */
    private boolean hasArgument(String command, String flag, String value) {
        String[] parts = command.trim().split("\\s+");
        int matches = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(flag)) {
                if (i + 1 >= parts.length || !value.equals(parts[i + 1])) return false;
                matches++;
            } else if (parts[i].startsWith(flag)) return false;
        }
        return matches == 1;
    }

    /** 拒绝无法完整重放的全局启动指令。 */
    private boolean hasGlobalOption(String command) {
        for (String part : command.trim().split("\\s+")) if (part.equals("-g") || part.startsWith("-g")) return true;
        return false;
    }

    /** 原子替换文件，校验失败时仅在文件未被外部修改的情况下恢复。 */
    private Map<String, String> publish(Path binary, Path main, Path target, Path prefix, byte[] original, byte[] next, String digest) throws Exception {
        Path dir = target.getParent();
        PosixFileAttributes attributes = Files.readAttributes(target, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Path temp = Files.createTempFile(dir, ".monitor-", ".tmp");
        Path backup = dir.resolve(".monitor-backup-" + UUID.randomUUID());
        boolean replaced = false;
        try {
            Files.write(temp, next); Files.setPosixFilePermissions(temp, attributes.permissions());
            Files.setOwner(temp, attributes.owner()); Files.getFileAttributeView(temp, PosixFileAttributeView.class).setGroup(attributes.group());
            Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES);
            if (!sha(readBounded(target)).equals(digest)) throw new IOException("发布前配置发生变化");
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); replaced = true;
            CommandResult checked = run(binary, "-t", main, prefix);
            if (checked.code != 0) {
                if (!sha(readBounded(target)).equals(sha(next))) return result("UNKNOWN", "配置校验失败，但文件已被外部修改，未自动回滚；备份：" + backup, null, null);
                Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); replaced = false;
                return result("FAILED", "配置校验失败，已恢复原文件：" + checked.output, null, null);
            }
            if (!sha(readBounded(target)).equals(sha(next))) return result("UNKNOWN", "校验后配置被外部修改，未发送 reload；备份：" + backup, null, null);
            CommandResult reload = run(binary, "-s", main, prefix);
            return result("UNKNOWN", reload.code == 0 ? "已发送 reload，但无法确认生效；备份：" + backup : "reload 命令失败，运行状态待人工确认；备份：" + backup + "；" + reload.output, null, sha(next));
        } catch (Exception e) {
            if (replaced) return result("UNKNOWN", "发布过程异常，运行状态待人工确认，备份：" + backup + "；" + e.getClass().getSimpleName(), null, null);
            throw e;
        } finally { Files.deleteIfExists(temp); }
    }

    /** 执行指定 Nginx 二进制的校验或 reload，限制执行时间与记录的输出。 */
    private CommandResult run(Path binary, String mode, Path main, Path prefix) throws Exception {
        List<String> args = new ArrayList<>(); args.add(binary.toString()); args.add(mode);
        if ("-s".equals(mode)) args.add("reload");
        args.add("-c"); args.add(main.toString()); args.add("-p"); args.add(prefix.toString());
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[1024]; int n;
                while ((n = in.read(buf)) != -1) { int room = 4096 - output.size(); if (room > 0) output.write(buf, 0, Math.min(room, n)); }
            } catch (IOException ignored) { }
        }, "nginx-output");
        reader.setDaemon(true); reader.start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("Nginx 命令超时"); }
        reader.join(1000);
        return new CommandResult(process.exitValue(), new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    /** 保存受限的命令执行结果。 */
    private static class CommandResult {
        private final int code;
        private final String output;
        /** 初始化命令结果。 */
        private CommandResult(int code, String output) { this.code = code; this.output = output; }
    }

    /** 读取大小受限的配置内容。 */
    private byte[] readBounded(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n, total = 0; while ((n = in.read(buf)) != -1) { total += n; if (total > 65536) throw new IOException("配置超过 64KB"); out.write(buf, 0, n); } return out.toByteArray();
        }
    }
    /** 验证白名单中的绝对路径。 */
    private Path absolute(String value) throws IOException { if (value == null || !Paths.get(value).isAbsolute()) throw new IOException("白名单路径必须为绝对路径"); return Paths.get(value).normalize(); }
    /** 计算内容摘要。 */
    private String sha(byte[] data) throws Exception { StringBuilder out = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) out.append(String.format("%02x", b & 255)); return out.toString(); }
    /** 构造任务结果。 */
    private Map<String, String> result(String state, String message, String content, String digest) { Map<String, String> out = new HashMap<>(); out.put("state", state); out.put("message", message); if (content != null) out.put("content", content); if (digest != null) out.put("digest", digest); return out; }
    /** 以当前 Agent 凭据发送 JSON 请求。 */
    private String post(String path, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection)config.server().resolve("api/agent/" + path).toURL().openConnection();
        try {
            c.setConnectTimeout((int)config.timeout().toMillis()); c.setReadTimeout((int)config.timeout().toMillis()); c.setInstanceFollowRedirects(false); c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Authorization", "Bearer " + config.token()); c.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(bytes.length); try (OutputStream o = c.getOutputStream()) { o.write(bytes); }
            int status = c.getResponseCode(); if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            if (status == 204 || c.getContentLength() == 0) return "{}";
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[4096]; int n, total=0; while ((n=in.read(b))!=-1) { total+=n; if (total>262144) throw new IOException("响应过大"); out.write(b,0,n); } return new String(out.toByteArray(), StandardCharsets.UTF_8); }
        } finally { c.disconnect(); }
    }
}
