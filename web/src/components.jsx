import { Activity, Box, Check, CircleHelp, Clock3, Cpu, Database, HardDrive, Layers3, LockKeyhole, Plus, Search, Server, ServerOff, X } from 'lucide-react';
import { bytes, percent, time } from './format';

const statuses = { ONLINE: ['在线', 'online'], OFFLINE: ['离线', 'offline'], UNREGISTERED: ['未注册', 'unknown'] };

/** 展示 Agent 的在线状态。 */
export function Status({ value }) {
  const [label, color] = statuses[value] || statuses.UNREGISTERED;
  return <span className={`status ${color}`}><i />{label}</span>;
}

/** 展示平台侧边导航。 */
export function Sidebar({ page, setPage, onLogout }) {
  const nav = [['overview', '概览', Layers3], ['servers', '服务器', Server], ['instances', '受管实例', Box]];
  return <aside className="sidebar"><div className="brand"><span className="brand-mark"><Layers3 size={21} /></span><div>Monitor<span>服务器管理平台</span></div></div>
    <div className="nav-label">工作空间</div><nav>{nav.map(([key, text, Icon]) => <button key={key} className={page === key ? 'nav-item active' : 'nav-item'} onClick={() => setPage(key)}><Icon size={18}/>{text}</button>)}</nav>
    <div className="sidebar-bottom"><div className="connection"><span className="connection-dot"/>管理控制台</div><button className="logout" onClick={onLogout}><LockKeyhole size={15}/>退出登录</button></div>
  </aside>;
}

/** 管理员令牌输入页面，凭据仅保存在页面内存中。 */
export function Login({ onLogin, error, busy }) {
  return <div className="login-page"><div className="login-card"><div className="login-brand"><span className="brand-mark"><Layers3 size={22}/></span> Monitor</div><h1>欢迎使用 Monitor</h1><p>输入管理员令牌，进入服务器管理平台。</p><form onSubmit={e => { e.preventDefault(); onLogin(new FormData(e.currentTarget).get('token')); }}><label>管理员令牌<input name="token" type="password" autoComplete="off" placeholder="请输入管理员令牌" required autoFocus /></label>{error && <div className="form-error" role="alert">{error}</div>}<button className="primary full" disabled={busy}>{busy ? '验证中…' : '进入管理平台'}</button></form><small>令牌仅保存在当前页面内存中，刷新后需要重新输入。</small></div></div>;
}

/** 以统计卡片展示服务器和 Agent 数量。 */
export function Summary({ servers }) {
  const cards = [ ['服务器总数', servers.length, Server, 'neutral'], ['在线 Agent', servers.filter(s => s.agentStatus === 'ONLINE').length, Check, 'green'], ['离线 Agent', servers.filter(s => s.agentStatus === 'OFFLINE').length, ServerOff, 'red'], ['未注册 Agent', servers.filter(s => s.agentStatus === 'UNREGISTERED').length, CircleHelp, 'gray'] ];
  return <section className="summary">{cards.map(([label, value, Icon, tone]) => <div className="summary-card" key={label}><span className={`summary-icon ${tone}`}><Icon size={21}/></span><div><span className="summary-label">{label}</span><strong>{value}<small>{label === '服务器总数' ? '台' : '个'}</small></strong></div></div>)}</section>;
}

/** 显示可搜索筛选的服务器列表。 */
export function ServerTable({ servers, selectedId, onSelect, query, setQuery, status, setStatus, environment, setEnvironment, onAdd }) {
  const environments = [...new Set(servers.map(s => s.environment))].sort();
  const filtered = servers.filter(s => `${s.name} ${s.address}`.toLowerCase().includes(query.toLowerCase()) && (status === 'ALL' || s.agentStatus === status) && (environment === 'ALL' || s.environment === environment));
  return <section className="panel server-panel"><div className="panel-heading"><div><h2>服务器列表</h2><span className="count">共 {filtered.length} 台服务器</span></div></div><div className="filters"><div className="search"><Search size={16}/><input aria-label="搜索服务器" placeholder="搜索服务器名称或 IP" value={query} onChange={e => setQuery(e.target.value)} /></div><select aria-label="筛选环境" value={environment} onChange={e => setEnvironment(e.target.value)}><option value="ALL">全部环境</option>{environments.map(item => <option key={item}>{item}</option>)}</select><select aria-label="筛选状态" value={status} onChange={e => setStatus(e.target.value)}><option value="ALL">全部状态</option><option value="ONLINE">在线</option><option value="OFFLINE">离线</option><option value="UNREGISTERED">未注册</option></select></div>
    <div className="table-scroll"><table><thead><tr><th>服务器名称</th><th>IP 地址</th><th>环境</th><th>Agent 状态</th><th>最后心跳</th><th>操作</th></tr></thead><tbody>{filtered.map(s => <tr key={s.id} className={selectedId === s.id ? 'selected' : ''} onClick={() => onSelect(s.id)}><td><span className="server-name"><Server size={16}/>{s.name}</span></td><td className="mono">{s.address}</td><td>{s.environment}</td><td><Status value={s.agentStatus}/></td><td className="muted nowrap">{time(s.lastHeartbeatAt)}</td><td><button className="text-button" onClick={() => onSelect(s.id)}>详情</button></td></tr>)}</tbody></table></div>
    {!filtered.length && <div className="empty"><Server size={26}/><strong>{servers.length ? '没有匹配的服务器' : '还没有服务器'}</strong><span>{servers.length ? '试试调整搜索内容或筛选条件。' : '添加服务器后，即可登记 Agent 并查看监控。'}</span>{!servers.length && <button className="secondary" onClick={onAdd}><Plus size={15}/>添加服务器</button>}</div>}
    <div className="table-footer">显示 {filtered.length} / {servers.length} 台服务器</div>
  </section>;
}

/** 展示单项资源使用量。 */
function Resource({ icon: Icon, label, value, sub }) {
  return <div className="resource"><div className="resource-head"><Icon size={17}/>{label}</div><strong>{value == null ? '—' : `${value.toFixed(1)}%`}</strong><span>{sub || '暂无数据'}</span><div className="progress"><i style={{ width: `${value ?? 0}%` }}/></div></div>;
}

/** 根据真实采样点绘制 CPU 使用率趋势。 */
function MetricChart({ metrics }) {
  const points = [...metrics].reverse().filter(m => Number.isFinite(m.cpuPercent));
  if (!points.length) return <div className="chart-empty"><Activity size={22}/><span>暂无 CPU 采样数据</span></div>;
  const x = i => points.length === 1 ? 50 : 8 + i * 284 / (points.length - 1);
  const y = p => 105 - Math.max(0, Math.min(100, p.cpuPercent));
  const coords = points.map((p, i) => `${x(i)},${y(p)}`).join(' ');
  return <div className="chart"><div className="chart-y"><span>100%</span><span>50%</span><span>0%</span></div><svg viewBox="0 0 300 112" preserveAspectRatio="none" role="img" aria-label={`CPU 趋势，共 ${points.length} 个采样点`}><line x1="0" y1="5" x2="300" y2="5"/><line x1="0" y1="55" x2="300" y2="55"/><line x1="0" y1="105" x2="300" y2="105"/>{points.length > 1 && <polyline className="chart-line" points={coords}/>}<circle cx={x(points.length-1)} cy={y(points.at(-1))} r="3"/></svg><div className="chart-times"><span>{time(points[0].collectedAt)}</span><span>{time(points.at(-1).collectedAt)}</span></div></div>;
}

/** 显示选中服务器的指标、凭据操作和实例列表。 */
export function ServerDetail({ server, metrics, instances, loading, error, onRegister, onRotate, onAddInstance }) {
  if (!server) return <section className="panel detail-panel detail-empty"><Server size={30}/><h2>选择一台服务器</h2><p>查看资源使用情况、Agent 和受管实例。</p></section>;
  const latest = metrics[0];
  return <section className="panel detail-panel"><div className="detail-title"><h2>服务器详情</h2><span className="tiny-label">实时概览</span></div><div className="detail-identity"><span className="device-icon"><Server size={19}/></span><div><div className="identity-line"><h3>{server.name}</h3><Status value={server.agentStatus}/></div><span className="muted mono">{server.address}</span></div></div><div className="detail-meta"><span>环境 <b>{server.environment}</b></span><span>Agent 版本 <b>{server.agentVersion || '—'}</b></span></div>
    <div className="detail-actions">{server.agentStatus === 'UNREGISTERED' ? <button className="secondary" onClick={onRegister}>注册 Agent</button> : <button className="secondary" onClick={onRotate}>重新签发凭据</button>}<span>最后心跳 {time(server.lastHeartbeatAt)}</span></div>
    {error && <div className="inline-error" role="alert">{error}</div>}
    <div className="section-heading"><h3>资源使用情况</h3><span>{loading ? '更新中…' : latest ? `采集于 ${time(latest.collectedAt)}` : '暂无采样'}</span></div>
    <div className="resources"><Resource icon={Cpu} label="CPU" value={latest?.cpuPercent} sub={latest ? `负载 ${latest.loadOne}` : null}/><Resource icon={Database} label="内存" value={percent(latest?.memoryUsedBytes, latest?.memoryTotalBytes)} sub={latest ? `${bytes(latest.memoryUsedBytes)} / ${bytes(latest.memoryTotalBytes)}` : null}/><Resource icon={HardDrive} label="磁盘" value={percent(latest?.diskUsedBytes, latest?.diskTotalBytes)} sub={latest ? `${bytes(latest.diskUsedBytes)} / ${bytes(latest.diskTotalBytes)}` : null}/></div>
    <div className="section-heading chart-heading"><h3>CPU 使用率趋势</h3><span>最近 {metrics.length} 个采样点</span></div><MetricChart metrics={metrics}/>
    <div className="network"><span>网络接收 <b>{latest ? bytes(latest.networkRxBytes) : '—'}</b></span><span>网络发送 <b>{latest ? bytes(latest.networkTxBytes) : '—'}</b></span><small>累计值</small></div>
    <div className="section-heading instance-heading"><h3>受管实例 <span>({instances.length})</span></h3><button className="text-button" onClick={onAddInstance}><Plus size={15}/>添加实例</button></div>
    {instances.length ? <div className="instance-list">{instances.map(item => <div className="instance-row" key={item.id}><span className="instance-icon"><Box size={16}/></span><div><strong>{item.name}</strong><small>{item.description || '暂无描述'}</small></div><span className="type-chip">{item.type}</span></div>)}</div> : <div className="instance-empty">暂无受管实例</div>}
  </section>;
}

/** 通用弹窗容器。 */
export function Modal({ title, subtitle, onClose, children }) {
  return <div className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><div className="modal-header"><div><h2 id="modal-title">{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button className="icon-button" aria-label="关闭" onClick={onClose}><X size={19}/></button></div>{children}</section></div>;
}
