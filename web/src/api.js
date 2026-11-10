/** 使用当前管理员凭据访问同源管理接口。 */
export async function request(token, path, options = {}) {
  const response = await fetch(`/api/admin${path}`, {
    ...options,
    headers: { Authorization: `Bearer ${token}`, ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
    cache: 'no-store',
  });
  if (!response.ok) {
    let message = response.status === 401 ? '管理员令牌无效或已失效' : `请求失败（${response.status}）`;
    try { const body = await response.json(); if (body.message) message = body.message; } catch { /* 非 JSON 错误使用默认提示。 */ }
    const error = new Error(message); error.status = response.status; throw error;
  }
  return response.status === 204 ? null : response.json();
}

/** 以 JSON 格式提交数据。 */
export function post(token, path, body) {
  return request(token, path, { method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
}
