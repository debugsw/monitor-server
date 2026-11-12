import { useCallback, useEffect, useState } from 'react';
import { Box, ChevronRight, Clock3, Copy, Plus, RefreshCw, Server } from 'lucide-react';
import { post, request } from './api';
import { Login, Modal, ServerDetail, ServerTable, Sidebar, Status, Summary } from './components';
import { time } from './format';

/** 管理控制台的身份、数据刷新及交互状态。 */
export default function App() {
  const [token, setToken] = useState('');
  const [loginError, setLoginError] = useState('');
  const [busy, setBusy] = useState(false);
  const [servers, setServers] = useState([]);
  const [selectedId, setSelectedId] = useState('');
  const [detail, setDetail] = useState({ metrics: [], instances: [], nginx: null, loading: false, error: '' });
  const [allInstances, setAllInstances] = useState([]);
  const [instancesError, setInstancesError] = useState('');
  const [page, setPage] = useState('overview');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('ALL');
  const [environment, setEnvironment] = useState('ALL');
  const [modal, setModal] = useState(null);
  const [modalError, setModalError] = useState('');
  const [banner, setBanner] = useState('');
  const [updatedAt, setUpdatedAt] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [copied, setCopied] = useState(false);
  const server = servers.find(item => item.id === selectedId);

  /** 验证管理员令牌并建立当前页面会话。 */
  async function login(value) {
    setBusy(true); setLoginError('');
    try { const result = await request(value, '/servers'); setServers(result); setToken(value); setUpdatedAt(new Date()); setSelectedId(result[0]?.id || ''); }
    catch (error) { setLoginError(error.message); }
    finally { setBusy(false); }
  }

  /** 清除当前会话中的凭据和业务数据。 */
  function logout(message = '') {
    setToken(''); setServers([]); setSelectedId(''); setDetail({ metrics: [], instances: [], nginx: null, loading: false, error: '' }); setAllInstances([]); setModal(null); setLoginError(message); setBanner(''); setUpdatedAt(null);
  }

  /** 刷新服务器列表并区分鉴权失败和网络故障。 */
  const loadServers = useCallback(async () => {
    try {
      const result = await request(token, '/servers');
      setServers(result); setUpdatedAt(new Date()); setBanner('');
      setSelectedId(current => result.some(item => item.id === current) ? current : (result[0]?.id || ''));
    } catch (error) {
      if (error.status === 401) logout('管理员令牌已失效，请重新登录。');
      else setBanner(`服务器列表更新失败：${error.message}`);
    }
  }, [token]);

  useEffect(() => {
    if (!token) return undefined;
    loadServers();
    const timer = window.setInterval(loadServers, 5000);
    return () => window.clearInterval(timer);
  }, [token, loadServers, refreshKey]);

  useEffect(() => {
    if (!token || !selectedId) return undefined;
    let active = true;
    setDetail({ metrics: [], instances: [], nginx: null, loading: true, error: '' });
    /** 并发获取当前服务器的指标与实例。 */
    async function loadDetail() {
      const results = await Promise.allSettled([request(token, `/servers/${selectedId}/metrics?limit=100`), request(token, `/servers/${selectedId}/instances`), request(token, `/servers/${selectedId}/nginx`)]);
      if (!active) return;
      if (results.some(r => r.status === 'rejected' && r.reason.status === 401)) { logout('管理员令牌已失效，请重新登录。'); return; }
      setDetail(old => ({ metrics: results[0].status === 'fulfilled' ? results[0].value : old.metrics, instances: results[1].status === 'fulfilled' ? results[1].value : old.instances, nginx: results[2].status === 'fulfilled' ? results[2].value : old.nginx, loading: false, error: results.some(r => r.status === 'rejected') ? '部分数据更新失败，请检查连接后重试。' : '' }));
    }
    loadDetail(); const timer = window.setInterval(loadDetail, 5000);
    return () => { active = false; window.clearInterval(timer); };
  }, [token, selectedId, refreshKey]);

  useEffect(() => {
    if (!token || page !== 'instances') return undefined;
    let active = true;
    /** 查询各服务器的实例，失败时明确标记数据不完整。 */
    async function loadInstances() {
      const results = await Promise.allSettled(servers.map(s => request(token, `/servers/${s.id}/instances`)));
      if (!active) return;
      if (results.some(r => r.status === 'rejected' && r.reason.status === 401)) { logout('管理员令牌已失效，请重新登录。'); return; }
      setAllInstances(results.flatMap((r, i) => r.status === 'fulfilled' ? r.value.map(item => ({ ...item, serverName: servers[i].name, serverAddress: servers[i].address })) : []));
      setInstancesError(results.some(r => r.status === 'rejected') ? '部分服务器的实例加载失败，列表可能不完整。' : '');
    }
    loadInstances();
    return () => { active = false; };
  }, [token, page, servers, refreshKey]);

  /** 打开对话框并清除上一次错误。 */
  function openModal(name) { setModalError(''); setCopied(false); setModal({ name }); }
  /** 在服务端执行登记或凭据操作。 */
  async function submit(e) {
    e.preventDefault(); if (busy) return;
    setBusy(true); setModalError('');
    try {
      const data = new FormData(e.currentTarget);
      if (modal.name === 'server') {
        const result = await post(token, '/servers', { name: data.get('name').trim(), address: data.get('address').trim(), environment: data.get('environment').trim(), tags: data.get('tags').trim() });
        setSelectedId(result.id); await loadServers(); setSelectedId(result.id); setModal(null); setRefreshKey(n => n + 1);
      } else if (modal.name === 'instance') {
        await post(token, `/servers/${selectedId}/instances`, { name: data.get('name').trim(), type: data.get('type').trim().toUpperCase(), description: data.get('description').trim() });
        setModal(null); setRefreshKey(n => n + 1);
      } else {
        const result = await post(token, `/servers/${selectedId}/agent${modal.name === 'rotate' ? '/token' : ''}`);
        setModal({ name: 'token', value: result.token, agentId: result.agentId }); setRefreshKey(n => n + 1);
      }
    } catch (error) { if (error.status === 401) logout('管理员令牌已失效，请重新登录。'); else setModalError(error.message); }
    finally { setBusy(false); }
  }

  /** 复制只显示一次的 Agent 凭据。 */
  async function copyToken() {
    try { await navigator.clipboard.writeText(modal.value); setCopied(true); }
    catch { setModalError('复制失败，请手动选中并复制令牌。'); }
  }

  if (!token) return <Login onLogin={login} error={loginError} busy={busy}/>;
  return <div className="app"><Sidebar page={page} setPage={setPage} onLogout={() => logout()}/><main className="main"><header className="topbar"><span>工作空间 <ChevronRight size={14}/> {page === 'instances' ? '受管实例' : page === 'servers' ? '服务器' : '概览'}</span><div><span className={`top-status ${banner ? "disconnected" : ""}`}><i/>{banner ? "连接异常" : "管理端已连接"}</span><button className="refresh" onClick={() => setRefreshKey(n => n + 1)}><RefreshCw size={15}/>刷新</button></div></header>
    <div className="content"><div className="page-title"><div><h1>{page === 'instances' ? '受管实例' : page === 'servers' ? '服务器管理' : '服务器概览'}</h1><p>{page === 'instances' ? '集中查看各服务器上登记的受管实例。' : '集中查看服务器运行状态、Agent 连接情况与受管实例。'}</p></div><button className="primary" onClick={() => openModal('server')}><Plus size={17}/>添加服务器</button></div>
    {banner && <div className="banner" role="alert">{banner}</div>}
    {page === 'instances' ? <section className="panel instance-page"><div className="panel-heading"><div><h2>实例列表</h2><span className="count">共 {allInstances.length} 个实例</span></div></div>{instancesError && <div className="inline-error">{instancesError}</div>}{allInstances.length ? <div className="table-scroll"><table><thead><tr><th>实例名称</th><th>类型</th><th>所属服务器</th><th>描述</th><th>登记时间</th><th>操作</th></tr></thead><tbody>{allInstances.map(item => <tr key={item.id}><td><span className="server-name"><Box size={16}/>{item.name}</span></td><td><span className="type-chip">{item.type}</span></td><td>{item.serverName} <span className="muted">{item.serverAddress}</span></td><td>{item.description || '—'}</td><td>{time(item.createdAt)}</td><td><button className="text-button" onClick={() => { setSelectedId(item.serverId); setPage('servers'); }}>查看服务器</button></td></tr>)}</tbody></table></div> : <div className="empty"><Box size={26}/><strong>暂无受管实例</strong><span>在服务器详情中添加实例。</span></div>}</section> : <><Summary servers={servers}/><div className="workspace"><ServerTable servers={servers} selectedId={selectedId} onSelect={setSelectedId} query={query} setQuery={setQuery} status={status} setStatus={setStatus} environment={environment} setEnvironment={setEnvironment} onAdd={() => openModal('server')}/><ServerDetail server={server} {...detail} onRegister={() => openModal('register')} onRotate={() => openModal('rotate')} onAddInstance={() => openModal('instance')}/></div><div className="update-note"><Clock3 size={14}/>每 5 秒更新 · 最近更新 {time(updatedAt)}</div></>}
    </div></main>
    {modal && <Modal title={{ server: '添加服务器', instance: '添加受管实例', register: '注册 Agent', rotate: '重新签发凭据', token: 'Agent 凭据' }[modal.name]} subtitle={modal.name === 'instance' ? `所属服务器：${server?.name}` : modal.name === 'register' || modal.name === 'rotate' ? `服务器：${server?.name}` : undefined} onClose={() => { if (!busy) setModal(null); }}>
      {modal.name === 'token' ? <><p className="modal-copy">请立即保存此令牌，关闭后将无法再次查看。</p>{modal.agentId && <p className="modal-copy">Agent ID：<span className="mono">{modal.agentId}</span></p>}<textarea aria-label="Agent 令牌" readOnly value={modal.value} rows="3" onFocus={e => e.target.select()}/>{modalError && <div className="form-error">{modalError}</div>}<div className="modal-actions"><button className="secondary" onClick={copyToken}><Copy size={15}/>{copied ? '已复制' : '复制令牌'}</button><button className="primary" onClick={() => setModal(null)}>已保存</button></div></> : <form onSubmit={submit}>
        {modal.name === 'server' && <div className="form-grid"><label>服务器名称<input name="name" maxLength="100" required autoFocus placeholder="例如 web-01"/></label><label>IP 地址或主机名<input name="address" maxLength="255" required placeholder="例如 10.0.0.1"/></label><label>环境<input name="environment" maxLength="64" required placeholder="例如 生产、测试"/></label><label>标签 <span>选填</span><input name="tags" maxLength="500" placeholder="例如 gateway"/></label></div>}
        {modal.name === 'instance' && <div className="form-grid"><label>实例名称<input name="name" maxLength="100" required autoFocus placeholder="例如 nginx-gateway"/></label><label>实例类型<input name="type" maxLength="64" required pattern="[A-Za-z][A-Za-z0-9_]*" placeholder="例如 NGINX、REDIS"/></label><label className="span-all">描述 <span>选填</span><input name="description" maxLength="500" placeholder="实例用途或备注"/></label></div>}
        {modal.name === 'register' && <p className="modal-copy">将为该服务器生成 Agent 凭据，令牌只会显示一次。</p>}
        {modal.name === 'rotate' && <p className="modal-copy warning">重新签发后，旧凭据立即失效，Agent 需要更新凭据后才能继续上报。</p>}
        {modalError && <div className="form-error" role="alert">{modalError}</div>}<div className="modal-actions"><button type="button" className="secondary" onClick={() => setModal(null)} disabled={busy}>取消</button><button className="primary" disabled={busy}>{busy ? '处理中…' : modal.name === 'rotate' ? '确认重新签发' : modal.name === 'register' ? '生成凭据' : '保存'}</button></div></form>}
    </Modal>}
  </div>;
}
