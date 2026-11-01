'use strict';

const $ = id => document.getElementById(id);
const bridge = (name, method, args = []) => {
  const api = window[name];
  if (!api || typeof api[method] !== 'function') throw new Error('请在澄盾应用中使用此功能');
  return api[method](...args);
};
const call = (method, ...args) => bridge('Android', method, args);
const native = (method, ...args) => bridge('AndroidNative', method, args);
const read = (method, fallback, name = 'Android') => {
  try { return bridge(name, method); } catch (_) { return fallback; }
};
const json = (value, fallback = {}) => { try { return JSON.parse(value) ?? fallback; } catch (_) { return fallback; } };
const setText = (id, value) => { $(id).textContent = value; };
let toastTimer;
function toast(message) {
  $('toast').textContent = String(message || '已完成');
  $('toast').classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $('toast').classList.remove('visible'), 3500);
}
function showResult(title, value) {
  setText('result-title', title);
  let text = value == null ? '操作已完成' : String(value);
  try { text = JSON.stringify(JSON.parse(text), null, 2); } catch (_) {}
  setText('result-body', text);
  $('threat-actions').replaceChildren();
  const threats = json(text, {});
  for (const item of [...(threats.certain || []), ...(threats.suspect || [])]) {
    if (!item.pkg) continue;
    for (const [label, method] of [['隔离网络', 'blockAppNetwork'], ['自愿发送样本', 'offerSample']]) {
      const button = document.createElement('button'); button.className = 'secondary full'; button.textContent = label + ' · ' + (item.name || item.pkg);
      button.addEventListener('click', () => { try { call(method, item.pkg); } catch(error) {toast(error.message);} }); $('threat-actions').append(button);
    }
  }
  $('result-dialog').showModal();
}
function tab(name) {
  if (!['overview', 'protect', 'apps', 'tools', 'settings'].includes(name)) name = 'overview';
  document.querySelectorAll('.page').forEach(page => page.classList.toggle('active', page.id === name));
  document.querySelectorAll('.bottom-nav button').forEach(button => {
    const selected = button.dataset.tab === name;
    button.classList.toggle('selected', selected);
    if (selected) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current');
  });
  history.replaceState(null, '', '#' + name);
  window.scrollTo(0, 0);
  if (name === 'apps') loadApps();
  if (name === 'tools') refreshTools();
  refresh();
}
window.switchPage = page => {
  const number = Number(String(page).replace('PAGES', ''));
  tab(['overview', 'protect', 'apps', 'tools', 'settings'][number - 1] || 'overview');
};
window.onShizukuCommandResult = result => showResult('终端结果', result);
window.updateSensorData = () => {};
window.updateEnvironmentWarning = () => {};

const modes = [['basic', '基础'], ['daily', '日常'], ['daily30', '日常 30'], ['super', '超级'], ['extreme', '极强'], ['final', '终结']];
for (const [value, title] of modes) {
  const button = document.createElement('button');
  button.textContent = title;
  button.dataset.mode = value;
  $('modes').append(button);
}
function toggle(container, label, getter, setter) {
  const row = document.createElement('label');
  row.className = 'switch-row';
  const text = document.createElement('span'); text.textContent = label;
  const input = document.createElement('input'); input.type = 'checkbox'; input.setAttribute('role', 'switch');
  input.checked = !!read(getter, false); input.dataset.getter = getter;
  input.addEventListener('change', () => { try { call(setter, input.checked); toast('已保存'); } catch (error) { toast(error.message); } });
  row.append(text, input); $(container).append(row);
}
function range(container, label, getter, setter, min, max, fallback, name = 'Android') {
  const row = document.createElement('label');
  const heading = document.createElement('span'); heading.className = 'range-heading';
  const title = document.createElement('span'); title.textContent = label;
  const output = document.createElement('output');
  const input = document.createElement('input'); input.type = 'range'; input.min = min; input.max = max;
  input.value = read(getter, fallback, name); output.textContent = input.value;
  heading.append(title, output);
  input.addEventListener('input', () => { output.textContent = input.value; });
  input.addEventListener('change', () => { try { bridge(name, setter, [Number(input.value)]); } catch (error) { toast(error.message); } });
  row.append(heading, input); $(container).append(row);
}
toggle('trigger-settings', '音量键紧急逃生', 'getVolumeTriggerEnabled', 'setVolumeTriggerEnabled');
range('trigger-settings', '连续按键次数', 'getVolumeTriggerCount', 'setVolumeTriggerCount', 2, 10, 3);
const volumeLabel = document.createElement('label'); volumeLabel.textContent = '触发按键';
const volumeSelect = document.createElement('select');
for (const [value, label] of [['down', '音量减'], ['up', '音量加']]) {
  const option = document.createElement('option'); option.value = value; option.textContent = label; volumeSelect.append(option);
}
volumeSelect.value = read('getVolumeTriggerKey', 'down');
volumeSelect.addEventListener('change', () => { try { call('setVolumeTriggerKey', volumeSelect.value); } catch (error) { toast(error.message); } });
volumeLabel.append(volumeSelect); $('trigger-settings').append(volumeLabel);
toggle('trigger-settings', '摇一摇紧急逃生', 'getShakeTriggerEnabled', 'setShakeTriggerEnabled');
range('trigger-settings', '摇动灵敏度', 'getShakeSensitivity', 'setShakeSensitivity', 1, 10, 5);
toggle('trigger-settings', '自动风险拦截', 'getAutoBlockEnabled', 'setAutoBlockEnabled');
for (const [label, getter, setter, min, max, fallback] of [
  ['滤镜亮度', 'getBrightness', 'setBrightness', 0, 200, 100],
  ['对比度', 'getContrast', 'setContrast', 0, 200, 100],
  ['饱和度', 'getSaturation', 'setSaturation', 0, 200, 100],
  ['冷暖色', 'getTemperature', 'setTemperature', 0, 100, 50]
]) range('filter-settings', label, getter, setter, min, max, fallback, 'AndroidNative');

const permissionRows = [
  ['accessibility', '无障碍服务', () => call('openAccessibilitySettings')],
  ['overlay', '悬浮窗', () => call('openPermSettings', 'overlay')],
  ['usage', '使用情况访问', () => call('openPermSettings', 'usage')],
  ['notification', '通知权限', () => call('requestNotificationPermission')],
  ['applist', '应用列表访问', () => call('requestAppListPermission')],
  ['battery', '后台运行', () => call('openBatteryOptSettings')]
];
for (const [key, label, action] of permissionRows) {
  const button = document.createElement('button'); button.className = 'row-button';
  const title = document.createElement('span'); title.textContent = label;
  const state = document.createElement('span'); state.id = 'permission-' + key;
  button.append(title, state);
  button.addEventListener('click', () => { try { action(); } catch (error) { toast(error.message); } });
  $('permissions').append(button);
}
let protectOn = false;
function refresh() {
  const status = json(read('getProtectStatus', '{}'));
  const permissions = json(read('getMissingPermissions', '{}'));
  const entry = json(read('getEntryRequirements', '{}'));
  protectOn = !!status.on;
  const privilege = json(read('getActivationStatus', '{}'));
  const ready = !!privilege.ready;
  setText('protection-pill', protectOn ? '守护正在运行' : '守护尚未开启');
  setText('protection-title', protectOn ? '正在守护，\n安心使用。' : '你的设备，\n由你守护。');
  $('protection-title').style.whiteSpace = 'pre-line';
  setText('protection-description', protectOn ? '后台持续巡检，随时为你提供紧急救援。' : '检查授权并开启守护，让保护真正开始。');
  setText('protection-toggle', protectOn ? '关闭守护 ↗' : '开启守护 ↗');
  setText('priv-state', ready ? '已就绪' : '未激活'); $('priv-dot').classList.toggle('ready', ready);
  setText('acc-state', permissions.accessibility ? '已开启' : '未授权');
  setText('duration', status.duration || '—');
  setText('activation-status', privilege.summary || '尚未连接特权服务。可使用无线调试配对，或接入已安装的 Shizuku。');
  document.querySelectorAll('[data-mode]').forEach(button => button.classList.toggle('selected', button.dataset.mode === read('getShieldMode', 'basic')));
  for (const [key] of permissionRows) {
    const enabled = key === 'battery' ? read('isBatteryOptIgnored', false) : (permissions[key] ?? entry[key]);
    setText('permission-' + key, enabled ? '已就绪 ✓' : '去授权 ↗');
  }
  document.querySelectorAll('[data-getter]').forEach(input => { input.checked = !!read(input.dataset.getter, false); });
  setText('version', privilege.version || 'ClearGuard 1.0.0');
}
function refreshTools() {
  setText('device-facts', '可用内存 ' + read('getAvailableMemory', '—', 'AndroidNative') + ' · 电池温度 ' + read('getBatteryTemp', '—', 'AndroidNative') + '°C · ' + read('getCurrentColorMode', '标准', 'AndroidNative'));
}
let apps = [];
function loadApps() {
  apps = json(read('getInstalledApps', '[]'), []);
  if (!Array.isArray(apps)) apps = [];
  apps.sort((a, b) => String(a.name || a.pkg).localeCompare(String(b.name || b.pkg), 'zh'));
  renderApps();
}
function renderApps() {
  const query = $('app-search').value.trim().toLowerCase();
  const filtered = apps.filter(app => String(app.name || '').toLowerCase().includes(query) || String(app.pkg || '').toLowerCase().includes(query));
  setText('app-count', filtered.length + ' 个'); $('app-list').replaceChildren();
  if (!filtered.length) {
    const empty = document.createElement('p'); empty.className = 'hint'; empty.textContent = query ? '没有匹配的应用' : '未读取到应用，请先授予应用列表权限。'; $('app-list').append(empty);
  }
  for (const app of filtered) {
    const row = document.createElement('div'); row.className = 'app-row';
    const avatar = document.createElement('span'); avatar.className = 'app-avatar'; avatar.textContent = String(app.name || app.pkg || '?').slice(0, 1);
    const info = document.createElement('div'); info.className = 'app-info';
    const label = document.createElement('strong'); label.textContent = app.name || app.pkg;
    const pkg = document.createElement('small'); pkg.textContent = app.pkg;
    info.append(label, pkg);
    const details = document.createElement('button'); details.textContent = '详情'; details.setAttribute('aria-label', '查看 ' + (app.name || app.pkg));
    details.addEventListener('click', () => { try { call('openAppInfo', app.pkg); } catch (error) { toast(error.message); } });
    const block = document.createElement('button'); block.textContent = '管控';
    block.addEventListener('click', () => { if (confirm('将「' + (app.name || app.pkg) + '」加入黑名单？')) { try { call('addSingleToBlacklist', app.pkg); toast('已加入黑名单'); } catch (error) { toast(error.message); } } });
    const network = document.createElement('button'); network.textContent = '断网'; network.addEventListener('click', () => { try { call('blockAppNetwork', app.pkg); } catch(error) {toast(error.message);} });
    const remove = document.createElement('button'); remove.textContent = '处置'; remove.addEventListener('click', () => {try {call('openRemoval', app.pkg);} catch(error) {toast(error.message);}});
    row.append(avatar, info, details, block, network, remove); $('app-list').append(row);
  }
}
$('app-search').addEventListener('input', renderApps);

const actions = {
  refresh: () => { refresh(); toast('状态已刷新'); },
  'toggle-protect': () => {
    if (protectOn) { if (!confirm('关闭守护与紧急逃生触发器？')) return; call('stopProtect'); }
    else { const result = json(call('startProtect')); if (result.status === 'need_permission') toast('请先完成：' + result.missing); }
    setTimeout(refresh, 400);
  },
  activate: () => call('openActivation'),
  terminal: () => call('openTerminal'),
  'native-apps': () => call('openNativeApps'),
  'runtime-logs': () => call('openRuntimeLogs'),
  rescue: () => { if (confirm('立即打开紧急救援？')) call('triggerShakeAlert'); },
  'request-privilege': () => call('requestSelectedPrivilege'),
  'save-source': () => { call('configurePrivilegeSource', Number($('priv-source').value), $('external-shizuku').checked); refresh(); toast('特权来源已保存'); },
  'save-ad': () => {
    const split = id => $(id).value.split(/[,，\n]/).map(item => item.trim()).filter(Boolean);
    const config = { enabled: $('ad-enabled').checked, customKeywords: split('ad-keywords'), monitorApps: split('ad-monitor'), blacklistApps: split('ad-exclude') };
    call('updateAdSkipConfig', JSON.stringify(config)); localStorage.setItem('ad-config', JSON.stringify(config)); toast('广告跳过配置已保存');
  },
  'deep-clean': () => { if (confirm('清理缓存和垃圾文件？此操作无法撤销，请确认已保存重要内容。')) runResult('performDeepClean', '深度清理'); },
  'trust-all': () => { if (confirm('信任当前全部应用将减少拦截保护。确认继续？')) { call('trustAllApps'); toast('当前应用已加入信任列表'); } },
  'clear-crash': () => { if (confirm('清空崩溃日志？')) { call('clearCrashLog'); toast('日志已清空'); } },
  'close-dialog': () => $('result-dialog').close(),
  'copy-result': () => { call('copyText', $('result-body').textContent); toast('已复制'); }
};
function runResult(method, title, button) {
  if (button) button.disabled = true;
  toast('正在处理，请稍候…');
  setTimeout(() => {
    try { showResult(title, call(method)); } catch (error) { toast(error.message); }
    finally { if (button) button.disabled = false; }
  }, 80);
}
document.addEventListener('click', event => {
  const button = event.target.closest('button'); if (!button) return;
  try {
    if (button.dataset.tab) tab(button.dataset.tab);
    else if (button.dataset.action) actions[button.dataset.action]?.();
    else if (button.dataset.result) runResult(button.dataset.result, button.dataset.title, button);
    else if (button.dataset.call) { call(button.dataset.call); toast('已执行'); }
    else if (button.dataset.native) { native(button.dataset.native); refreshTools(); toast('已执行'); }
    else if (button.dataset.preset !== undefined) { native('setPreset', Number(button.dataset.preset)); toast('滤镜预设已更新'); }
    else if (button.dataset.mode) {
      if (['super', 'extreme', 'final'].includes(button.dataset.mode) && !confirm('此模式可能停止、冻结或卸载应用，确认选择？')) return;
      call('setShieldMode', button.dataset.mode); refresh(); toast('拦截策略已保存');
    }
  } catch (error) { toast(error.message); }
});
$('hide-recents').checked = !!read('getHideRecents', false);
$('hide-recents').addEventListener('change', () => { try { call('setHideRecents', $('hide-recents').checked); } catch (error) { toast(error.message); } });
$('system-brightness').addEventListener('change', () => { try { native('setScreenBrightness', Number($('system-brightness').value)); } catch (error) { toast(error.message); } });
const activation = json(read('getActivationStatus', '{}'));
$('priv-source').value = activation.source || 0; $('external-shizuku').checked = !!activation.externalAllowed;
const adConfig = json(localStorage.getItem('ad-config'), {});
$('ad-enabled').checked = !!adConfig.enabled;
for (const [id, key] of [['ad-keywords', 'customKeywords'], ['ad-monitor', 'monitorApps'], ['ad-exclude', 'blacklistApps']]) $(id).value = (adConfig[key] || []).join(',');
function appearance() {
  const preference = $('appearance').value;
  document.documentElement.dataset.theme = preference === 'system' ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light') : preference;
  localStorage.setItem('appearance', preference);
}
$('appearance').value = localStorage.getItem('appearance') || 'system';
$('appearance').addEventListener('change', appearance);
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', appearance);
appearance();
window.addEventListener('focus', refresh);
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
setInterval(() => { if (!document.hidden) refresh(); }, 15000);
tab(location.hash.slice(1) || 'overview');

$('refresh-rate').addEventListener('change', () => { try { native('setRefreshRate', Number($('refresh-rate').value)); } catch (error) { toast(error.message); } });
$('render-mode').addEventListener('change', () => { try { native('setRenderMode', $('render-mode').value); } catch (error) { toast(error.message); } });
