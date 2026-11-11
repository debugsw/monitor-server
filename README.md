# monitor-server

统一监控平台服务端。当前提供 Linux 服务器登记、Agent 凭据签发与心跳、主机指标上报查询，以及可扩展的受管实例登记接口。提供同源管理页面；提供 Linux Agent；此阶段不包含 Nginx 专属管理或告警功能。

## 环境与启动

需要 Java 17、Maven 3.8+。默认使用本地持久化 H2 数据库，启动时由 Flyway 创建表；请备份 `data/` 目录。设置一个至少 32 字符的高强度随机管理员令牌，且不要将令牌提交到仓库。

```bash
export MONITOR_ADMIN_TOKEN='替换为至少32字符的随机令牌'
mvn -DskipTests clean package
java -jar target/monitor-server-0.1.0-SNAPSHOT.jar
```

默认绑定 `127.0.0.1:8080`，仅用于本机访问。多服务器接入时，可以通过 `MONITOR_BIND_ADDRESS` 指定监听地址，并在受信任网络中部署 TLS 反向代理、限制来源及保护令牌；当前服务本身没有实现 TLS、双向证书认证或用户角色，不能直接暴露到公网。

启动后访问 `http://127.0.0.1:8080/`，输入管理员令牌。令牌只保存在当前页面内存中，刷新页面需要重新输入。页面支持服务器登记、筛选、Agent 凭据签发与轮换、主机指标和受管实例查看与登记；指标由 Agent 上报，未安装或未上报时显示暂无数据。

前端源码位于 `web/`，仓库同时保存构建后的静态资源，因此使用 Maven 打包不需要 Node.js。修改前端后需要先重新构建，再执行 Maven 打包：

```bash
cd web
npm ci
npm run build
cd ..
mvn -DskipTests clean package
```

前端开发需要 Node.js 18 或更高版本；`npm run dev` 使用 Vite 开发服务器，默认将 `/api` 请求代理至 `http://127.0.0.1:8080`。执行 `npm run build` 会更新 `src/main/resources/static/`，提交前应同时提交源码、锁文件及生成的静态资源。

可配置项：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MONITOR_ADMIN_TOKEN` | 无 | 必填，至少 32 字符 |
| `MONITOR_BIND_ADDRESS` | `127.0.0.1` | HTTP 监听地址 |
| `MONITOR_AGENT_ONLINE_SECONDS` | `30` | 心跳在线判定时间，单位秒 |
| `MONITOR_DB_URL` | `jdbc:h2:file:./data/monitor` | H2 数据库地址 |
| `MONITOR_DB_USERNAME` | `sa` | 数据库用户 |
| `MONITOR_DB_PASSWORD` | 空 | 数据库密码 |

## 接口与调用顺序

所有接口使用 `Authorization: Bearer <令牌>`。管理接口使用管理员令牌；Agent 接口使用对应服务器的 Agent 令牌。时间使用带时区的 ISO 8601 格式，例如 `2026-11-10T08:00:00Z`。

1. `POST /api/admin/servers`：登记服务器，提交 `{"name":"server-1","address":"10.0.0.1","environment":"test","tags":"gateway"}`，返回服务器 `id`。`tags` 可省略。
2. `POST /api/admin/servers/{serverId}/agent`：注册 Agent，返回 `agentId` 和 `token`。每台服务器目前只能注册一个 Agent，令牌只在该次响应中返回，数据库仅保存摘要，请安全保存。
3. 如需使旧 Agent 令牌失效，调用 `POST /api/admin/servers/{serverId}/agent/token` 重新签发。新令牌只返回一次，旧令牌立即失效，Agent 状态重置为离线。
4. `POST /api/agent/heartbeat`：使用 Agent 令牌提交 `{"version":"1.0.0"}`。返回 204；服务端以接收时间判断在线状态。
5. `POST /api/agent/host-metrics`：使用 Agent 令牌上报主机指标，返回 204。服务器归属由令牌确定，不能由请求指定。
6. `GET /api/admin/servers`：查询服务器和 Agent 状态，状态为 `UNREGISTERED`、`ONLINE` 或 `OFFLINE`。
7. `GET /api/admin/servers/{serverId}/metrics?limit=100`：按采集时间倒序查询指标，`limit` 范围 1–500。
8. `POST /api/admin/servers/{serverId}/instances`：登记受管实例，例如 `{"name":"nginx-gateway","type":"NGINX","description":"入口网关"}`；`description` 可省略。类型使用大写字母、数字和下划线，以字母开头。
9. `GET /api/admin/servers/{serverId}/instances`：查询实例列表。

主机指标上报示例：

```json
{
  "collectedAt": "2026-11-10T08:00:00Z",
  "cpuPercent": 25.4,
  "memoryTotalBytes": 8589934592,
  "memoryUsedBytes": 4294967296,
  "diskTotalBytes": 107374182400,
  "diskUsedBytes": 53687091200,
  "loadOne": 1.2,
  "networkRxBytes": 123456,
  "networkTxBytes": 654321
}
```

网络字段为采集以来的累计字节数，不是速率；磁盘字段的统计范围由 Agent 约定。服务端校验数值范围并同时记录采集时间和接收时间。当前指标不自动清理，也不去重；长时间运行需定期备份并安排数据清理，后续可接入时序数据库和保留策略。

## 数据库

首个迁移 `V1__base_platform.sql` 创建 `ops_server`、`ops_agent`、`ops_instance`、`ops_host_metric` 四张表，以及实例归属和主机指标时间索引；不预置业务数据。当前默认支持 H2 文件数据库，不应在多个服务端进程间直接共享同一个数据库文件。

## Linux Agent

Agent 位于 `agent/`，为独立 Java 17 程序，使用 Linux `/proc` 和 Java 文件系统接口采集，无需修改 Nginx 或业务应用。本次不增加数据库表、字段或索引。

构建：

```bash
mvn -f agent/pom.xml -DskipTests package
```

在管理页面登记服务器并注册 Agent，保存一次性显示的 Agent 令牌。Linux 主机上创建仅 Agent 运行账号可读的令牌文件，再配置环境变量：

```bash
export MONITOR_AGENT_SERVER_URL='https://monitor.example.com/'
export MONITOR_AGENT_TOKEN_FILE='/etc/monitor-agent/token'
java -jar agent/target/monitor-agent-0.1.0-SNAPSHOT.jar
```

服务端默认仅监听本机；远程接入需配置可信 TLS 反向代理及网络访问。Agent 默认使用 JVM 的系统信任库验证 HTTPS 证书，不跟随 HTTP 重定向。为防止令牌明文传输，仅允许本机回环地址使用 HTTP；请使用 TLS 保护远程访问。也可用 `MONITOR_AGENT_TOKEN` 提供令牌，但令牌文件优先，避免将凭据写在命令行中。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MONITOR_AGENT_SERVER_URL` | 无 | 必填，平台地址；可包含反向代理路径前缀 |
| `MONITOR_AGENT_TOKEN_FILE` | 无 | Agent 令牌文件路径，优先于环境变量令牌 |
| `MONITOR_AGENT_TOKEN` | 无 | 未指定令牌文件时必填 |
| `MONITOR_AGENT_INTERVAL_SECONDS` | 5 | 心跳及采集间隔，1–3600 秒；应小于服务端在线判定时间 |
| `MONITOR_AGENT_TIMEOUT_SECONDS` | 5 | 连接和请求超时，1–60 秒 |
| `MONITOR_AGENT_DISK_PATH` | `/` | 统计所在文件系统的路径 |
| `MONITOR_AGENT_NETWORK_INTERFACE` | 自动 | 指定网卡名；未指定时选择第一条启用的 IPv4 默认路由网卡 |
| `MONITOR_AGENT_PROC_ROOT` | `/proc` | Linux proc 挂载路径；容器内需确认其代表目标主机 |

CPU 使用率根据 `/proc/stat` 两次采样计算，第一条指标在一个采集周期后产生；内存已用量为 `MemTotal - MemAvailable`；磁盘为指定路径所在文件系统的总量与已用量；网络为选定网卡自系统启动以来的累计字节数。网卡切换或计数器重置会影响累计值比较。缺失必要数据时该次指标不上报，日志记录错误。心跳与指标独立执行，上报失败将在下一周期继续尝试，不持久化或补传失败的历史采样；`401` 表示凭据无效，重新签发后需更新令牌并重启 Agent。

`agent/deploy/monitor-agent.service` 是 systemd 配置示例，不会自动安装或创建账号。使用前需自行创建低权限 `monitor-agent` 用户、安装 JAR 到示例路径，并准备权限受限的 `/etc/monitor-agent/agent.env` 和令牌文件；按实际 Linux 环境调整 Java、服务地址和目录后再启用。
