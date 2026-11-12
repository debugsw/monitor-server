# monitor-server

统一监控平台服务端。当前提供 Linux 服务器登记、Agent 凭据签发与心跳、主机指标上报查询，以及可扩展的受管实例登记接口。提供同源管理页面；提供 Linux Agent；支持 Nginx master 进程发现和受限配置文件管理；此阶段不包含告警功能。

## 环境与启动

需要 Java 8、Maven 3.8+。默认使用本地持久化 H2 数据库，启动时由 Flyway 创建表；请备份 `data/` 目录。设置一个至少 32 字符的高强度随机管理员令牌，且不要将令牌提交到仓库。

```bash
export MONITOR_ADMIN_TOKEN='替换为至少32字符的随机令牌'
mvn -DskipTests clean package
java -jar target/monitor-server-0.1.0-SNAPSHOT.jar
```

默认绑定 `0.0.0.0:8080`，可通过服务器 IP 访问；可通过 `MONITOR_BIND_ADDRESS` 指定监听地址，例如设置为 `127.0.0.1` 只允许本机访问。请限制防火墙来源并保护令牌；当前服务本身没有实现 TLS、双向证书认证或用户角色，不应直接暴露到公网。

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
| `MONITOR_BIND_ADDRESS` | `0.0.0.0` | HTTP 监听地址，可设为 `127.0.0.1` 仅允许本机访问 |
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

## 旧版 H2 数据迁移

服务端为兼容 Java 8，使用 Spring Boot 2.7.18、H2 2.2.224 和 Flyway 8.5.13。旧版使用 H2 2.3.232，不能假定旧数据库文件可由新驱动直接打开。**已有数据时先停止旧服务、备份整个数据库目录，再把旧库导出并导入到新的独立目录**，验证数据后再切换 `MONITOR_DB_URL`；不要覆盖原数据库。迁移 SQL 包含令牌摘要等敏感数据，应限制访问并在完成后安全处理。Flyway 8 启动时会提示 H2 2.2 超出其已测试版本；当前迁移副本已验证可读取，生产数据仍需自行备份和核对。

以下示例使用默认 `sa` 和空密码，假定旧服务包为 `old-server.jar`、新服务包为 `new-server.jar`，且路径均为绝对路径；如果设置过密码，需要按实际数据库配置提供凭据。需要 Java 17 执行旧版 H2 导出，Java 8 可执行新版导入：

```bash
umask 077
mkdir -p /path/to/secure-migration /path/to/new-data
unzip -p /path/to/old-server.jar BOOT-INF/lib/h2-2.3.232.jar > /path/to/secure-migration/h2-old.jar
unzip -p /path/to/new-server.jar BOOT-INF/lib/h2-2.2.224.jar > /path/to/secure-migration/h2-new.jar
/path/to/java17/bin/java -cp /path/to/secure-migration/h2-old.jar org.h2.tools.Script \
  -url jdbc:h2:file:/path/to/old-data/monitor -user sa -script /path/to/secure-migration/monitor.sql
/path/to/java8/bin/java -cp /path/to/secure-migration/h2-new.jar org.h2.tools.RunScript \
  -url jdbc:h2:file:/path/to/new-data/monitor -user sa -script /path/to/secure-migration/monitor.sql
```

在新库上启动新服务并核对服务器、Agent 和指标数据；确认后再切换使用。保留旧库备份以便回退。Spring Boot 2.7 社区版已结束维护，应在受控环境中部署并安排后续升级。

## Linux Agent

Agent 位于 `agent/`，为独立 Java 8 程序，使用 Linux `/proc` 和 Java 文件系统接口采集，无需修改 Nginx 或业务应用。本次不增加数据库表、字段或索引。

构建：

```bash
mvn -f agent/pom.xml -DskipTests package
```

在管理页面登记服务器并注册 Agent，保存一次性显示的 Agent 令牌。Linux 主机上创建仅 Agent 运行账号可读的令牌文件，再配置环境变量：

```bash
export MONITOR_AGENT_SERVER_URL='http://monitor.example.com:8080/'
export MONITOR_AGENT_TOKEN_FILE='/etc/monitor-agent/token'
java -jar agent/target/monitor-agent-0.1.0-SNAPSHOT.jar
```

服务端默认监听所有 IPv4 网卡；远程接入需确保网络及防火墙允许访问，并限制可信来源。Agent 支持 HTTP 和 HTTPS，不跟随 HTTP 重定向。HTTP 会明文传输令牌和数据，请仅在可信网络中使用；使用 HTTPS 时由 JVM 信任库验证证书。也可用 `MONITOR_AGENT_TOKEN` 提供令牌，但令牌文件优先，避免将凭据写在命令行中。

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

## Nginx 只读进程发现

升级服务端和 Agent 后，Agent 定时扫描 Linux `/proc` 并上报 Nginx master 进程的 PID、可执行文件路径和进程启动命令；管理页面在服务器详情中显示最近快照及采集时间。可执行文件路径和启动参数取决于 Agent 的操作系统权限；无法读取部分进程时显示结果不完整。master 进程存在并不代表业务或配置正常，当前没有连接数和请求量指标。采集状态只保存最近一次，不保存历史曲线。

服务端数据库迁移 `V2__nginx_snapshot.sql` 新增 `ops_nginx_snapshot` 表，字段包括服务器标识、采集时间、接收时间、采集不完整标记和实例 JSON；服务器标识作为主键及外键，无初始化数据。服务器升级前请备份数据库。进程命令行可能包含敏感信息，访问管理接口需谨慎授权。

可通过 `MONITOR_NGINX_CONFIG_READ=true` 显式启用主配置读取；仅支持 master 进程启动命令中以 `-c` 明确指定的简单绝对路径（字母、数字、下划线、点、斜杠和连字符），大小上限 64 KB，要求 UTF-8。默认配置路径、相对路径、包含空格的路径及 `include` 文件不会读取。配置可能包含敏感信息，启用后内容会随快照上报、存入服务端数据库并展示给管理员。HTTP 明文传输配置及凭据，仅在可信网络中启用。

## Nginx 单文件配置管理

服务端迁移 `V3__nginx_tasks.sql` 增加 `ops_nginx_task`、`ops_nginx_publish_record` 两张表，以及按服务器、状态、实例和时间查询的索引；不新增初始化数据。任务和读取结果可能包含配置内容，HTTP 传输为明文，应限制网络访问，保护数据库和备份。

此功能要求 Agent 运行在目标 Nginx 的 Linux 主机上、具有对应的读取或写入权限；每个实例由本机白名单指定，平台无法指定任意可执行文件或路径。创建仅允许 Agent 账号和管理员访问的 Java properties 文件，例如：

```properties
gateway.binary=/usr/sbin/nginx
gateway.main=/etc/nginx/nginx.conf
gateway.target=/etc/nginx/nginx.conf
gateway.prefix=/etc/nginx/
gateway.masterPid=1234
```

`gateway` 是页面输入的实例标识，`main` 为主配置文件，`target` 为本次允许管理的单个文件，`prefix` 必须与 master 启动参数一致。多个文件可配置为不同标识。当前要求 `/proc/<masterPid>/exe` 与 `binary` 一致，master 启动命令明确含有与清单一致的 `-c` 和 `-p`，且不含 `-g`；PID 变化后需更新清单。默认路径、相对路径、带空格的路径及其他启动方式暂不支持发布；target 必须是非符号链接、非硬链接的普通文件。请人工核对该 target 确实被当前主配置引用。

```bash
export MONITOR_NGINX_MANIFEST=/etc/monitor-agent/nginx.properties
# 仅在允许 Agent 修改配置及执行 reload 时设置
export MONITOR_NGINX_WRITE_ENABLED=true
```

重启 Agent 后，在服务器详情的 Nginx 配置管理区输入 `gateway`，读取后编辑并确认发布。Agent 比对 SHA-256 摘要，保留备份，原子替换单文件并执行 `nginx -t`；校验失败且文件未被外部修改时恢复原文件。校验成功后发送 reload。由于命令退出成功不能证明 master 已应用配置，页面显示 `UNKNOWN`，需要检查 Nginx 错误日志和业务健康状态。超时、运行时错误或外部并发修改同样可能为 `UNKNOWN`，应人工核对备份及运行状态，不要直接重复提交。清单、备份文件、任务记录与配置内容需严格控制访问。

Agent 领取的任务不会自动重新派发，避免意外重复执行；Agent 中断或结果上报失败可能使任务一直停在 `CLAIMED`。配置文件只能逐个发布，无法保证跨文件原子性；外部进程不遵守相同锁时，版本检查不能完全消除并发竞争。真实 Linux Nginx 的部署行为仍需在测试环境验证。
