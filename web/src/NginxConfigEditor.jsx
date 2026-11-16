import { useEffect, useState } from 'react';
import { post, request } from './api';

/** 创建 Nginx 配置任务并展示执行结果。 */
export default function NginxConfigEditor({ token, serverId, ready, readinessMessage }) {
  const [key, setKey] = useState('');
  const [content, setContent] = useState('');
  const [original, setOriginal] = useState('');
  const [digest, setDigest] = useState('');
  const [taskId, setTaskId] = useState('');
  const [task, setTask] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [history, setHistory] = useState([]);
  const pending = Boolean(taskId) && (!task || ['WAITING', 'CLAIMED'].includes(task.state));
  useEffect(() => {
    if (!serverId) return undefined;
    let active = true;
    /** 读取最近的发布记录。 */
    async function loadHistory() {
      try { const rows = await request(token, `/servers/${serverId}/nginx/publications`); if (active) setHistory(rows); }
      catch (e) { if (active) setError(e.message); }
    }
    loadHistory(); const timer = setInterval(loadHistory, 5000);
    return () => { active = false; clearInterval(timer); };
  }, [token, serverId]);
  useEffect(() => { setContent(''); setOriginal(''); setDigest(''); setTaskId(''); setTask(null); setError(''); }, [serverId]);
  useEffect(() => {
    if (!taskId || !serverId) return undefined;
    let active = true;
    let timer;
    /** 定期读取任务状态，已领取任务不自动重试。 */
    async function refresh() {
      try {
        const value = await request(token, `/servers/${serverId}/nginx/tasks/${taskId}`);
        if (!active) return;
        setTask(value);
        if (['WAITING', 'CLAIMED'].includes(value.state)) timer = setTimeout(refresh, 2000);
        if (value.state === 'SUCCEEDED' && value.operation === 'READ') { setContent(value.content || ''); setOriginal(value.content || ''); setDigest(value.digest || ''); }
      } catch (e) { if (active) { setError(e.message); timer = setTimeout(refresh, 5000); } }
    }
    refresh();
    return () => { active = false; clearTimeout(timer); };
  }, [token, serverId, taskId]);
  /** 提交配置读取或发布任务。 */
  async function submit(operation) {
    if (pending || busy) return;
    if (!ready) { setError(readinessMessage); return; }
    if (!key.trim()) { setError('请输入 Agent 白名单中的实例标识'); return; }
    if (operation === 'PUBLISH' && (!digest || !window.confirm('确认发布此配置？Agent 将先校验配置，然后发送 reload。'))) return;
    setBusy(true); setError('');
    try {
      const result = await post(token, `/servers/${serverId}/nginx/tasks`, { instanceKey: key.trim(), operation, ...(operation === 'PUBLISH' ? { expectedDigest: digest, content } : {}) });
      setTask(null); setTaskId(result.id);
      setDigest('');
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <section className="nginx-editor"><div className="section-heading"><h3>Nginx 配置管理</h3><span>单文件操作</span></div><p>输入 Agent 本机白名单中的实例标识，读取配置后可编辑并发布。</p><div className="nginx-editor-controls"><input aria-label="Nginx 实例标识" placeholder="例如 gateway" value={key} disabled={busy || pending} onChange={e => { setKey(e.target.value); setDigest(''); }} /><button className="secondary" disabled={busy || pending || !serverId || !ready} onClick={() => submit('READ')}>读取配置</button></div>{!ready && <div className="inline-error">{readinessMessage}</div>}{error && <div className="inline-error">{error}</div>}{taskId && <p className="nginx-task">任务 {taskId}：{task?.state || '查询中'} {task?.waitingReason || task?.message || ''}</p>}{digest && <><p>原始版本：{digest}</p><textarea aria-label="Nginx 配置内容" rows="12" value={content} onChange={e => setContent(e.target.value)} /><details><summary>查看修改前的配置</summary><pre>{original}</pre></details><button className="primary" disabled={busy || pending || !ready || content === original} onClick={() => submit('PUBLISH')}>校验并发布</button></>}<details className="nginx-history"><summary>最近发布记录（{history.length}）</summary>{history.map(item => <p key={item.taskId}>{item.instanceKey} · {item.state} · {item.createdAt} · {item.message || '等待处理'}</p>)}</details></section>;
}
