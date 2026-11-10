/** 格式化时间，未采集时显示占位符。 */
export function time(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? '—' : date.toLocaleString('zh-CN', { hour12: false });
}

/** 将字节数转换为便于阅读的单位。 */
export function bytes(value) {
  if (value == null || !Number.isFinite(value)) return '—';
  if (value === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  return `${(value / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`;
}

/** 计算资源使用率。 */
export function percent(used, total) {
  return total > 0 ? Math.min(100, used / total * 100) : null;
}
