// ═══════════════════════════════════════════
// DeepSeek Harness — Frontend v15
// minimal · dark-first
// ═══════════════════════════════════════════

// Auto-bust CSS cache on every page load
(function () {
  var css = document.getElementById('app-css');
  if (css) css.href = '/app.css?v=' + Date.now();
})();

const API = '/api/agent';
const SETTINGS_API = '/api/harness/settings/models';
const MODEL_CATALOG_API = '/api/harness/runtime/models';
const MODEL_DISCOVER_API = '/api/harness/settings/models/discover';
const CHANNEL_PRESETS_API = '/api/harness/channels/presets';
const DEFAULT_CHANNEL = '';
const PLUGIN_API = '/api/harness/plugins';
const CONSOLE_API = '/api/harness/console';
const APPROVAL_API = '/api/harness/approvals';

async function apiRequest(path, options = {}) {
  const response = await fetch(path, options);
  let payload = null;
  try {
    payload = await response.json();
  } catch (error) {
    payload = null;
  }

  const succeeded = response.ok && payload && payload.code === '00000';
  if (!succeeded) {
    throw new Error((payload && payload.info) || ('HTTP ' + response.status));
  }
  return payload.data;
}

const SSE_TIMEOUT_MS = 300_000;
const MAX_RETRIES = 2;
const RETRY_DELAY_MS = 1500;

const ERROR_MAP = {
  'HTTP 400': '请求格式有误，请检查输入',
  'HTTP 401': 'API 密钥无效，请检查设置',
  'HTTP 403': '无访问权限，请检查 API 密钥',
  'HTTP 404': '接口不存在，请检查服务配置',
  'HTTP 429': '请求过于频繁，请稍后重试',
  'HTTP 500': '服务暂时不可用，请稍后重试',
  'HTTP 502': '网关错误，请稍后重试',
  'HTTP 503': '服务不可用，请稍后重试',
  'EMPTY_RESPONSE': '模型返回了空内容，请重试',
  'STREAM_CLOSED': '连接意外断开，请重试',
  'MALFORMED_RESPONSE': '响应格式异常，请重试',
  'AUTH': '认证失败，请检查 API 密钥',
  'RATE_LIMIT': '请求频率超限，请稍后重试',
};

function translateError(msg) {
  if (!msg) return '未知错误';
  for (var key in ERROR_MAP) {
    if (msg.indexOf(key) !== -1) return ERROR_MAP[key];
  }
  return msg;
}

// ── Marked 配置 ────────────────────────────
marked.setOptions({ breaks: true, gfm: true, headerIds: false, mangle: false });

const _renderer = new marked.Renderer();
_renderer.code = function(token) {
  const code = (typeof token === 'object' && token !== null) ? (token.text || '') : token;
  const lang = (typeof token === 'object' && token !== null) ? (token.lang || '') : '';
  const language = (lang || '').trim() || 'text';
  let highlighted;
  try {
    if (typeof hljs !== 'undefined' && language !== 'text') {
      highlighted = hljs.getLanguage(language)
        ? hljs.highlight(code, { language }).value
        : hljs.highlightAuto(code).value;
    } else { highlighted = code; }
  } catch (e) { highlighted = code; }
  return '<div class="code-block-wrapper">'
    + '<span class="code-lang-label">' + language + '</span>'
    + '<button class="code-copy-btn" data-action="copy">复制</button>'
    + '<pre><code class="hljs language-' + language + '">' + highlighted + '</code></pre>'
    + '</div>';
};
marked.use({ renderer: _renderer });

// Event delegation for dynamically rendered buttons (replaces inline onclick)
document.addEventListener('click', function(e) {
  var t = e.target;
  if (!t) return;
  if (t.classList.contains('code-copy-btn')) {
    e.preventDefault();
    copyCodeBlock(t);
  }
  if (t.classList.contains('collapsible-header') || t.classList.contains('think-block-header')) {
    e.preventDefault();
    t.parentElement.classList.toggle('expanded');
  }
});

// ── 会话状态 ───────────────────────────────
class ChatSession {
  constructor(agentId) {
    this.agentId = agentId;
    this.displayName = '对话 ' + (state.sessions.length + 1);
    this.messages = [];
    this.sending = false;
    this.streaming = false;
    this.error = null;
    this.abortController = null;
    this.waitTimer = null;
    this.waitStartTime = null;
    this.stats = { turns: 0, steps: 0, llmTimeMs: 0, firstTokenMs: 0, inputTokens: 0, outputTokens: 0, cacheHitRate: 0 };
    this.title = '新对话';
    this.lastMessage = '';
    this.createdAt = new Date().toISOString();
    this.workspace = state.currentWorkspace || '';
    this.approvalMode = 'REQUEST_APPROVAL';
    this.approvalPollTimer = null;
    // P0: 消息排队
    this.queue = [];
    // P1: To-do 列表
    this.todos = [];
    // P1: Thinking
    this.thinking = null;
    // P2: Plan
    this.plan = null;
    // P2: Goal
    this.goal = null;
    // P2: 子代理
    this.subagents = [];
    // P2: 交付物
    this.deliverables = [];
    // P2: 后台任务
    this.jobs = [];
    // P2: 轨迹
    this.trajectory = [];
    // v28: 完整执行日志轨迹
    this.trajectoryLog = [];
    this._trajTurn = 0;
    this._trajSeq = 0;
    // P2: 引用
    this.references = [];
    // P2: 会话回忆
    this.recalls = [];
    // P2: 上下文压缩
    this.compactionInfo = null;
    // P2: 工作流
    this.workflow = null;
  }
}

// ── 全局状态 ───────────────────────────────
const state = {
  sessions: [],
  currentSessionId: null,
  theme: localStorage.getItem('theme') || 'light',
  sidebarCollapsed: JSON.parse(localStorage.getItem('sidebarCollapsed') || 'false'),
  sidebarSessionLimit: 10,
  hasMoreSessions: true,
  loadingMoreSessions: false,
  workspaces: JSON.parse(localStorage.getItem('workspaces') || '[]'),
  currentWorkspace: localStorage.getItem('currentWorkspace') || '',
  modelSettings: [],
  availableModels: [],
  currentChannel: localStorage.getItem('currentChannel') || DEFAULT_CHANNEL,
  activeMainTab: 'chat',
  activeSettingsTab: 'models',
  plugins: [],
  pluginConfigs: {},
  selectedPluginId: null,
  // v11: 审批队列
  pendingApprovals: [],
  // v11: 侧边面板
  activePanel: null, // 'todos' | 'plan' | 'goal' | 'trajectory' | 'subagents' | 'deliverables' | 'jobs' | 'recall' | null
  // v29: 轨迹视图搜索关键字
  trajSearch: '',
  // v29.1: 轨迹明细列表倒序（true=最新在上，默认）
  trajDesc: true,
  // v11: 会话历史
  sessionHistory: [],
  sessionHistoryLimit: 20,
  // v11: 斜杠命令
  slashCommands: [
    { name: '/help', desc: '显示帮助信息', icon: '❓' },
    { name: '/clear', desc: '清空当前对话', icon: '🗑' },
    { name: '/export', desc: '导出对话记录', icon: '📥' },
    { name: '/compact', desc: '压缩上下文', icon: '📦' },
    { name: '/branch', desc: '分支当前对话', icon: '🌿' },
    { name: '/channel', desc: '切换模型渠道', icon: '🤖' },
    { name: '/workspace', desc: '切换工作区', icon: '📁' },
    { name: '/todo', desc: '管理待办列表', icon: '✓' },
    { name: '/plan', desc: '查看/管理计划', icon: '📋' },
    { name: '/goal', desc: '查看/管理目标', icon: '🎯' },
    { name: '/skills', desc: '查看可用技能', icon: '⚡' },
    { name: '/agents', desc: '查看子代理', icon: '🤖' },
    { name: '/jobs', desc: '查看后台任务', icon: '⚙' },
    { name: '/feedback', desc: '提交反馈', icon: '💬' },
  ],
  // v11: 技能列表
  skills: [],
  // v11: Agent 预设
  agentPresets: [
    { name: '默认助手', desc: '通用对话助手', model: null, systemPrompt: '' },
    { name: '代码专家', desc: '专注代码生成与调试', model: null, systemPrompt: 'You are a coding expert.' },
    { name: '文档写手', desc: '专注文档撰写', model: null, systemPrompt: 'You are a technical writer.' },
  ],
  // v11: 权限预设
  permissionPresets: [
    { name: '只读', desc: '仅允许读取操作', permissions: { read: true, write: false, exec: false, network: false } },
    { name: '标准', desc: '允许读取和有限写入', permissions: { read: true, write: true, exec: false, network: false } },
    { name: '完全', desc: '允许所有操作', permissions: { read: true, write: true, exec: true, network: true } },
  ],
  // v11: 消息反馈
  messageFeedback: {},
};

// ── 工具函数 ───────────────────────────────
const $ = (s) => document.querySelector(s);
const $$ = (s) => document.querySelectorAll(s);

function el(tag, attrs, ...children) {
  const e = document.createElement(tag);
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v == null || v === false) continue;
      if (k === 'class') e.className = v;
      else if (k === 'id') e.id = v;
      else if (k === 'onclick') e.onclick = v;
      else if (k === 'oninput') e.oninput = v;
      else if (k === 'onchange') e.onchange = v;
      else if (k === 'onkeydown') e.onkeydown = v;
      else if (k === 'onkeyup') e.onkeyup = v;
      else if (k === 'onpaste') e.onpaste = v;
      else if (k === 'ondragover') e.ondragover = v;
      else if (k === 'ondrop') e.ondrop = v;
      else if (k === 'style') e.style.cssText = v;
      else if (k === 'html') e.innerHTML = v;
      else if (k === 'text') e.textContent = v;
      else e.setAttribute(k, v);
    }
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    if (typeof c === 'string' || typeof c === 'number') e.appendChild(document.createTextNode(String(c)));
    else if (c instanceof Node) e.appendChild(c);
  }
  return e;
}

function icon(name, className) {
  const icons = {
    brand: "<span class='brand-emoji' aria-hidden='true'>🐳</span>",
    panel: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><rect x='4.5' y='5' width='15' height='14' rx='3' stroke='currentColor' stroke-width='1.7'/><path d='M10 5.8v12.4' stroke='currentColor' stroke-width='1.7' stroke-linecap='round'/></svg>",
    plus: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><path d='M12 7v10M7 12h10' stroke='currentColor' stroke-width='1.8' stroke-linecap='round'/><circle cx='12' cy='12' r='8.5' stroke='currentColor' stroke-width='1.5' opacity='.45'/></svg>",
    plusPlain: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><path d='M12 7v10M7 12h10' stroke='currentColor' stroke-width='1.8' stroke-linecap='round'/></svg>",
    folder: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><path d='M4.5 8.5a2 2 0 0 1 2-2h3l1.7 2H17.5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-7Z' stroke='currentColor' stroke-width='1.7' stroke-linejoin='round'/></svg>",
    search: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><circle cx='11' cy='11' r='5.5' stroke='currentColor' stroke-width='1.7'/><path d='m16 16 3.5 3.5' stroke='currentColor' stroke-width='1.7' stroke-linecap='round'/></svg>",
    sliders: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><path d='M5 7h6M15 7h4M5 17h4M13 17h6M5 12h10M19 12h0' stroke='currentColor' stroke-width='1.7' stroke-linecap='round'/><circle cx='13' cy='7' r='2' stroke='currentColor' stroke-width='1.5'/><circle cx='10' cy='17' r='2' stroke='currentColor' stroke-width='1.5'/><circle cx='18' cy='12' r='2' stroke='currentColor' stroke-width='1.5'/></svg>",
    addWorkspace: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><path d='M13.5 6.5h-7a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3' stroke='currentColor' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'/><path d='M15.5 5.5v6M12.5 8.5h6' stroke='currentColor' stroke-width='1.7' stroke-linecap='round'/></svg>",
    settings: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><path d='M12 8.7a3.3 3.3 0 1 0 0 6.6 3.3 3.3 0 0 0 0-6.6Z' stroke='currentColor' stroke-width='1.7'/><path d='M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1a7.5 7.5 0 0 0-1.7-1l-.3-2.5h-4l-.3 2.5a7.5 7.5 0 0 0-1.7 1l-2.4-1-2 3.4 2 1.5a7 7 0 0 0 0 2l-2 1.5 2 3.4 2.4-1a7.5 7.5 0 0 0 1.7 1l.3 2.5h4l.3-2.5a7.5 7.5 0 0 0 1.7-1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1Z' stroke='currentColor' stroke-width='1.4' stroke-linejoin='round'/></svg>",
    pencil: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><path d='M4 20h4L18.5 9.5a2.1 2.1 0 0 0-3-3L5 17v3Z' stroke='currentColor' stroke-width='1.7' stroke-linejoin='round'/><path d='M14.5 7.5l3 3' stroke='currentColor' stroke-width='1.7' stroke-linecap='round'/></svg>",
    trash: "<svg viewBox='0 0 24 24' fill='none' aria-hidden='true'><path d='M5 7h14M10 7V5h4v2M6.5 7l.7 12a2 2 0 0 0 2 1.9h5.6a2 2 0 0 0 2-1.9L17.5 7' stroke='currentColor' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'/></svg>",
  };
  return el('span', { class: 'icon' + (className ? ' ' + className : ''), html: icons[name] || '' });
}

function getCurrentSession() { return state.sessions.find(s => s.agentId === state.currentSessionId) || null; }
function wsEntryPath(w) { return typeof w === 'string' ? w : (w && w.path) || ''; }
function wsEntryName(w) {
  if (typeof w === 'object' && w && w.name) return w.name;
  var p = wsEntryPath(w);
  return p ? p.split('/').filter(Boolean).pop() : '';
}
function getSession(id) { return state.sessions.find(s => s.agentId === id) || null; }

// v28: localStorage 持久化对话记录
function saveSessionsToStorage() {
  try {
    var minimal = state.sessions.map(function(s) {
        return {
        agentId: s.agentId,
        sessionId: s.sessionId || null,
        title: s.title,
        displayName: s.displayName,
        lastMessage: s.lastMessage,
        createdAt: s.createdAt,
        workspace: s.workspace,
        messages: (s.messages || []).map(function(m) {
          return {
            role: m.role,
            content: typeof m.content === 'string' ? m.content : '',
            blocks: m.blocks,
            reasoning: m.reasoning,
            time: m.time,
            toolName: m.toolName,
            callId: m.callId,
            args: m.args,
            result: m.result,
            status: m.status,
            streaming: false,
          };
        }),
        trajectoryLog: s.trajectoryLog || [],
        _trajTurn: s._trajTurn || 0,
        _trajSeq: s._trajSeq || 0,
      };
    });
    localStorage.setItem('dshSessions', JSON.stringify(minimal));
  } catch (e) { /* quota */ }
}

function loadSessionsFromStorage() {
  try {
    var raw = localStorage.getItem('dshSessions');
    if (!raw) return;
    var saved = JSON.parse(raw);
    if (!Array.isArray(saved)) return;
    state.sessions = saved.map(function(s) {
      var sess = new ChatSession(s.agentId);
      sess.sessionId = s.sessionId || null;
      sess.title = s.title || '历史对话';
      sess.displayName = s.displayName || sess.title;
      sess.lastMessage = s.lastMessage || '';
      sess.createdAt = s.createdAt || new Date().toISOString();
      sess.workspace = (s.workspace === 'default') ? '' : (s.workspace || '');
      sess.messages = (s.messages || []).map(function(m) {
        m.streaming = false;
        m.meta = null;
        return m;
      });
      sess.trajectoryLog = s.trajectoryLog || [];
      sess._trajTurn = s._trajTurn || 0;
      sess._trajSeq = s._trajSeq || 0;
      sess.sending = false;
      sess.streaming = false;
      return sess;
    });
  } catch (e) { /* ignore */ }
}

// v29: 轨迹日志记录 — 增加 t0/t1 epoch 毫秒时间戳，驱动时间线甘特图渲染
function trajPush(sess, kind, text, extra) {
  sess.trajectoryLog = sess.trajectoryLog || [];
  sess._trajSeq = (sess._trajSeq || 0) + 1;
  if (kind === 'user') sess._trajTurn = (sess._trajTurn || 0) + 1;
  extra = extra || {};
  var now = Date.now();
  var t0 = extra.t0 != null ? extra.t0 : now;
  var t1 = extra.t1 != null ? extra.t1 : (kind === 'tool' || kind === 'message' ? t0 : t0 + 1);
  const entry = {
    seq: sess._trajSeq,
    turn: sess._trajTurn || 0,
    kind: kind,
    text: (text || ""),
    time: new Date(t0).toISOString(),
    t0: t0,
    t1: t1,
    duration: extra.duration ? extra.duration : null,
    tokens: extra.tokens != null ? extra.tokens : null,
    input: extra.input != null ? extra.input : null,
    output: extra.output != null ? extra.output : null,
    toolName: extra.toolName ? extra.toolName : null,
    status: extra.status ? extra.status : null,
    error: extra.error ? extra.error : null,
  };
  sess.trajectoryLog.push(entry);
  // 侧边面板 / 主区域轨迹实时刷新
  if (state.currentSessionId === sess.agentId && (state.activePanel === 'trajectory' || state.activeMainTab === 'trajectory')) render();
  return entry;
}

// v29: 回填轨迹中 running 状态的工具条目（done 事件到达时补充结果/状态/结束时间）
function trajSettleTools(sess, doneMessages) {
  if (!sess.trajectoryLog || !Array.isArray(doneMessages)) return;
  var running = sess.trajectoryLog.filter(function(e) { return e.kind === 'tool' && e.status === 'running'; });
  if (running.length === 0) return;
  var toolResults = doneMessages.filter(function(m) { return m.role === 'tool'; });
  for (var i = 0; i < running.length && i < toolResults.length; i++) {
    var entry = running[i];
    var tr = toolResults[i];
    entry.status = tr.status || 'success';
    var durMs = typeof tr.duration === 'number' ? tr.duration : (tr.duration ? parseFloat(tr.duration) * 1000 : null);
    if (durMs && isFinite(durMs) && durMs > 0) {
      entry.t1 = entry.t0 + durMs;
      entry.duration = (durMs / 1000).toFixed(1) + 's';
    } else {
      entry.t1 = Date.now();
      entry.duration = ((entry.t1 - entry.t0) / 1000).toFixed(1) + 's';
    }
    if (tr.result != null) {
      var rText = typeof tr.result === 'string' ? tr.result : JSON.stringify(tr.result);
      entry.text = (rText || '').slice(0, 300);
      entry.result = (rText || '').slice(0, 4000);
    }
    if (tr.error) entry.error = String(tr.error).slice(0, 300);
  }
}

// ── 主题 ───────────────────────────────────
function initTheme() { document.documentElement.setAttribute('data-theme', state.theme); }
function toggleTheme() {
  state.theme = state.theme === 'light' ? 'dark' : 'light';
  localStorage.setItem('theme', state.theme);
  initTheme();
  render();
}

// ═══════════════════════════════════════════
// RENDER 层
// ═══════════════════════════════════════════
function render() {
  const root = $('#app') || document.body;
  root.innerHTML = '';
  root.appendChild(state.activeMainTab === 'settings' ? renderSettingsPage() : renderLayout());
}

function renderLayout() {
  const app = el('div', { class: 'app' });
  app.appendChild(renderSidebar());
  const mainArea = el('div', { class: 'main-area' });
  mainArea.appendChild(renderMain());
  app.appendChild(mainArea);
  return app;
}

// ── 侧边栏 ─────────────────────────────────
function renderSidebar() {
  const sidebar = el('aside', { class: 'sidebar' + (state.sidebarCollapsed ? ' collapsed' : '') });
  const firstWs = state.workspaces[0];
  const currentWorkspace = state.currentWorkspace || wsEntryPath(firstWs) || '';
  const currentWsObj = state.workspaces.find(function(w) { return wsEntryPath(w) === currentWorkspace; }) || firstWs;
  const workspaceName = currentWorkspace ? wsEntryName(currentWsObj) || currentWorkspace.split('/').filter(Boolean).pop() : '切换工作区';

  const collapsedBar = el('div', { class: 'sidebar-collapsed-bar' });
  collapsedBar.appendChild(icon('brand', 'c-logo'));
  const expandBtn = el('button', { class: 'c-btn', onclick: toggleSidebar, title: '展开侧边栏' });
  expandBtn.appendChild(icon('panel'));
  expandBtn.appendChild(el('span', { class: 'c-tooltip' }, '展开'));
  collapsedBar.appendChild(expandBtn);
  collapsedBar.appendChild(el('div', { class: 'c-divider' }));
  const newChatRound = el('button', { class: 'c-btn', onclick: newChat, title: '新会话' });
  newChatRound.appendChild(icon('plusPlain'));
  newChatRound.appendChild(el('span', { class: 'c-tooltip' }, '新会话'));
  collapsedBar.appendChild(newChatRound);
  const wsRound = el('button', { class: 'c-btn', title: workspaceName, onclick: function() { showDirectoryPicker(function(path) { state.currentWorkspace = path; localStorage.setItem('currentWorkspace', path); render(); }); } });
  wsRound.appendChild(icon('folder'));
  wsRound.appendChild(el('span', { class: 'c-tooltip' }, workspaceName));
  collapsedBar.appendChild(wsRound);
  const settingsRound = el('button', { class: 'c-btn', onclick: function() { openSettingsPage(); }, title: '设置' });
  settingsRound.appendChild(icon('settings'));
  settingsRound.appendChild(el('span', { class: 'c-tooltip' }, '设置'));
  collapsedBar.appendChild(settingsRound);
  collapsedBar.appendChild(el('div', { class: 'c-spacer' }));
  collapsedBar.appendChild(el('div', { class: 'c-model-dot', title: state.currentChannel || '当前激活渠道' }));
  sidebar.appendChild(collapsedBar);

  const expandSection = el('div', { class: 'sidebar-expand-section' });

  const header = el('div', { class: 'sidebar-header' });
  const brand = el('div', { class: 'sidebar-brand' });
  brand.appendChild(icon('brand', 'sidebar-logo'));
  const wordmark = el('div', { class: 'sidebar-wordmark' });
  wordmark.appendChild(el('span', { class: 'sidebar-title' }, 'deepseek'));
  wordmark.appendChild(el('span', { class: 'sidebar-badge' }, 'HARNESS'));
  wordmark.appendChild(el('span', { class: 'sidebar-java-tag' }, 'Java'));
  brand.appendChild(wordmark);
  header.appendChild(brand);
  const toggleBtn = el('button', { class: 'sidebar-toggle', onclick: toggleSidebar, title: '收起侧边栏' });
  toggleBtn.appendChild(icon('panel'));
  header.appendChild(toggleBtn);
  expandSection.appendChild(header);

  const newBtn = el('button', { class: 'sidebar-new-btn', onclick: newChat });
  newBtn.appendChild(icon('plus', 'sidebar-new-btn-icon'));
  newBtn.appendChild(el('span', null, '新会话'));
  expandSection.appendChild(newBtn);

  // Workspace action toolbar
  const wsToolbar = el('div', { class: 'ws-toolbar' });
  const pickBtn = el('button', {
    class: 'workspace-action-btn',
    title: '切换工作区',
    onclick: function() {
      showDirectoryPicker(function(path) {
        state.currentWorkspace = path;
        localStorage.setItem('currentWorkspace', path);
        render();
      });
    }
  });
  pickBtn.appendChild(icon('search'));
  wsToolbar.appendChild(pickBtn);
  const filterBtn = el('button', { class: 'workspace-action-btn', title: '设置', onclick: function() { openSettingsPage(); } });
  filterBtn.appendChild(icon('sliders'));
  wsToolbar.appendChild(filterBtn);
  const clearBtn = el('button', {
    class: 'workspace-action-btn danger',
    title: '清空对话',
    disabled: state.sessions.length === 0 ? 'disabled' : null,
    onclick: confirmClearSessions
  });
  clearBtn.appendChild(icon('trash'));
  wsToolbar.appendChild(clearBtn);
  expandSection.appendChild(wsToolbar);

  // Unified workspace + session list
  const sessionList = el('div', { class: 'session-list' });
  let dragSrcName = null;

  function renderSessionsForWorkspace(wsPath) {
    const wsSessions = state.sessions.filter(function(sess) {
      return (sess.workspace || '') === wsPath;
    });
    const group = el('div', { class: 'ws-session-group' });
    if (wsSessions.length === 0) {
      group.appendChild(el('div', { class: 'ws-session-empty' }, '暂无对话'));
    } else {
      const display = wsSessions.slice(0, state.sidebarSessionLimit);
      for (const sess of display) {
        group.appendChild(renderSessionItem(sess));
      }
      const hidden = wsSessions.length - state.sidebarSessionLimit;
      if (hidden > 0 || state.hasMoreSessions) {
        group.appendChild(el('button', {
          class: 'session-expand-btn',
          disabled: state.loadingMoreSessions ? 'disabled' : null,
          onclick: loadMoreSessions
        }, state.loadingMoreSessions ? '加载中…' : '加载更多'));
      }
    }
    return group;
  }

  function renderWorkspaceRow(p, n, isActive) {
    const wrap = el('div', { class: 'ws-session-wrap' + (isActive ? ' active' : '') });
    const header = el('div', { class: 'ws-session-header', draggable: 'true', title: p });
    header.appendChild(icon('folder', 'ws-session-header-icon'));
    header.appendChild(el('span', { class: 'ws-session-header-name' }, n));
    const wsCount = state.sessions.filter(function(s) { return (s.workspace || '') === p; }).length;
    header.appendChild(el('span', { class: 'ws-session-header-count' }, String(wsCount)));
    // rename + delete actions
    const actions = el('div', { class: 'ws-session-header-actions' });
    const newChatBtn = el('button', { class: 'workspace-item-btn', title: '在此空间新建对话', onclick: function(e) {
      e.stopPropagation();
      state.currentWorkspace = p;
      localStorage.setItem('currentWorkspace', p);
      newChat();
    } });
    newChatBtn.appendChild(icon('addWorkspace', 'workspace-item-btn-icon'));
    actions.appendChild(newChatBtn);
    const renameBtn = el('button', { class: 'workspace-item-btn', title: '重命名', onclick: function(e) { e.stopPropagation(); showRenameWorkspaceForm(n); } });
    renameBtn.appendChild(icon('pencil', 'workspace-item-btn-icon'));
    actions.appendChild(renameBtn);
    const delBtn = el('button', { class: 'workspace-item-btn', title: '删除', onclick: function(e) { e.stopPropagation(); confirmDeleteWorkspace(n); } });
    delBtn.appendChild(icon('trash', 'workspace-item-btn-icon'));
    actions.appendChild(delBtn);
    header.appendChild(actions);
    header.onclick = function() {
      state.currentWorkspace = p;
      localStorage.setItem('currentWorkspace', p);
      render();
    };
    header.ondragstart = function(e) { dragSrcName = n; e.dataTransfer.effectAllowed = 'move'; try { e.dataTransfer.setData('text/plain', n); } catch (_) {} };
    header.ondragover = function(e) { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; header.classList.add('drag-over'); };
    header.ondragleave = function() { header.classList.remove('drag-over'); };
    header.ondrop = function(e) {
      e.preventDefault();
      header.classList.remove('drag-over');
      var src = dragSrcName;
      dragSrcName = null;
      if (!src || src === n) return;
      reorderWorkspaces(src, n);
    };
    wrap.appendChild(header);
    if (isActive) {
      wrap.appendChild(renderSessionsForWorkspace(p));
    }
    return wrap;
  }

  if (state.workspaces.length === 0) {
    const allSessions = state.sessions.slice();
    if (allSessions.length === 0) {
      sessionList.appendChild(el('div', { class: 'empty' }, '暂无对话'));
    } else {
      const display = allSessions.slice(0, state.sidebarSessionLimit);
      for (const sess of display) {
        sessionList.appendChild(renderSessionItem(sess));
      }
      if (allSessions.length > state.sidebarSessionLimit || state.hasMoreSessions) {
        sessionList.appendChild(el('button', {
          class: 'session-expand-btn',
          disabled: state.loadingMoreSessions ? 'disabled' : null,
          onclick: loadMoreSessions
        }, state.loadingMoreSessions ? '加载中…' : '加载更多'));
      }
    }
  } else {
    for (const w of state.workspaces) {
      const p = wsEntryPath(w);
      if (!p) continue;
      const n = wsEntryName(w);
      sessionList.appendChild(renderWorkspaceRow(p, n, p === currentWorkspace));
    }
    const orphanSessions = state.sessions.filter(function(s) { return !s.workspace; });
    if (orphanSessions.length > 0) {
      sessionList.appendChild(renderWorkspaceRow('', '默认', !currentWorkspace));
    }
  }
  expandSection.appendChild(sessionList);

  const footer = el('div', { class: 'sidebar-footer' });
  const settingsBtn = el('button', { class: 'sidebar-settings-link', onclick: function() { openSettingsPage(); }, title: '设置' });
  settingsBtn.appendChild(icon('settings', 'sidebar-settings-icon'));
  settingsBtn.appendChild(el('span', null, '设置'));
  footer.appendChild(settingsBtn);
  expandSection.appendChild(footer);

  sidebar.appendChild(expandSection);
  return sidebar;
}

function renderSessionItem(sess) {
  const item = el('div', {
    class: 'session-item' + (sess.agentId === state.currentSessionId ? ' active' : ''),
    role: 'button',
    tabindex: '0',
    onclick: function() { selectSession(sess.agentId); },
    onkeydown: function(e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); selectSession(sess.agentId); }
    }
  });
  const row = el('div', { class: 'session-item-row' });
  row.appendChild(el('div', {
    class: 'session-item-title',
    title: sess.title || sess.lastMessage || sess.displayName || '新会话'
  }, sess.title || sess.lastMessage || sess.displayName || '新会话'));
  row.appendChild(el('span', { class: 'session-item-time' }, formatSessionAge(sess.createdAt)));
  const actions = el('div', { class: 'session-item-actions' });
  actions.appendChild(el('button', {
    class: 'session-item-menu-btn',
    title: '会话操作',
    'aria-label': '会话操作',
    onclick: function(e) {
      e.stopPropagation();
      showSessionMenu(e, sess);
    }
  }, el('span', { class: 'session-item-dots' })));
  row.appendChild(actions);
  item.appendChild(row);
  return item;
}

function showSessionMenu(event, sess) {
  closeSessionMenu();
  const menu = el('div', { class: 'session-context-menu' });
  menu.appendChild(el('button', { class: 'session-context-item', onclick: function() {
    closeSessionMenu();
    renameSession(sess);
  } }, '✎ 重命名'));
  menu.appendChild(el('button', { class: 'session-context-item danger', onclick: function() {
    closeSessionMenu();
    confirmDeleteSession(sess);
  } }, '🗑 删除'));
  document.body.appendChild(menu);
  const item = event.currentTarget;
  const rect = item.getBoundingClientRect();
  const width = menu.offsetWidth || 152;
  const left = Math.max(8, Math.min(window.innerWidth - width - 8, rect.right - width));
  const top = Math.min(window.innerHeight - menu.offsetHeight - 8, rect.bottom + 6);
  menu.style.left = left + 'px';
  menu.style.top = top + 'px';
  setTimeout(function() {
    document.addEventListener('click', closeSessionMenu, { once: true });
  }, 0);
}

function closeSessionMenu() {
  const menu = $('.session-context-menu');
  if (menu) menu.remove();
}

function renameSession(sess) {
  const existing = $('.session-rename-modal');
  if (existing) existing.remove();
  const overlay = el('div', { class: 'modal-overlay session-rename-modal', onclick: function(e) { if (e.target === overlay) overlay.remove(); } });
  const modal = el('div', { class: 'modal', style: 'width:380px' });
  const header = el('div', { class: 'modal-header' });
  header.appendChild(el('div', { class: 'modal-title' }, '重命名对话'));
  header.appendChild(el('button', { class: 'modal-close', onclick: function() { overlay.remove(); } }, '×'));
  modal.appendChild(header);
  const body = el('div', { class: 'modal-body' });
  const input = el('input', {
    class: 'form-input',
    value: sess.title || sess.displayName || '',
    placeholder: '输入对话名称',
    autocomplete: 'off'
  });
  body.appendChild(input);
  modal.appendChild(body);
  const footer = el('div', { class: 'modal-footer' });
  footer.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { overlay.remove(); } }, '取消'));
  footer.appendChild(el('button', { class: 'btn btn-primary', onclick: function() {
    const value = input.value.trim();
    if (!value) { showToast('名称不能为空'); return; }
    sess.title = value;
    sess.displayName = value;
    saveSessionsToStorage();
    overlay.remove();
    render();
    showToast('对话已重命名');
  } }, '保存'));
  modal.appendChild(footer);
  overlay.appendChild(modal);
  document.body.appendChild(overlay);
  input.focus();
  input.select();
  input.onkeydown = function(e) {
    if (e.key === 'Enter') { e.preventDefault(); footer.querySelector('.btn-primary').click(); }
    if (e.key === 'Escape') overlay.remove();
  };
}

function confirmDeleteSession(sess) {
  const existing = $('.session-delete-modal');
  if (existing) existing.remove();
  const overlay = el('div', { class: 'modal-overlay session-delete-modal', onclick: function(e) { if (e.target === overlay) overlay.remove(); } });
  const modal = el('div', { class: 'modal', style: 'width:380px' });
  const header = el('div', { class: 'modal-header' });
  header.appendChild(el('div', { class: 'modal-title' }, '删除对话'));
  header.appendChild(el('button', { class: 'modal-close', onclick: function() { overlay.remove(); } }, '×'));
  modal.appendChild(header);
  const body = el('div', { class: 'modal-body' });
  const label = sess.title || sess.displayName || '该对话';
  body.appendChild(el('div', { style: 'font-size:14px;line-height:22px;' }, '确定删除「' + label + '」？仅从当前界面列表移除。'));
  modal.appendChild(body);
  const footer = el('div', { class: 'modal-footer' });
  footer.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { overlay.remove(); } }, '取消'));
  footer.appendChild(el('button', { class: 'btn btn-danger', onclick: function() {
    state.sessions = state.sessions.filter(function(item) { return item.agentId !== sess.agentId; });
    if (state.currentSessionId === sess.agentId) {
      state.currentSessionId = state.sessions[0] ? state.sessions[0].agentId : null;
    }
    saveSessionsToStorage();
    overlay.remove();
    render();
    showToast('对话已删除');
  } }, '删除'));
  modal.appendChild(footer);
  overlay.appendChild(modal);
  document.body.appendChild(overlay);
}

function confirmClearSessions() {
  if (state.sessions.length === 0) return;
  const existing = $('.session-clear-modal');
  if (existing) existing.remove();
  const overlay = el('div', { class: 'modal-overlay session-clear-modal', onclick: function(e) { if (e.target === overlay) overlay.remove(); } });
  const modal = el('div', { class: 'modal', style: 'width:380px' });
  const header = el('div', { class: 'modal-header' });
  header.appendChild(el('div', { class: 'modal-title' }, '清空对话'));
  header.appendChild(el('button', { class: 'modal-close', onclick: function() { overlay.remove(); } }, '×'));
  modal.appendChild(header);
  const body = el('div', { class: 'modal-body' });
  body.appendChild(el('div', { style: 'font-size:14px;line-height:22px;' }, '确定清空对话列表？所有对话仅从当前界面移除。'));
  modal.appendChild(body);
  const footer = el('div', { class: 'modal-footer' });
  footer.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { overlay.remove(); } }, '取消'));
  footer.appendChild(el('button', { class: 'btn btn-danger', onclick: function() {
    for (const sess of state.sessions) {
      if (!sess.sending && !sess.streaming) continue;
      stopWaitTimer(sess);
      sess.sending = false;
      sess.streaming = false;
      if (sess.abortController) sess.abortController.abort();
    }
    state.sessions = [];
    state.currentSessionId = null;
    saveSessionsToStorage();
    overlay.remove();
    render();
    showToast('对话已清空');
  } }, '清空'));
  modal.appendChild(footer);
  overlay.appendChild(modal);
  document.body.appendChild(overlay);
}

function toggleSidebar() {
  // P1: 移动端用 overlay 模式，不改变 grid
  if (window.innerWidth <= 768) {
    document.body.classList.toggle('sidebar-overlay-open');
    return;
  }
  state.sidebarCollapsed = !state.sidebarCollapsed;
  localStorage.setItem('sidebarCollapsed', JSON.stringify(state.sidebarCollapsed));
  
  // Animate: add phase class, then swap to collapsed/expanded after fade
  const sidebar = $('.sidebar');
  if (sidebar) {
    if (state.sidebarCollapsed) {
      // Collapsing: fade out wide content first
      sidebar.classList.add('collapsing');
      setTimeout(function() {
        sidebar.classList.remove('collapsing');
        sidebar.classList.add('collapsed');
      }, 150);
    } else {
      // Expanding: remove collapsed, fade in wide content
      sidebar.classList.remove('collapsed');
      sidebar.classList.add('expanding');
      setTimeout(function() {
        sidebar.classList.remove('expanding');
      }, 200);
    }
  } else {
    render();
  }
}

function showRenameWorkspaceForm(currentName) {
  const existing = $('.ws-rename-modal');
  if (existing) existing.remove();
  const overlay = el('div', { class: 'modal-overlay ws-rename-modal', onclick: function(e) { if (e.target === overlay) overlay.remove(); } });
  const modal = el('div', { class: 'modal', style: 'width:400px' });
  const header = el('div', { class: 'modal-header' });
  header.appendChild(el('div', { class: 'modal-title' }, '重命名工作区'));
  header.appendChild(el('button', { class: 'modal-close', onclick: function() { overlay.remove(); } }, '×'));
  modal.appendChild(header);
  const body = el('div', { class: 'modal-body' });
  const formGroup = el('div', { class: 'form-group' });
  formGroup.appendChild(el('label', { class: 'form-label' }, '工作区名称'));
  const input = el('input', { class: 'form-input', value: currentName, autocomplete: 'off' });
  formGroup.appendChild(input);
  body.appendChild(formGroup);
  const errorEl = el('div', { class: 'error-banner', style: 'display:none;margin-top:8px' });
  body.appendChild(errorEl);
  modal.appendChild(body);
  const footerEl = el('div', { class: 'modal-footer' });
  footerEl.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { overlay.remove(); } }, '取消'));
  const saveBtn = el('button', { class: 'btn btn-primary' }, '保存');
  footerEl.appendChild(saveBtn);
  modal.appendChild(footerEl);
  overlay.appendChild(modal);
  document.body.appendChild(overlay);
  input.focus();
  input.select();
  async function doRename() {
    const name = input.value.trim();
    if (!name || name === currentName) { overlay.remove(); return; }
    saveBtn.textContent = '保存中…';
    saveBtn.disabled = true;
    errorEl.style.display = 'none';
    try {
      await apiRequest(API + '/workspaces/' + encodeURIComponent(currentName), {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name })
      });
      await loadWorkspaces();
      var renamed = state.workspaces.find(function(w) { return wsEntryName(w) === name || wsEntryName(w) === name.replace(/[^\p{L}\p{N}._-]/gu, '_'); });
      if (renamed) {
        state.currentWorkspace = wsEntryPath(renamed);
        localStorage.setItem('currentWorkspace', state.currentWorkspace);
        state.sessions.forEach(function(s) { if ((s.workspace || '') === currentName) s.workspace = state.currentWorkspace; });
        saveSessionsToStorage();
      }
      overlay.remove();
      render();
      showToast('工作区已重命名');
    } catch (e) {
      errorEl.textContent = '重命名失败: ' + e.message;
      errorEl.style.display = 'flex';
      saveBtn.textContent = '保存';
      saveBtn.disabled = false;
    }
  }
  saveBtn.onclick = doRename;
  input.onkeydown = function(e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); doRename(); }
    if (e.key === 'Escape') overlay.remove();
  };
}

function confirmDeleteWorkspace(name) {
  const existing = $('.ws-delete-modal');
  if (existing) existing.remove();
  const overlay = el('div', { class: 'modal-overlay ws-delete-modal', onclick: function(e) { if (e.target === overlay) overlay.remove(); } });
  const modal = el('div', { class: 'modal', style: 'width:380px' });
  const header = el('div', { class: 'modal-header' });
  header.appendChild(el('div', { class: 'modal-title' }, '删除工作区'));
  header.appendChild(el('button', { class: 'modal-close', onclick: function() { overlay.remove(); } }, '×'));
  modal.appendChild(header);
  const body = el('div', { class: 'modal-body' });
  body.appendChild(el('div', { style: 'font-size:14px;line-height:22px;' }, '确定删除工作区「' + name + '」？该工作区目录及其下文件将被删除，且不可恢复。'));
  modal.appendChild(body);
  const footerEl = el('div', { class: 'modal-footer' });
  footerEl.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { overlay.remove(); } }, '取消'));
  const delBtn = el('button', { class: 'btn btn-danger' }, '删除');
  footerEl.appendChild(delBtn);
  modal.appendChild(footerEl);
  overlay.appendChild(modal);
  document.body.appendChild(overlay);
  delBtn.onclick = async function() {
    delBtn.textContent = '删除中…';
    delBtn.disabled = true;
    try {
      await apiRequest(API + '/workspaces/' + encodeURIComponent(name), { method: 'DELETE' });
      await loadWorkspaces();
      state.sessions = state.sessions.filter(function(s) { return (s.workspace || '') !== name; });
      saveSessionsToStorage();
      if (state.currentWorkspace && state.currentWorkspace.indexOf(name) !== -1) {
        state.currentWorkspace = '';
        localStorage.removeItem('currentWorkspace');
      }
      overlay.remove();
      render();
      showToast('工作区已删除');
    } catch (e) {
      delBtn.textContent = '删除';
      delBtn.disabled = false;
      alert('删除失败: ' + e.message);
    }
  };
}

async function reorderWorkspaces(srcName, targetName) {
  var names = state.workspaces.map(function(w) { return wsEntryName(w); });
  var from = names.indexOf(srcName);
  var to = names.indexOf(targetName);
  if (from === -1 || to === -1 || from === to) return;
  names.splice(to, 0, names.splice(from, 1)[0]);
  var optimistic = names.map(function(n) {
    var w = state.workspaces.find(function(x) { return wsEntryName(x) === n; }) || {};
    return { name: n, path: wsEntryPath(w) };
  });
  state.workspaces = optimistic;
  render();
  try {
    await apiRequest(API + '/workspaces/order', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ order: names })
    });
    await loadWorkspaces();
    render();
  } catch (e) { /* ignore — order kept locally */ }
}

// ── 主区域 ─────────────────────────────────
function renderMain() {
  const main = el('main', { class: 'main' });

  const sess = getCurrentSession();

  const header = el('div', { class: 'main-header' });
  const titleWrap = el('div', { class: 'main-header-meta' });
  titleWrap.appendChild(el('div', { class: 'main-header-title' }, sess ? (sess.title || sess.displayName) : '新对话'));
  const headerSub = el('div', { class: 'main-header-sub' });
  if (sess && sess.messages.length > 0) {
    headerSub.appendChild(el('span', { class: 'main-header-model' }, state.currentChannel || '当前激活渠道'));
  }
  if (sess && sess.currentStage) {
    headerSub.appendChild(el('span', { class: 'process-stage', id: 'process-stage' },
      el('span', { class: 'process-stage-dot' }),
      sess.currentStage
    ));
  }
  titleWrap.appendChild(headerSub);
  header.appendChild(titleWrap);

  const tabs = el('div', { class: 'main-header-tabs' });
  tabs.appendChild(el('button', {
    class: 'main-tab' + (state.activeMainTab === 'chat' ? ' active' : ''),
    onclick: function() { state.activeMainTab = 'chat'; render(); }
  }, '对话'));
  tabs.appendChild(el('button', {
    class: 'main-tab' + (state.activeMainTab === 'trajectory' ? ' active' : ''),
    onclick: function() { state.activeMainTab = 'trajectory'; render(); }
  }, '轨迹'));
  header.appendChild(tabs);

  const headerRight = el('div', { class: 'main-header-right' });

  if (sess && sess.messages.length > 0) {
    headerRight.appendChild(el('button', { class: 'main-header-session', onclick: () => exportSession(sess), title: '导出当前对话' }, 'Session log ⬇'));
  }

  header.appendChild(headerRight);
  main.appendChild(header);

  // Messages or Welcome
  if (state.activeMainTab === 'trajectory' && sess) {
    main.appendChild(renderTrajectoryView(sess));
  } else if (!sess || sess.messages.length === 0) {
    main.appendChild(renderWelcome(sess));
  } else {
    const messages = el('div', { class: 'messages' });
    const inner = el('div', { class: 'messages-inner' });

    // v11: 加载更多按钮
    if (sess.messages.length > 50) {
      const loadMore = el('button', { class: 'load-earlier', onclick: () => loadEarlierMessages(sess) }, '加载更多消息');
      inner.appendChild(loadMore);
    }

    for (const m of sess.messages) {
      inner.appendChild(renderMessage(m));
    }

    // v11: 队列区域
    if (sess.queue && sess.queue.length > 0) {
      inner.appendChild(renderQueueDock(sess));
    }

    // v11: 错误卡片
    if (sess.error) {
      inner.appendChild(renderErrorCard(sess));
    }

    messages.appendChild(inner);
    
    // Scroll-to-bottom button
    const scrollBtn = el('button', { class: 'scroll-bottom-btn', onclick: scrollToBottom, title: '回到底部' }, '↓');
    messages.appendChild(scrollBtn);
    
    // Scroll detection for scroll-to-bottom button visibility
    messages.addEventListener('scroll', function() {
      const isNearBottom = messages.scrollHeight - messages.scrollTop - messages.clientHeight < 100;
      if (isNearBottom) {
        scrollBtn.classList.remove('visible');
      } else {
        scrollBtn.classList.add('visible');
      }
    });
    
    main.appendChild(messages);
    setTimeout(scrollToBottom, 0);
  }

  // Composer
  if (state.pendingApprovals.length > 0) {
    main.appendChild(renderApprovalBanner());
  }
  main.appendChild(renderComposer(sess));

  return main;
}

// ── Welcome ────────────────────────────────
function renderWelcome(sess) {
  const welcome = el('div', { class: 'welcome' });
  if (sess && sess.messages.length === 0 && state.sessions.length > 0) {
    // 空会话引导
    welcome.appendChild(el('div', { class: 'welcome-icon' }, '💬'));
    welcome.appendChild(el('div', { class: 'welcome-title' }, '开始新对话'));
    welcome.appendChild(el('div', { class: 'welcome-subtitle' }, '在下方输入框中输入消息，按 Enter 发送，Shift+Enter 换行。'));
  } else {
    welcome.appendChild(el('div', { class: 'welcome-icon' }, '👋'));
    welcome.appendChild(el('div', { class: 'welcome-title' }, 'DeepSeek Harness · Java Edition'));
    welcome.appendChild(el('div', { class: 'welcome-subtitle' }, '保留核心对话能力，减少侧栏和工具区干扰。'));
  }
  const hints = el('div', { class: 'welcome-hints' });
  const items = ['Markdown 渲染', '图片附件', '工具调用', '代码复制'];
  for (const item of items) {
    hints.appendChild(el('div', { class: 'welcome-hint' }, item));
  }
  welcome.appendChild(hints);

  return welcome;
}

function renderTrajectoryView(sess) {
  const container = el('div', { class: 'messages trajectory-view' });
  const inner = el('div', { class: 'messages-inner trajectory-view-inner' });
  renderTrajectoryPanel(inner, sess);
  container.appendChild(inner);
  return container;
}

// ── 消息渲染 ───────────────────────────────
function renderMessage(m) {
  const msgEl = el('div', { class: 'msg ' + m.role });

  // v11: 工具调用消息
  if (m.role === 'tool') {
    return renderToolCall(m);
  }

  // v11: 系统消息（审批请求等）
  if (m.role === 'system') {
    return renderSystemMessage(m);
  }

  const bubble = el('div', { class: 'msg-bubble' });

  // Copy button (assistant only) — streaming 期间也显示，方便随时复制当前内容
  if (m.role === 'assistant' && m.content) {
    const copyBtn = el('button', {
      class: 'msg-copy-btn',
      onclick: function(e) {
        copyToClipboard(m.content);
        e.target.classList.add('copied');
        e.target.textContent = '✓';
        setTimeout(function() { e.target.classList.remove('copied'); e.target.textContent = '⎘'; }, 1500);
      },
      title: '复制'
    }, '⎘');
    bubble.appendChild(copyBtn);
  }

  // v11: 编辑按钮 (user messages, not streaming)
  if (m.role === 'user' && !m.streaming && !m.editing) {
    const editBtn = el('button', {
      class: 'msg-edit-btn',
      onclick: function() { startEditMessage(m); },
      title: '编辑'
    }, '✎');
    bubble.appendChild(editBtn);
  }

  // v11: 编辑模式
  if (m.editing) {
    return renderEditMessage(m);
  }

  const contentEl = el('div', { class: 'msg-content' });
  if (m.streaming && !m.content && !m.reasoning) {
    // 简洁等待指示器
    const waitContainer = el('div', { class: 'waiting-indicator' });
    const dotsEl = el('span', { class: 'waiting-dots' });
    for (let i = 0; i < 3; i++) {
      dotsEl.appendChild(el('span', { class: 'waiting-dot', style: `animation-delay:${(i * 0.16).toFixed(2)}s` }));
    }
    waitContainer.appendChild(dotsEl);
    const sess = getCurrentSession();
    const elapsed = sess && sess.waitStartTime ? formatElapsedDuration((Date.now() - sess.waitStartTime) / 1000) : '0.0s';
    waitContainer.appendChild(el('span', { class: 'waiting-text', id: 'wait-text' }, '正在思考… ' + elapsed));
    contentEl.appendChild(waitContainer);
  } else if (m.streaming) {
    startStreamReveal(m);
    contentEl.innerHTML = renderAssistantContent(visibleAssistantMessage(m, m._revealLength), true);
    contentEl.appendChild(el('span', { class: 'streaming-cursor' }, '▋'));
  } else {
    contentEl.innerHTML = renderAssistantContent(m, false);
  }
  bubble.appendChild(contentEl);

  // v11: 附件预览
  if (m.attachments && m.attachments.length > 0) {
    const attEl = el('div', { class: 'attachment-area' });
    for (const att of m.attachments) {
      const item = el('div', { class: 'attachment-item' });
      if (att.type === 'image') {
        item.appendChild(el('img', { src: att.url, class: 'attachment-preview', alt: att.name }));
      } else {
        item.appendChild(el('span', { class: 'attachment-icon' }, '📄'));
      }
      item.appendChild(el('span', { class: 'attachment-name' }, att.name));
      bubble.appendChild(attEl);
      attEl.appendChild(item);
    }
  }

  // v11: 引用
  if (m.references && m.references.length > 0) {
    const refEl = el('div', { class: 'reference-list' });
    for (const ref of m.references) {
      const refCard = el('div', { class: 'reference-card' });
      refCard.appendChild(el('span', { class: 'reference-icon' }, '🔗'));
      refCard.appendChild(el('span', { class: 'reference-text' }, ref.text || ref.url || ''));
      if (ref.url) {
        refCard.appendChild(el('a', { href: ref.url, target: '_blank', class: 'reference-link' }, '打开'));
      }
      refEl.appendChild(refCard);
    }
    bubble.appendChild(refEl);
  }

  // Meta
  if (m.meta && !m.streaming) {
    const metaEl = el('div', { class: 'msg-meta' });
    if (m.meta.time) metaEl.appendChild(el('span', { class: 'msg-meta-item' }, '⏱ ' + formatTimestamp(m.meta.time)));
    if (m.meta.duration) metaEl.appendChild(el('span', { class: 'msg-meta-item' }, '⏳ ' + m.meta.duration));
    if (m.meta.firstToken && m.meta.firstToken !== '-') metaEl.appendChild(el('span', { class: 'msg-meta-item' }, '⚡ ' + m.meta.firstToken));
    if (m.meta.tokens) metaEl.appendChild(el('span', { class: 'msg-meta-item' }, '📝 ' + m.meta.tokens));
    bubble.appendChild(metaEl);
  }

  msgEl.appendChild(bubble);
  return msgEl;
}

// ═══════════════════════════════════════════
// P0: 工具调用可视化
// ═══════════════════════════════════════════
function renderToolCall(m) {
  const args = normalizeToolArgs(m.args || m.argsRaw);
  const model = toolRowModel(m.toolName || 'unknown', args, m.result, m.status || 'running');
  const card = el('div', { class: 'tool-call', 'data-tool': m.toolName || 'unknown', 'data-state': model.state });
  if (m.expanded) card.classList.add('expanded');
  const header = el('div', {
    class: 'tool-call-header',
    role: 'button', tabindex: '0',
    onclick: function() { m.expanded = !m.expanded; card.classList.toggle('expanded'); },
    onkeydown: function(e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); header.click(); } }
  });
  header.appendChild(el('span', { class: 'tool-call-icon' }, model.icon));
  header.appendChild(el('span', { class: 'tool-call-name' }, model.title));
  header.appendChild(el('span', { class: 'tool-call-dot', 'aria-hidden': 'true' }));
  header.appendChild(el('span', { class: 'tool-call-summary' }, model.summary));
  header.appendChild(el('span', { class: 'tool-call-chevron' }, '▸'));
  card.appendChild(header);

  const body = el('div', { class: 'tool-call-body' });
  if (model.body !== null) {
    const inputSection = el('div', { class: 'tool-call-io' });
    inputSection.appendChild(el('div', { class: 'tool-call-section-label' }, 'IN'));
    inputSection.appendChild(el('pre', { class: 'tool-call-json' }, model.body));
    body.appendChild(inputSection);
  }
  if (model.output !== null) {
    const outputSection = el('div', { class: 'tool-call-io' });
    outputSection.appendChild(el('div', { class: 'tool-call-section-label' }, 'OUT'));
    outputSection.appendChild(el('pre', { class: 'tool-call-json tool-call-output' }, model.output));
    body.appendChild(outputSection);
  }
  if (m.duration) {
    body.appendChild(el('div', { class: 'tool-call-duration' }, '耗时 ' + m.duration));
  }
  card.appendChild(body);
  return card;
}

function normalizeToolArgs(args) {
  if (typeof args !== 'string' || !args) return args || {};
  try { return JSON.parse(args); } catch (e) { return args; }
}

function toolRowModel(toolName, args, result, status) {
  const lower = String(toolName).toLowerCase();
  const variant = ['bash', 'pwsh'].some(function(name) { return lower.includes(name); }) ? 'bash'
    : ['read', 'cat', 'open'].some(function(name) { return lower.includes(name); }) ? 'read'
    : ['search', 'grep', 'glob', 'web_search'].some(function(name) { return lower.includes(name); }) ? 'search'
    : ['write', 'edit'].some(function(name) { return lower.includes(name); }) ? 'edit'
    : ['run_code', 'execute'].some(function(name) { return lower.includes(name); }) ? 'code' : 'others';
  const titles = { bash: 'Bash', read: 'Read', search: 'Search', edit: 'Edit', code: 'Code', others: 'Tool call' };
  const summaryKeys = {
    bash: ['description', 'command'],
    read: ['path', 'file_path', 'url'],
    search: ['query', 'pattern', 'url'],
    edit: ['path', 'file_path'],
    code: ['description'],
    others: []
  };
  const argsObject = (args && typeof args === 'object') ? args : {};
  let summary = '';
  for (const key of summaryKeys[variant] || []) {
    if (typeof argsObject[key] === 'string' && argsObject[key]) { summary = firstTextLine(argsObject[key]); break; }
  }
  if (!summary) {
    summary = Object.values(argsObject).find(function(value) { return typeof value === 'string' && value; }) || '';
  }
  if (!summary) summary = (args && typeof args === 'object') ? JSON.stringify(args) : String(args || toolName);
  const output = result == null ? null : (typeof result === 'string' ? result : JSON.stringify(result, null, 2));
  const state = status === 'error' ? 'error' : status === 'success' ? 'ok' : 'running';
  return {
    title: titles[variant] || 'Tool call',
    icon: state === 'error' ? '⚠' : state === 'ok' ? '✓' : '⏳',
    summary: state === 'error' && output ? firstTextLine(output) : firstTextLine(summary),
    body: args == null ? null : ((args && typeof args === 'object') ? JSON.stringify(args, null, 2) : String(args)),
    output: output,
    state: state
  };
}

function firstTextLine(text) {
  const value = String(text || '');
  const newline = value.indexOf('\n');
  return newline === -1 ? value : value.slice(0, newline);
}

function formatElapsedDuration(seconds) {
  const value = Number(seconds) || 0;
  if (value < 60) return value.toFixed(1) + 's';
  const totalSeconds = Math.floor(value);
  const minutes = Math.floor(totalSeconds / 60);
  const remainingSeconds = totalSeconds % 60;
  return minutes + '分' + String(remainingSeconds).padStart(2, '0') + '秒';
}

function latestTextLine(text) {
  const value = String(text || '').trimEnd();
  const newline = value.lastIndexOf('\n');
  return newline === -1 ? value : value.slice(newline + 1);
}

// ═══════════════════════════════════════════
// P1: 错误卡片
// ═══════════════════════════════════════════
function renderErrorCard(sess) {
  const card = el('div', { class: 'error-card' });
  card.appendChild(el('div', { class: 'error-card-icon' }, '⚠'));
  const info = el('div', { class: 'error-card-info' });
  info.appendChild(el('div', { class: 'error-card-title' }, '请求失败'));
  info.appendChild(el('div', { class: 'error-card-msg' }, sess.error || '未知错误'));
  card.appendChild(info);
  card.appendChild(el('button', {
    class: 'btn btn-secondary error-card-retry',
    onclick: function() {
      sess.error = null;
      const lastUserMsg = [...sess.messages].reverse().find(m => m.role === 'user');
      if (lastUserMsg) {
        const idx = sess.messages.indexOf(lastUserMsg);
        sess.messages = sess.messages.slice(0, idx + 1);
        sendMessage(lastUserMsg.content);
      } else {
        render();
      }
    }
  }, '重试'));
  return card;
}

// ═══════════════════════════════════════════
// P0: 系统消息渲染
// ═══════════════════════════════════════════
function renderSystemMessage(m) {
  const card = el('div', { class: 'sys-msg' });
  card.appendChild(el('span', { class: 'sys-msg-icon' }, m.icon || 'ℹ'));
  card.appendChild(el('span', { class: 'sys-msg-text' }, m.content));
  return card;
}

// ═══════════════════════════════════════════
// P0: 消息编辑
// ═══════════════════════════════════════════
function startEditMessage(m) {
  m.editing = true;
  render();
}

function renderEditMessage(m) {
  const msgEl = el('div', { class: 'msg user' });
  const bubble = el('div', { class: 'msg-bubble' });
  const textarea = el('textarea', { class: 'msg-edit-textarea', rows: '3' }, m.content);
  bubble.appendChild(textarea);
  const actions = el('div', { class: 'msg-edit-actions' });
  actions.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { m.editing = false; render(); } }, '取消'));
  actions.appendChild(el('button', { class: 'btn btn-primary', onclick: function() {
    const newText = textarea.value.trim();
    if (!newText || newText === m.content) { m.editing = false; render(); return; }
    m.editing = false;
    m.content = newText;
    // 重新发送
    const sess = getCurrentSession();
    if (!sess) return;
    // 删除此消息之后的所有消息
    const idx = sess.messages.indexOf(m);
    sess.messages = sess.messages.slice(0, idx + 1);
    // 移除这条用户消息，重新发送
    sess.messages.pop();
    sendMessage(newText);
  } }, '重新发送'));
  bubble.appendChild(actions);
  msgEl.appendChild(bubble);
  return msgEl;
}

// ═══════════════════════════════════════════
// P0: 斜杠命令
// ═══════════════════════════════════════════
function handleSlashCommand(input, cursorPos) {
  const text = input.value;
  // 检查是否在输入斜杠命令
  const beforeCursor = text.slice(0, cursorPos);
  const slashMatch = beforeCursor.match(/\/(\w*)$/);
  if (!slashMatch) {
    hideSlashSuggestions();
    return;
  }
  const partial = slashMatch[1].toLowerCase();
  const matches = state.slashCommands.filter(cmd => cmd.name.includes('/' + partial));
  if (matches.length === 0) {
    hideSlashSuggestions();
    return;
  }
  showSlashSuggestions(input, matches);
}

function showSlashSuggestions(input, matches) {
  hideSlashSuggestions();
  const container = el('div', { class: 'slash-command-suggestions' });
  for (const cmd of matches.slice(0, 8)) {
    const item = el('button', {
      class: 'slash-cmd-item',
      onclick: function() {
        input.value = cmd.name + ' ';
        input.focus();
        autoResize({ target: input });
        hideSlashSuggestions();
      }
    });
    item.appendChild(el('span', { class: 'slash-cmd-icon' }, cmd.icon));
    item.appendChild(el('div', { class: 'slash-cmd-info' },
      el('div', { class: 'slash-cmd-name' }, cmd.name),
      el('div', { class: 'slash-cmd-desc' }, cmd.desc)
    ));
    container.appendChild(item);
  }
  // P2: 用 absolute 定位挂载到 composer 内部，避免 fixed 在滚动/transform 上下文中偏移
  const composerEl = input.closest('.composer');
  if (composerEl) {
    container.style.position = 'absolute';
    container.style.bottom = '100%';
    container.style.left = '0';
    container.style.width = '100%';
    container.id = 'slash-suggestions';
    composerEl.style.position = 'relative';
    composerEl.appendChild(container);
  } else {
    // fallback: fixed 定位
    const rect = input.getBoundingClientRect();
    container.style.position = 'fixed';
    container.style.bottom = (window.innerHeight - rect.top + 4) + 'px';
    container.style.left = rect.left + 'px';
    container.style.width = rect.width + 'px';
    container.id = 'slash-suggestions';
    document.body.appendChild(container);
  }
}

function hideSlashSuggestions() {
  const existing = $('#slash-suggestions');
  if (existing) existing.remove();
}

// ═══════════════════════════════════════════
// @ 插件提及
// ═══════════════════════════════════════════
state.mentionState = { items: [], activeIndex: -1 };

function activeMentionPlugins() {
  return state.plugins.filter(function(plugin) {
    return !plugin.status || plugin.status === 'ACTIVE';
  });
}

function handlePluginMention(input, cursorPos) {
  const text = input.value.slice(0, cursorPos);
  const match = text.match(/(?:^|\s)@([A-Za-z0-9._-]*)$/);
  if (!match) {
    hidePluginMentionSuggestions();
    return;
  }

  const partial = match[1].toLowerCase();
  const items = activeMentionPlugins().filter(function(plugin) {
    const id = (plugin.pluginId || '').toLowerCase();
    const name = (plugin.displayName || '').toLowerCase();
    return id.includes(partial) || name.includes(partial);
  }).slice(0, 8);

  if (items.length === 0) {
    hidePluginMentionSuggestions();
    return;
  }
  showPluginMentionSuggestions(input, items);
}

function showPluginMentionSuggestions(input, items) {
  hidePluginMentionSuggestions();
  state.mentionState.items = items;
  state.mentionState.activeIndex = 0;

  const container = el('div', { class: 'slash-command-suggestions', id: 'plugin-mention-suggestions' });
  items.forEach(function(plugin, index) {
    const item = el('button', {
      type: 'button',
      class: 'slash-cmd-item' + (index === 0 ? ' active' : ''),
      onclick: function() {
        applyPluginMention(input, plugin.pluginId);
      }
    });
    item.appendChild(el('span', { class: 'slash-cmd-icon' }, '🧩'));
    item.appendChild(el('div', { class: 'slash-cmd-info' },
      el('div', { class: 'slash-cmd-name' }, '@' + plugin.pluginId),
      el('div', { class: 'slash-cmd-desc' }, plugin.displayName || plugin.pluginId)
    ));
    container.appendChild(item);
  });

  const rect = input.getBoundingClientRect();
  container.style.position = 'fixed';
  container.style.bottom = (window.innerHeight - rect.top + 4) + 'px';
  container.style.left = rect.left + 'px';
  container.style.width = Math.max(rect.width, 260) + 'px';
  document.body.appendChild(container);
}

function hidePluginMentionSuggestions() {
  const existing = $('#plugin-mention-suggestions');
  if (existing) existing.remove();
  state.mentionState.items = [];
  state.mentionState.activeIndex = -1;
}

function handleMentionKeys(input, event) {
  const container = $('#plugin-mention-suggestions');
  if (!container || state.mentionState.items.length === 0) return false;

  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    const count = state.mentionState.items.length;
    state.mentionState.activeIndex = event.key === 'ArrowDown'
      ? (state.mentionState.activeIndex + 1) % count
      : (state.mentionState.activeIndex - 1 + count) % count;
    Array.from(container.children).forEach(function(item, index) {
      item.classList.toggle('active', index === state.mentionState.activeIndex);
    });
    return true;
  }
  if ((event.key === 'Enter' || event.key === 'Tab') && state.mentionState.activeIndex >= 0) {
    event.preventDefault();
    applyPluginMention(input, state.mentionState.items[state.mentionState.activeIndex].pluginId);
    return true;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    hidePluginMentionSuggestions();
    return true;
  }
  return false;
}

function hasActivePluginMention() {
  return !!$('#plugin-mention-suggestions');
}

function applyPluginMention(input, pluginId) {
  const cursorPos = input.selectionStart || input.value.length;
  const before = input.value.slice(0, cursorPos);
  const match = before.match(/@([A-Za-z0-9._-]*)$/);
  if (!match) {
    hidePluginMentionSuggestions();
    return;
  }
  const start = cursorPos - match[0].length;
  const insertion = '@' + pluginId + ' ';
  input.value = input.value.slice(0, start) + insertion + input.value.slice(cursorPos);
  const caret = start + insertion.length;
  input.focus();
  input.setSelectionRange(caret, caret);
  autoResize({ target: input });
  hidePluginMentionSuggestions();
}

async function executeSlashCommand(text, sess) {
  const parts = text.split(/\s+/);
  const cmd = parts[0].toLowerCase();
  const args = parts.slice(1).join(' ');

  switch (cmd) {
    case '/help':
      sess.messages.push({ role: 'system', content: '可用命令: ' + state.slashCommands.map(c => c.name).join(', '), icon: '❓' });
      render();
      return true;

    case '/clear':
      sess.messages = [];
      sess.stats = { turns: 0, steps: 0, llmTimeMs: 0, firstTokenMs: 0, inputTokens: 0, outputTokens: 0, cacheHitRate: 0 };
      render();
      return true;

    case '/export':
      exportSession(sess);
      return true;

    case '/compact':
      sess.compactionInfo = { status: 'compacting', timestamp: new Date().toISOString() };
      const compactNotice = { role: 'system', content: '正在压缩上下文…', icon: '📦' };
      sess.messages.push(compactNotice);
      render();
      const recent = sess.messages.slice(-20);
      const saved = Math.max(sess.messages.length - recent.length, 0);
      sess.messages = recent;
      compactNotice.content = '保留最近 20 条，节省 ' + saved + ' 条消息';
      compactNotice.icon = '✅';
      sess.compactionInfo = { status: 'done', saved: saved, timestamp: new Date().toISOString() };
      render();
      return true;

    case '/branch':
      branchConversation(null);
      return true;

    case '/channel':
      if (args) {
        const matchedChannel = (state.availableModels || []).find(function(model) {
          return model.channelCode === args || model.displayName === args;
        });
        state.currentChannel = matchedChannel ? matchedChannel.channelCode : args;
        localStorage.setItem('currentChannel', state.currentChannel);
        showToast('模型渠道已切换: ' + (matchedChannel ? matchedChannel.displayName : args));
        render();
      } else {
        const currentChannel = (state.availableModels || []).find(function(model) {
          return model.channelCode === state.currentChannel;
        });
        showToast('当前模型渠道: ' + (currentChannel ? currentChannel.displayName : (state.currentChannel || '未选择')) + '。用法: /channel <渠道名称>');
      }
      return true;

    case '/workspace':
      if (args) {
        state.currentWorkspace = args;
        localStorage.setItem('currentWorkspace', args);
        render();
      } else {
        showToast('当前工作区: ' + (state.currentWorkspace || '默认') + '。用法: /workspace <名称>');
      }
      return true;

    case '/todo':
      state.activePanel = 'todos';
      render();
      return true;

    case '/plan':
      state.activePanel = 'plan';
      render();
      return true;

    case '/goal':
      state.activePanel = 'goal';
      render();
      return true;

    case '/skills':
      showSkillsModal();
      return true;

    case '/agents':
      state.activePanel = 'subagents';
      render();
      return true;

    case '/jobs':
      state.activePanel = 'jobs';
      render();
      return true;

    case '/feedback':
      showFeedbackModal(sess);
      return true;

    default:
      return false;
  }
}

// ═══════════════════════════════════════════
// P1: 图片附件
// ═══════════════════════════════════════════
function handleFilePaste(e, sess) {
  const items = e.clipboardData?.items;
  if (!items) return;
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault();
      const file = item.getAsFile();
      if (file) addAttachment(sess, file);
    }
  }
}

function handleFileDrop(e, sess) {
  e.preventDefault();
  const files = e.dataTransfer?.files;
  if (!files) return;
  for (const file of files) {
    if (file.type.startsWith('image/')) {
      addAttachment(sess, file);
    }
  }
}

function addAttachment(sess, file) {
  if (!sess) return;
  if (!sess.pendingAttachments) sess.pendingAttachments = [];
  const maxSize = 10 * 1024 * 1024; // 10MB
  if (file.size > maxSize) {
    showToast('图片不能超过 10MB');
    return;
  }
  const reader = new FileReader();
  reader.onload = function() {
    sess.pendingAttachments.push({
      name: file.name,
      type: 'image',
      url: reader.result,
      size: file.size,
    });
    render();
  };
  reader.readAsDataURL(file);
}

function removeAttachment(sess, index) {
  if (sess.pendingAttachments) {
    sess.pendingAttachments.splice(index, 1);
    render();
  }
}

// ═══════════════════════════════════════════
// P1: 审批
// ═══════════════════════════════════════════
function renderApprovalBanner() {
  const banner = el('div', { class: 'approval-banner' });
  const approval = state.pendingApprovals[0];
  const command = approval.displayCommand
    || (approval.args && (approval.args.command || approval.args.path || approval.args.directory))
    || approval.toolName;
  banner.appendChild(el('div', { class: 'approval-info' },
    el('span', { class: 'approval-icon' }, '⚠️'),
    el('span', null,
      el('strong', null, '运行审批'),
      el('div', { class: 'approval-command' }, approval.toolName + ' · ' + command)
    )
  ));
  const actions = el('div', { class: 'approval-actions' });
  actions.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { resolveRuntimeApproval(approval, 'DENY'); } }, '拒绝'));
  actions.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { resolveRuntimeApproval(approval, 'ALLOW_ONCE'); } }, '允许一次'));
  actions.appendChild(el('button', { class: 'btn btn-primary', onclick: function() { resolveRuntimeApproval(approval, 'ALLOW_SESSION'); } }, '本会话允许'));
  banner.appendChild(el('div', { class: 'approval-card' },
    el('div', { class: 'approval-head' },
      el('span', { class: 'approval-icon' }, '⚠️'),
      el('div', { class: 'approval-copy' },
        el('div', { class: 'approval-title-row' },
          el('strong', null, '运行审批'),
          el('span', { class: 'approval-tool' }, approval.toolName)
        ),
        el('div', { class: 'approval-command', title: command }, command)
      )
    ),
    actions
  ));
  return banner;
}

async function resolveRuntimeApproval(approval, verdict) {
  try {
    await apiRequest(APPROVAL_API + '/runtime/' + approval.approvalId + '/resolve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ verdict })
    });
    showToast(verdict === 'DENY' ? '已拒绝本次执行' : '已允许执行');
  } catch (e) {
    showToast('审批失败：' + e.message);
  }
  state.pendingApprovals = state.pendingApprovals.filter(a => a !== approval);
  render();
}

function startApprovalPolling(sess) {
  stopApprovalPolling(sess);
  sess.approvalPollTimer = setInterval(async function() {
    if (!sess.sending) {
      stopApprovalPolling(sess);
      return;
    }
    const before = state.pendingApprovals.length;
    await loadPendingApprovals();
    if (before !== state.pendingApprovals.length && state.currentSessionId === sess.agentId) {
      render();
    }
  }, 700);
}

function stopApprovalPolling(sess) {
  if (sess && sess.approvalPollTimer) {
    clearInterval(sess.approvalPollTimer);
    sess.approvalPollTimer = null;
  }
}

// ═══════════════════════════════════════════
// P1: 历史消息加载
// ═══════════════════════════════════════════
async function loadHistoryMessages(sess) {
  try {
    const data = await apiRequest(
      CONSOLE_API + '/sessions/' + encodeURIComponent(sess.sessionId) + '/messages'
    );
      if (Array.isArray(data) && data.length > 0) {
        sess.messages = data.map(function(m) {
          return {
            role: m.role,
            content: m.content || '',
            blocks: m.blocks,
            time: formatTime(m.occurredAt || new Date().toISOString()),
            streaming: false,
            meta: m.role === 'assistant' ? {
              time: m.occurredAt || new Date().toISOString(),
              duration: '-',
              firstToken: '-',
              tokens: estimateTokens(m.content || ''),
            } : null,
          };
        });
        saveSessionsToStorage();
        if (state.currentSessionId === sess.agentId) render();
        setTimeout(scrollToBottom, 50);
      }
  } catch (e) { /* ignore */ }
}

async function loadEarlierMessages(sess) {
  try {
    if (!sess.sessionId) {
      showToast('没有更多历史消息');
      return;
    }
    const data = await apiRequest(
      CONSOLE_API + '/sessions/' + encodeURIComponent(sess.sessionId) + '/messages'
    );
      if (Array.isArray(data) && data.length > 0) {
        sess.messages = [...data, ...sess.messages];
        render();
      } else {
        showToast('没有更多历史消息');
      }
  } catch (e) {
    showToast('加载历史失败');
  }
}

// ═══════════════════════════════════════════
// P2: 消息排队 + 插话
// ═══════════════════════════════════════════
function renderQueueDock(sess) {
  const dock = el('div', { class: 'queue-dock' });
  dock.appendChild(el('div', { class: 'queue-dock-header' },
    el('span', null, '排队消息 (' + sess.queue.length + ')'),
    el('button', { class: 'btn btn-secondary', style: 'font-size:10px;padding:2px 8px', onclick: function() { sess.queue = []; render(); } }, '清空')
  ));
  for (let i = 0; i < sess.queue.length; i++) {
    const qItem = sess.queue[i];
    const item = el('div', { class: 'queue-item' });
    item.appendChild(el('span', { class: 'queue-item-text' }, qItem.slice(0, 60)));
    const itemActions = el('div', { class: 'queue-item-actions' });
    // Steer: 发送所有排队消息
    itemActions.appendChild(el('button', { class: 'steer-btn', onclick: function() { steerQueue(sess); }, title: '插话发送全部排队消息' }, '↑'));
    // Remove
    itemActions.appendChild(el('button', { class: 'queue-item-remove', onclick: function() { sess.queue.splice(i, 1); render(); }, title: '移除' }, '×'));
    item.appendChild(itemActions);
    dock.appendChild(item);
  }
  return dock;
}

function steerQueue(sess) {
  if (!sess.queue || sess.queue.length === 0) return;
  const allMessages = sess.queue.join('\n\n');
  sess.queue = [];
  sendMessage(allMessages);
}

// ═══════════════════════════════════════════
// P2: 会话分支
// ═══════════════════════════════════════════
function branchConversation(fromMessage) {
  const sess = getCurrentSession();
  if (!sess) return;
  const newSess = new ChatSession('agent-' + Date.now());
  newSess.workspace = sess.workspace;
  newSess.title = sess.title + ' (分支)';
  if (fromMessage) {
    const idx = sess.messages.indexOf(fromMessage);
    newSess.messages = sess.messages.slice(0, idx + 1).map(m => ({ ...m, streaming: false, editing: false }));
  } else {
    newSess.messages = sess.messages.map(m => ({ ...m, streaming: false, editing: false }));
  }
  state.sessions.unshift(newSess);
  state.currentSessionId = newSess.agentId;
  render();
  showToast('已创建对话分支');
}

// ═══════════════════════════════════════════
// P2: 导出
// ═══════════════════════════════════════════
function exportSession(sess) {
  const data = {
    agentId: sess.agentId,
    title: sess.title,
    messages: sess.messages.map(m => ({ role: m.role, content: m.content, meta: m.meta })),
    stats: sess.stats,
    exportedAt: new Date().toISOString(),
  };
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = el('a', { href: url, download: 'conversation-' + sess.agentId + '-' + Date.now() + '.json' });
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  showToast('对话已导出');
}

// ═══════════════════════════════════════════
// P2: 技能
// ═══════════════════════════════════════════
function showSkillsModal() {
  const existing = $('.modal-overlay');
  if (existing) existing.remove();
  const overlay = el('div', { class: 'modal-overlay', onclick: function(e) { if (e.target === overlay) overlay.remove(); } });
  const modal = el('div', { class: 'modal', style: 'width:500px' });
  const header = el('div', { class: 'modal-header' });
  header.appendChild(el('div', { class: 'modal-title' }, '技能列表'));
  header.appendChild(el('button', { class: 'modal-close', onclick: function() { overlay.remove(); } }, '×'));
  modal.appendChild(header);
  const body = el('div', { class: 'modal-body' });
  if (state.skills.length === 0) {
    body.appendChild(el('div', { class: 'empty' }, '暂无可用技能'));
  } else {
    for (const skill of state.skills) {
      const card = el('div', { class: 'skill-card' });
      card.appendChild(el('div', { class: 'skill-card-name' }, skill.name || skill.id));
      card.appendChild(el('div', { class: 'skill-card-desc' }, skill.description || ''));
      if (skill.triggers) {
        card.appendChild(el('div', { class: 'skill-card-triggers' }, '触发: ' + skill.triggers));
      }
      body.appendChild(card);
    }
  }
  modal.appendChild(body);
  overlay.appendChild(modal);
  document.body.appendChild(overlay);
}

// ═══════════════════════════════════════════
// P2: 反馈
// ═══════════════════════════════════════════
function showFeedbackModal(sess) {
  const existing = $('.modal-overlay');
  if (existing) existing.remove();
  const overlay = el('div', { class: 'modal-overlay', onclick: function(e) { if (e.target === overlay) overlay.remove(); } });
  const modal = el('div', { class: 'modal', style: 'width:400px' });
  const header = el('div', { class: 'modal-header' });
  header.appendChild(el('div', { class: 'modal-title' }, '提交反馈'));
  header.appendChild(el('button', { class: 'modal-close', onclick: function() { overlay.remove(); } }, '×'));
  modal.appendChild(header);
  const body = el('div', { class: 'modal-body' });
  const textarea = el('textarea', { class: 'form-input', rows: '4', placeholder: '输入你的反馈…', style: 'width:100%;resize:vertical' });
  body.appendChild(textarea);
  modal.appendChild(body);
  const footerEl = el('div', { class: 'modal-footer' });
  footerEl.appendChild(el('button', { class: 'btn btn-primary', onclick: function() {
    const text = textarea.value.trim();
    if (text) {
      showToast('反馈已提交，感谢！');
      overlay.remove();
    } else {
      showToast('请输入反馈内容');
    }
  } }, '提交'));
  modal.appendChild(footerEl);
  overlay.appendChild(modal);
  document.body.appendChild(overlay);
}

// ═══════════════════════════════════════════
// P2: Agent 预设
// ═══════════════════════════════════════════
function applyAgentPreset(preset) {
  newChat();
  const sess = getCurrentSession();
  if (sess) {
    sess.title = preset.name;
    sess.systemPrompt = preset.systemPrompt;
    if (preset.model) {
      const matchedChannel = (state.availableModels || []).find(function(model) {
        return model.modelCode === preset.model;
      });
      if (matchedChannel && matchedChannel.channelCode) {
        state.currentChannel = matchedChannel.channelCode;
        localStorage.setItem('currentChannel', matchedChannel.channelCode);
      }
    }
  }
  render();
  showToast('已应用预设: ' + preset.name);
}

// ═══════════════════════════════════════════
// P2: 目录选择器
// ═══════════════════════════════════════════
function showDirectoryPicker(callback) {
  const existing = $('.modal-overlay');
  if (existing) existing.remove();
  const overlay = el('div', { class: 'modal-overlay', onclick: function(e) { if (e.target === overlay) overlay.remove(); } });
  const modal = el('div', { class: 'modal dir-picker-modal', style: 'width:450px' });
  const header = el('div', { class: 'modal-header' });
  header.appendChild(el('div', { class: 'modal-title' }, '选择目录'));
  header.appendChild(el('button', { class: 'modal-close', onclick: function() { overlay.remove(); } }, '×'));
  modal.appendChild(header);
  const body = el('div', { class: 'modal-body' });

  // 当前路径
  let currentPath = state.currentWorkspace || '/';
  const pathEl = el('div', { class: 'dir-picker-path' }, currentPath);
  body.appendChild(pathEl);

  // 尝试从后端加载目录列表；失败则用预设
  const listEl = el('div', { class: 'dir-picker-list' });
  body.appendChild(listEl);
  
  async function loadDirs(path) {
    listEl.innerHTML = '';
    listEl.appendChild(el('div', { class: 'dir-picker-loading' }, '加载中…'));
    try {
      const dirs = await apiRequest(API + '/workspaces/list?path=' + encodeURIComponent(path));
        listEl.innerHTML = '';
        if (dirs.length === 0) listEl.appendChild(el('div', { class: 'dir-picker-empty' }, '无子目录'));
        for (const d of dirs) {
          const name = typeof d === 'string' ? d : (d.name || d.path);
          const isDir = typeof d === 'string' ? d.endsWith('/') : (d.isDirectory !== false);
          if (!isDir) continue;
          const item = el('button', {
            class: 'dir-picker-item',
            onclick: function() {
              if (name === '..') {
                currentPath = currentPath.split('/').slice(0, -2).join('/') + '/';
              } else if (name !== '.') {
                currentPath += name;
              }
              pathEl.textContent = currentPath;
              loadDirs(currentPath);
            }
          }, name);
          listEl.appendChild(item);
        }
    } catch (e) {
      // 后端不可用时用预设目录
      listEl.innerHTML = '';
      const dirs = ['.', '..', 'src/', 'docs/', 'tests/', 'config/', 'scripts/'];
      for (const d of dirs) {
        const item = el('button', {
          class: 'dir-picker-item',
          onclick: function() {
            if (d === '..') {
              currentPath = currentPath.split('/').slice(0, -2).join('/') + '/';
            } else if (d !== '.') {
              currentPath += d;
            }
            pathEl.textContent = currentPath;
          }
        }, d);
        listEl.appendChild(item);
      }
    }
  }
  loadDirs(currentPath);
  modal.appendChild(body);

  const footerEl = el('div', { class: 'modal-footer' });
  footerEl.appendChild(el('button', { class: 'btn btn-secondary', onclick: function() { overlay.remove(); } }, '取消'));
  footerEl.appendChild(el('button', { class: 'btn btn-primary', onclick: function() {
    if (callback) callback(currentPath);
    overlay.remove();
  } }, '选择'));
  modal.appendChild(footerEl);

  overlay.appendChild(modal);
  document.body.appendChild(overlay);
}

// ═══════════════════════════════════════════
// P2: 侧边面板
// ═══════════════════════════════════════════
function renderSidePanel() {
  const sess = getCurrentSession();
  if (!sess) return el('div', { class: 'side-panel' });

  const panel = el('div', { class: 'side-panel' });
  const header = el('div', { class: 'side-panel-header' });
  const titles = {
    todos: '待办列表', plan: '计划', goal: '目标',
    subagents: '子代理', deliverables: '交付物',
    jobs: '后台任务', trajectory: '轨迹', recall: '会话回忆'
  };
  header.appendChild(el('div', { class: 'side-panel-title' }, titles[state.activePanel] || ''));
  header.appendChild(el('button', { class: 'side-panel-close', onclick: function() { state.activePanel = null; render(); } }, '×'));
  panel.appendChild(header);

  const body = el('div', { class: 'side-panel-body' });

  switch (state.activePanel) {
    case 'todos': renderTodosPanel(body, sess); break;
    case 'plan': renderPlanPanel(body, sess); break;
    case 'goal': renderGoalPanel(body, sess); break;
    case 'subagents': renderSubagentsPanel(body, sess); break;
    case 'deliverables': renderDeliverablesPanel(body, sess); break;
    case 'jobs': renderJobsPanel(body, sess); break;
    case 'trajectory': renderTrajectoryPanel(body, sess); break;
    case 'recall': renderRecallPanel(body, sess); break;
  }

  panel.appendChild(body);
  return panel;
}

// ── To-do 面板 ─────────────────────────────
function renderTodosPanel(body, sess) {
  if (!sess.todos || sess.todos.length === 0) {
    body.appendChild(el('div', { class: 'empty' }, '暂无待办'));
  } else {
    for (let i = 0; i < sess.todos.length; i++) {
      const todo = sess.todos[i];
      const item = el('div', { class: 'todo-item' });
      const checkbox = el('input', { type: 'checkbox', checked: todo.done, onchange: function(e) { todo.done = e.target.checked; render(); } });
      item.appendChild(checkbox);
      item.appendChild(el('span', { class: 'todo-text' + (todo.done ? ' done' : '') }, todo.text));
      item.appendChild(el('span', { class: 'todo-status ' + (todo.done ? 'done' : 'pending') }, todo.done ? '完成' : '待处理'));
      item.appendChild(el('button', { class: 'todo-remove', onclick: function() { sess.todos.splice(i, 1); render(); } }, '×'));
      body.appendChild(item);
    }
  }
  // 添加
  const addArea = el('div', { class: 'todo-add' });
  const input = el('input', { class: 'form-input', placeholder: '添加待办…', style: 'flex:1' });
  addArea.appendChild(input);
  addArea.appendChild(el('button', { class: 'btn btn-primary', onclick: function() {
    const text = input.value.trim();
    if (text) { sess.todos.push({ text, done: false }); render(); }
  } }, '添加'));
  body.appendChild(addArea);
}

// ── Plan 面板 ──────────────────────────────
function renderPlanPanel(body, sess) {
  if (!sess.plan || !sess.plan.steps || sess.plan.steps.length === 0) {
    body.appendChild(el('div', { class: 'empty' }, '暂无计划。使用对话让 Agent 生成计划。'));
  } else {
    for (let i = 0; i < sess.plan.steps.length; i++) {
      const step = sess.plan.steps[i];
      const item = el('div', { class: 'plan-step' });
      item.appendChild(el('div', { class: 'plan-step-num' }, i + 1));
      item.appendChild(el('div', { class: 'plan-step-content' },
        el('div', { class: 'plan-step-title' }, step.title),
        el('div', { class: 'plan-step-status ' + (step.status || 'pending') }, step.status || 'pending')
      ));
      body.appendChild(item);
    }
  }
}

// ── Goal 面板 ──────────────────────────────
function renderGoalPanel(body, sess) {
  if (!sess.goal) {
    body.appendChild(el('div', { class: 'empty' }, '暂无目标。使用对话设定目标。'));
  } else {
    const goal = sess.goal;
    const card = el('div', { class: 'goal-item' });
    card.appendChild(el('div', { class: 'goal-text' }, goal.text || '未命名目标'));
    card.appendChild(el('div', { class: 'goal-status ' + (goal.status || 'active') }, goal.status || 'active'));
    if (goal.progress != null) {
      const progressEl = el('div', { class: 'goal-progress' });
      const bar = el('div', { class: 'goal-progress-bar', style: 'width:' + goal.progress + '%' });
      progressEl.appendChild(bar);
      card.appendChild(progressEl);
      card.appendChild(el('span', { class: 'goal-progress-text' }, goal.progress + '%'));
    }
    body.appendChild(card);
  }
}

// ── Subagents 面板 ─────────────────────────
function renderSubagentsPanel(body, sess) {
  if (!sess.subagents || sess.subagents.length === 0) {
    body.appendChild(el('div', { class: 'empty' }, '暂无子代理'));
  } else {
    for (const sa of sess.subagents) {
      const card = el('div', { class: 'subagent-card' });
      card.appendChild(el('div', { class: 'subagent-avatar' }, '🤖'));
      card.appendChild(el('div', { class: 'subagent-info' },
        el('div', { class: 'subagent-name' }, sa.name || sa.id),
        el('div', { class: 'subagent-status ' + (sa.status || 'idle') }, sa.status || 'idle')
      ));
      if (sa.preview) {
        card.appendChild(el('div', { class: 'subagent-preview' }, sa.preview));
      }
      body.appendChild(card);
    }
  }
}

// ── Deliverables 面板 ──────────────────────
function renderDeliverablesPanel(body, sess) {
  if (!sess.deliverables || sess.deliverables.length === 0) {
    body.appendChild(el('div', { class: 'empty' }, '暂无交付物'));
  } else {
    for (const d of sess.deliverables) {
      const card = el('div', { class: 'deliverable-card' });
      const icon = d.type === 'file' ? '📄' : d.type === 'image' ? '🖼' : '📦';
      card.appendChild(el('span', { class: 'deliverable-icon' }, icon));
      card.appendChild(el('div', { class: 'deliverable-info' },
        el('div', { class: 'deliverable-name' }, d.name),
        el('div', { class: 'deliverable-type' }, d.type || 'file')
      ));
      if (d.url) {
        card.appendChild(el('a', { href: d.url, download: d.name, class: 'deliverable-download' }, '下载'));
      }
      body.appendChild(card);
    }
  }
}

// ── Jobs 面板 ──────────────────────────────
function renderJobsPanel(body, sess) {
  if (!sess.jobs || sess.jobs.length === 0) {
    body.appendChild(el('div', { class: 'empty' }, '暂无后台任务'));
  } else {
    for (const job of sess.jobs) {
      const item = el('div', { class: 'job-item' });
      item.appendChild(el('div', { class: 'job-info' },
        el('div', { class: 'job-name' }, job.name),
        el('div', { class: 'job-status ' + (job.status || 'running') }, job.status || 'running')
      ));
      if (job.progress != null) {
        const progressEl = el('div', { class: 'job-progress' });
        const bar = el('div', { class: 'job-progress-bar', style: 'width:' + job.progress + '%' });
        progressEl.appendChild(bar);
        item.appendChild(progressEl);
      }
      if (job.duration) {
        item.appendChild(el('span', { class: 'job-duration' }, job.duration));
      }
      body.appendChild(item);
    }
  }
}

// ── Trajectory 面板 ────────────────────────
// v29: 轨迹视图 — 统计条 + 三泳道时间线（Input/Model/Tools）+ 搜索 + 步骤明细列表
function renderTrajectoryPanel(body, sess) {
  const log = sess.trajectoryLog || [];
  if (log.length === 0) {
    body.appendChild(el('div', { class: 'trajectory-empty' }, '暂无轨迹数据，发送消息后将自动记录完整执行日志。'));
    return;
  }

  // ── 顶部统计条（时长 / Turn / 工具数 / 记录数 + 搜索框）──────────────
  const startTs = log[0].t0 || 0;
  const endTs = log.reduce(function(mx, e) { return Math.max(mx, e.t1 || e.t0 || 0); }, startTs);
  const totalMs = Math.max(1, endTs - startTs);
  const userCount = log.filter(function(e) { return e.kind === 'user'; }).length;
  const msgCount = log.filter(function(e) { return e.kind === 'message'; }).length;
  const toolCount = log.filter(function(e) { return e.kind === 'tool'; }).length;
  const sysCount = log.filter(function(e) { return e.kind === 'system'; }).length;
  const errCount = log.filter(function(e) { return e.kind === 'error'; }).length;
  const maxTurn = log.reduce(function(mx, e) { return Math.max(mx, e.turn || 0); }, 0);

  const summary = el('div', { class: 'traj-summary' });
  summary.appendChild(el('div', { class: 'traj-summary-item', title: '会话总耗时' },
    el('span', { class: 'traj-summary-ico', text: '⏱' }),
    el('span', { class: 'traj-summary-num', text: (totalMs / 1000).toFixed(1) + 's' }),
    el('span', { class: 'traj-summary-lbl', text: 'Duration' })));
  summary.appendChild(el('div', { class: 'traj-summary-item', title: '对话轮次' },
    el('span', { class: 'traj-summary-ico', text: '🔁' }),
    el('span', { class: 'traj-summary-num', text: String(maxTurn) }),
    el('span', { class: 'traj-summary-lbl', text: 'Turns' })));
  summary.appendChild(el('div', { class: 'traj-summary-item', title: '工具调用次数' },
    el('span', { class: 'traj-summary-ico', text: '🛠' }),
    el('span', { class: 'traj-summary-num', text: String(toolCount) }),
    el('span', { class: 'traj-summary-lbl', text: 'Tools' })));
  summary.appendChild(el('div', { class: 'traj-summary-item', title: '总事件数（用户 ' + userCount + ' · 模型 ' + msgCount + ' · 工具 ' + toolCount + ' · 系统 ' + sysCount + ' · 错误 ' + errCount + '）' },
    el('span', { class: 'traj-summary-ico', text: '📋' }),
    el('span', { class: 'traj-summary-num', text: String(log.length) }),
    el('span', { class: 'traj-summary-lbl', text: 'Events' })));

  const searchBox = el('input', {
    class: 'traj-search',
    type: 'search',
    placeholder: '搜索轨迹…',
    oninput: function(e) {
      state.trajSearch = e.target.value || '';
      // 只刷新列表区，避免输入框失焦
      renderTrajectoryList(sess);
    },
  });
  searchBox.value = state.trajSearch || '';
  summary.appendChild(searchBox);
  body.appendChild(summary);

  // ── 时间线（甘特图）────────────────────────────────────────────
  const timeline = el('div', { class: 'traj-timeline' });
  const tlHead = el('div', { class: 'traj-tl-head' });
  tlHead.appendChild(el('span', { class: 'traj-tl-title', text: '⏱ 执行时间线' }));
  const legend = el('div', { class: 'traj-tl-legend' });
  legend.appendChild(el('span', { class: 'traj-tl-legend-item' }, el('i', { class: 'traj-tl-swatch traj-tl-swatch-input' }), 'Input'));
  legend.appendChild(el('span', { class: 'traj-tl-legend-item' }, el('i', { class: 'traj-tl-swatch traj-tl-swatch-model' }), 'Model'));
  legend.appendChild(el('span', { class: 'traj-tl-legend-item' }, el('i', { class: 'traj-tl-swatch traj-tl-swatch-tool' }), 'Tools'));
  tlHead.appendChild(legend);
  timeline.appendChild(tlHead);

  // 时间刻度（0 / 25% / 50% / 75% / 100%）
  const tlTicks = el('div', { class: 'traj-tl-ticks' });
  const tickLabels = ['0s', (totalMs / 4000).toFixed(1) + 's', (totalMs / 2000).toFixed(1) + 's', (totalMs / 4 * 3 / 1000).toFixed(1) + 's', (totalMs / 1000).toFixed(1) + 's'];
  for (var ti = 0; ti < tickLabels.length; ti++) {
    tlTicks.appendChild(el('span', { class: 'traj-tl-tick', text: tickLabels[ti] }));
  }
  timeline.appendChild(tlTicks);

  // 三泳道：Input / Model / Tools
  const lanes = [
    { key: 'input', label: 'Input', events: log.filter(function(e) { return e.kind === 'user' || e.kind === 'system'; }) },
    { key: 'model', label: 'Model', events: log.filter(function(e) { return e.kind === 'message' || e.kind === 'error'; }) },
    { key: 'tool', label: 'Tools', events: log.filter(function(e) { return e.kind === 'tool'; }) },
  ];
  for (var li = 0; li < lanes.length; li++) {
    var lane = lanes[li];
    var laneEl = el('div', { class: 'traj-tl-lane traj-tl-lane-' + lane.key });
    laneEl.appendChild(el('div', { class: 'traj-tl-lane-label', text: lane.label }));
    var bars = el('div', { class: 'traj-tl-lane-bars' });
    for (var ei = 0; ei < lane.events.length; ei++) {
      var ev = lane.events[ei];
      var left = Math.max(0, Math.min(100, ((ev.t0 - startTs) / totalMs) * 100));
      var width = Math.max(1.5, Math.min(100 - left, (((ev.t1 || ev.t0) - ev.t0) / totalMs) * 100));
      var barCls = 'traj-tl-bar traj-tl-bar-' + lane.key;
      if (ev.status === 'running') barCls += ' traj-tl-bar-running';
      if (ev.status === 'error' || ev.kind === 'error' || ev.error) barCls += ' traj-tl-bar-error';
      var tipText = (ev.kind === 'tool' ? (ev.toolName || 'tool') : (KIND_LABELS[ev.kind] || ev.kind)) +
        (ev.duration ? ' · ' + ev.duration : '') +
        ' · ' + ((ev.t1 - ev.t0) / 1000).toFixed(1) + 's' +
        (ev.error ? '\n❌ ' + ev.error : '');
      var bar = el('div', {
        class: barCls,
        style: 'left:' + left.toFixed(2) + '%;width:' + width.toFixed(2) + '%',
        title: tipText,
      });
      bars.appendChild(bar);
    }
    laneEl.appendChild(bars);
    timeline.appendChild(laneEl);
  }
  body.appendChild(timeline);

  // ── 步骤明细列表（含搜索过滤）───────────────────────────────────
  const listWrap = el('div', { class: 'traj-list-wrap', id: 'traj-list-wrap' });
  body.appendChild(listWrap);
  renderTrajectoryListInto(listWrap, sess);
}

const KIND_LABELS = { user: 'USER', message: 'ASSISTANT', tool: 'TOOL', system: 'SYSTEM', error: 'ERROR' };

function renderTrajectoryList(sess) {
  var wrap = document.getElementById('traj-list-wrap');
  if (wrap) renderTrajectoryListInto(wrap, sess);
}

function renderTrajectoryListInto(listWrap, sess) {
  listWrap.innerHTML = '';
  const log = sess.trajectoryLog || [];
  const q = (state.trajSearch || '').toLowerCase();
  var matched = q ? log.filter(function(e) {
    return (e.text || '').toLowerCase().indexOf(q) !== -1 ||
           (e.toolName || '').toLowerCase().indexOf(q) !== -1 ||
           (KIND_LABELS[e.kind] || '').toLowerCase().indexOf(q) !== -1 ||
           (e.error || '').toLowerCase().indexOf(q) !== -1;
  }) : log;
  // v29.1: 倒序优先（最新在最上面，免滚动）；正序模式保持事件顺序
  var desc = state.trajDesc !== false;
  var filtered = desc ? matched.slice().reverse() : matched;

  if (matched.length === 0) {
    listWrap.appendChild(el('div', { class: 'trajectory-empty' }, '没有匹配「' + (state.trajSearch || '') + '」的记录。'));
    return;
  }

  const listHead = el('div', { class: 'traj-list-head' });
  listHead.appendChild(el('span', { class: 'traj-list-head-title' }, '📋 执行步骤明细' + (q ? '（' + matched.length + ' / ' + log.length + '）' : '')));
  listHead.appendChild(el('button', {
    class: 'traj-order-btn',
    title: '切换正序/倒序',
    onclick: function() {
      state.trajDesc = !desc;
      renderTrajectoryList(sess);
    },
  }, desc ? '最新在上 ↓' : '最早在上 ↑'));
  listWrap.appendChild(listHead);

  const table = el('div', { class: 'traj-table' });
  let lastTurn = -1;

  for (const entry of filtered) {
    // Turn 分隔线（倒序时轮次自然降序排列，切换条件不变）
    if (entry.turn !== lastTurn && entry.turn > 0) {
      const turnDivider = el('div', { class: 'traj-turn-divider' });
      turnDivider.appendChild(el('span', { class: 'traj-turn-label' }, 'Turn ' + entry.turn));
      table.appendChild(turnDivider);
      lastTurn = entry.turn;
    }

    const rowCls = 'traj-row traj-kind-' + (entry.kind || 'unknown') + (entry.status === 'running' ? ' traj-row-running' : '');
    const row = el('div', { class: rowCls });

    // 左侧指示点 + 序号
    row.appendChild(el('div', { class: 'traj-td traj-td-seq' },
      el('span', { class: 'traj-dot traj-dot-' + (entry.kind || 'unknown') }),
      el('span', { text: String(entry.seq) })));

    // Turn 列
    row.appendChild(el('div', { class: 'traj-td traj-td-turn' }, entry.turn > 0 ? String(entry.turn) : ''));

    // 类型列
    const kindCell = el('div', { class: 'traj-td traj-td-kind' });
    kindCell.appendChild(el('span', { class: 'traj-kind-badge traj-kind-' + (entry.kind || 'unknown') }, KIND_LABELS[entry.kind] || entry.kind));
    if (entry.toolName) kindCell.appendChild(el('span', { class: 'traj-tool-name' }, entry.toolName));
    row.appendChild(kindCell);

    // 内容列（可展开）
    const textCell = el('div', { class: 'traj-td traj-td-text' });
    const previewText = (entry.text || '').replace(/\s+/g, ' ').trim();
    const hasMore = previewText.length > 120 || (entry.result && entry.result.length > 120);
    const preview = el('span', { class: 'traj-text-preview' }, previewText.slice(0, 200) || '—');
    textCell.appendChild(preview);
    if (entry.error) textCell.appendChild(el('span', { class: 'traj-error' }, entry.error));
    if (entry.status && entry.status !== 'success' && entry.status !== 'completed') {
      textCell.appendChild(el('span', { class: 'traj-status' }, entry.status));
    }
    if (hasMore) {
      const expandBtn = el('button', {
        class: 'traj-expand-btn',
        title: '展开/收起完整内容',
        onclick: function() {
          var detail = textCell.querySelector('.traj-detail');
          if (detail) { detail.remove(); return; }
          var fullText = entry.kind === 'tool' && entry.result ? entry.result : (entry.text || '');
          var detail = el('pre', { class: 'traj-detail' }, fullText);
          textCell.appendChild(detail);
        },
      }, '⋯');
      textCell.appendChild(expandBtn);
    }
    row.appendChild(textCell);

    // 时间列
    row.appendChild(el('div', { class: 'traj-td traj-td-time', title: entry.time || '' }, formatTime(entry.time)));

    // 耗时列
    row.appendChild(el('div', { class: 'traj-td traj-td-duration' }, entry.duration || (entry.status === 'running' ? '…' : '—')));

    // Tokens 列
    const tokensCell = el('div', { class: 'traj-td traj-td-tokens' });
    if (entry.input != null) tokensCell.appendChild(el('span', { class: 'traj-token-in', title: '输入' }, '↑' + entry.input));
    if (entry.output != null) tokensCell.appendChild(el('span', { class: 'traj-token-out', title: '输出' }, '↓' + entry.output));
    if (entry.input == null && entry.output == null) tokensCell.appendChild(el('span', null, '—'));
    row.appendChild(tokensCell);

    table.appendChild(row);
  }
  listWrap.appendChild(table);
}

// ── Recall 面板 ────────────────────────────
function renderRecallPanel(body, sess) {
  if (!sess.recalls || sess.recalls.length === 0) {
    body.appendChild(el('div', { class: 'empty' }, '暂无会话回忆'));
  } else {
    for (const r of sess.recalls) {
      const item = el('div', { class: 'recall-item' });
      item.appendChild(el('div', { class: 'recall-info' },
        el('div', { class: 'recall-session' }, r.sessionLabel || r.sessionId),
        el('div', { class: 'recall-summary' }, r.summary || '')
      ));
      body.appendChild(item);
    }
  }
}

// ═══════════════════════════════════════════
// Markdown 解析
// ═══════════════════════════════════════════
function parseMessageContent(content, streaming, isUser) {
  if (!content) return '';
  if (isUser) {
    const div = document.createElement('div');
    div.textContent = content;
    return div.innerHTML.replace(/\n/g, '<br>');
  }

  let processed = content;

  // 提取 think/thinking 块
  const thinkRegex = /<(?:think|thinking)>([\s\S]*?)<\/(?:think|thinking)>/g;
  let m;
  while ((m = thinkRegex.exec(content)) !== null) {
    processed = processed.replace(m[0], '<div class="think-block"><div class="think-block-header"><span class="think-block-icon">🧠</span> 思考过程 <span class="think-block-toggle">▸</span></div><div class="think-block-body">' + escapeHtml(m[1].trim()) + '</div></div>');
  }

  const contextRegex = /<context>([\s\S]*?)<\/context>/g;
  while ((m = contextRegex.exec(content)) !== null) {
    processed = processed.replace(m[0], '<div class="collapsible"><div class="collapsible-header"><span class="collapsible-icon">▶</span> 上下文</div><div class="collapsible-body">' + escapeHtml(m[1].trim()) + '</div></div>');
  }

  if (streaming && processed.length < 50) {
    // 极短内容直接输出避免 marked 解析开销
    const div = document.createElement('div');
    div.textContent = processed;
    return div.innerHTML.replace(/\n/g, '<br>');
  }

  // 容错：中性化未闭合的加粗标记，避免模型偶发残缺输出导致 `**` 裸露。
  // 统计未转义的 `**` 个数，若为奇数说明存在未配对，逐个转义为字面量。
  processed = neutralizeUnclosedBold(processed);

  try {
    const html = marked.parse(processed);
    // 使用 DOMPurify 清洗 HTML，防止 XSS
    if (typeof DOMPurify !== 'undefined') {
      return DOMPurify.sanitize(html, { ADD_ATTR: ['class'], ADD_TAGS: ['div', 'span'] });
    }
    return html;
  } catch (e) {
    const div = document.createElement('div');
    div.textContent = processed;
    return div.innerHTML;
  }
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function renderAssistantContent(message, streaming) {
  if (Array.isArray(message.blocks) && message.blocks.length > 0) {
    return message.blocks.map(function(block) {
      if (block.kind === 'reasoning' && block.text) {
        return '<details class="think-block"><summary class="think-block-header">'
          + '<span class="think-block-icon">🧠</span> Think'
          + '<span class="think-block-sep" aria-hidden="true"></span>'
          + '<span class="think-block-summary">' + escapeHtml(firstTextLine(block.text)) + '</span>'
          + '<span class="think-block-toggle">▸</span></summary>'
          + '<div class="think-block-body">' + escapeHtml(block.text) + '</div></details>';
      }
      if (block.kind === 'text') return parseMessageContent(block.text, streaming, false);
      return '';
    }).join('');
  }
  if (message.reasoning) {
    return '<details class="think-block streaming"'
      + (streaming ? '' : ' open')
      + '><summary class="think-block-header"><span class="think-block-icon">🧠</span> Think'
      + '<span class="think-block-sep" aria-hidden="true"></span>'
      + '<span class="think-block-summary">' + escapeHtml(latestTextLine(message.reasoning)) + '</span>'
      + '<span class="think-block-toggle">▸</span></summary>'
      + '<div class="think-block-body">' + escapeHtml(message.reasoning) + '</div></details>';
  }
  return parseMessageContent(message.content || '', streaming, message.role === 'user');
}

function streamTextLength(message) {
  if (Array.isArray(message.blocks) && message.blocks.length > 0) {
    return message.blocks
      .filter(block => block.kind === 'text')
      .reduce((total, block) => total + (block.text || '').length, 0);
  }
  return (message.content || '').length;
}

function visibleAssistantMessage(message, limit) {
  if (limit == null) return message;
  if (Array.isArray(message.blocks) && message.blocks.length > 0) {
    let remaining = limit;
    const blocks = message.blocks.map(block => {
      if (block.kind !== 'text') return block;
      const text = block.text || '';
      const visible = text.slice(0, Math.max(0, remaining));
      remaining -= text.length;
      return { ...block, text: visible };
    });
    return { ...message, blocks };
  }
  return { ...message, content: (message.content || '').slice(0, Math.max(0, limit)) };
}

function startStreamReveal(message) {
  message._targetLength = streamTextLength(message);
  if (message._revealLength == null) message._revealLength = 0;
  if (message._revealTimer) return;
  message._revealTimer = setInterval(function() {
    if (message._revealLength < message._targetLength) {
      const pending = message._targetLength - message._revealLength;
      const step = Math.max(4, Math.ceil(Math.max(pending / 16, message._targetLength / 320)));
      message._revealLength = Math.min(message._targetLength, message._revealLength + step);
      paintStreamingMessage(message);
    } else if (!message.streaming) {
      stopStreamReveal(message);
      render();
    }
  }, 24);
}

function stopStreamReveal(message) {
  if (message._revealTimer) {
    clearInterval(message._revealTimer);
    message._revealTimer = null;
  }
  message._revealLength = message._targetLength || streamTextLength(message);
}

function waitForStreamReveal(message) {
  return new Promise(function(resolve) {
    if (!message || (message._revealLength || 0) >= (message._targetLength || 0)) {
      resolve();
      return;
    }
    const poll = setInterval(function() {
      if ((message._revealLength || 0) >= (message._targetLength || 0)) {
        clearInterval(poll);
        resolve();
      }
    }, 24);
  });
}

function paintStreamingMessage(message) {
  const msgs = $('.messages-inner');
  if (!msgs) return;
  const msgEls = msgs.querySelectorAll('.msg.assistant');
  const lastEl = msgEls[msgEls.length - 1];
  if (!lastEl) return;
  const contentEl = lastEl.querySelector('.msg-content');
  if (!contentEl) return;
  const html = renderAssistantContent(visibleAssistantMessage(message, message._revealLength), true);
  if (paintStreamingMessage._lastHtml === html) return;
  paintStreamingMessage._lastHtml = html;
  contentEl.innerHTML = html;
  contentEl.appendChild(el('span', { class: 'streaming-cursor' }, '▋'));
  const messagesContainer = $('.messages');
  if (messagesContainer) messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// 将未闭合的 `**` 加粗标记转义为字面量，避免裸露显示。
// 思路：marked 只在 `**` 成对闭合时渲染粗体；孤立（奇数）的 `**` 会原样显示。
// 这里统计未转义 `**` 出现次数，把多余的（导致无法配对的）替换为 `\*\*`。
// 仅作为容错兜底——正常模型输出的成对 `**` 不受影响。
function neutralizeUnclosedBold(text) {
  if (!text || text.indexOf('**') === -1) return text;
  // 移除行内代码与代码块，避免误伤代码里的 `**`
  const codeSpans = [];
  let stripped = text.replace(/(`{3}[\s\S]*?`{3}|`[^`\n]*`)/g, function(m) {
    codeSpans.push(m);
    return '\u0000CODE' + (codeSpans.length - 1) + '\u0000';
  });
  const matches = stripped.match(/\*\*/g);
  if (!matches || matches.length % 2 === 0) return text; // 成对，正常
  // 奇数个：把最后一个 `**` 转义（从后往前找第一个未转义的）
  let idx = stripped.lastIndexOf('**');
  if (idx !== -1) {
    stripped = stripped.slice(0, idx) + '\\*\\*' + stripped.slice(idx + 2);
  }
  // 还原代码段
  stripped = stripped.replace(/\u0000CODE(\d+)\u0000/g, function(_, i) { return codeSpans[+i]; });
  return stripped;
}

// ═══════════════════════════════════════════
// 统计栏
// ═══════════════════════════════════════════
function renderStatsBar(sess) {
  const bar = el('div', { class: 'stats-bar' });
  const s = sess.stats;
  bar.appendChild(el('span', null, '轮次 ' + s.turns));
  bar.appendChild(el('span', { class: 'stat-sep' }, '·'));
  bar.appendChild(el('span', null, '步骤 ' + s.steps));
  bar.appendChild(el('span', { class: 'stat-sep' }, '·'));
  bar.appendChild(el('span', null, 'LLM ' + (s.llmTimeMs / 1000).toFixed(1) + 's'));
  if (s.firstTokenMs > 0) {
    bar.appendChild(el('span', { class: 'stat-sep' }, '·'));
    bar.appendChild(el('span', null, '首token ' + (s.firstTokenMs / 1000).toFixed(1) + 's'));
  }
  bar.appendChild(el('span', { class: 'stat-sep' }, '·'));
  bar.appendChild(el('span', null, '↑' + s.inputTokens + ' ↓' + s.outputTokens + ' tok'));
  // v11: 压缩信息
  if (sess.compactionInfo && sess.compactionInfo.status === 'done') {
    bar.appendChild(el('span', { class: 'stat-sep' }, '·'));
    bar.appendChild(el('span', null, '📦 压缩' + (sess.compactionInfo.saved || 0) + '条'));
  }
  return bar;
}

// ═══════════════════════════════════════════
// 输入框 — v11 增强
// ═══════════════════════════════════════════
function renderComposer(sess) {
  const composer = el('div', { class: 'composer' });
  const inner = el('div', { class: 'composer-inner' });
  const approvalModes = [
    {
      value: 'REQUEST_APPROVAL',
      label: '请求审批',
      desc: '工具执行前会请求审批，敏感操作需要手动允许。',
    },
    {
      value: 'AUTO_APPROVE',
      label: '帮我审批',
      desc: '自动允许普通工具调用，请确认当前会话可信任。',
    },
    {
      value: 'FULL_OPEN',
      label: '完全开放',
      desc: '将允许读写、命令与网络等全部操作。',
    },
  ];
  const currentApprovalMode = (sess && sess.approvalMode) || 'REQUEST_APPROVAL';
  const currentApprovalModeInfo = approvalModes.find(function(mode) {
    return mode.value === currentApprovalMode;
  }) || approvalModes[0];

  const approvalInline = el('div', {
    class: 'composer-approval-selector',
    title: '允许本地对话授权 · ' + currentApprovalModeInfo.desc
  });
  const approvalSelect = el('select', {
    'aria-label': '审批模式',
    onchange: function(e) {
      if (!sess) return;
      sess.approvalMode = e.target.value;
      const modeInfo = approvalModes.find(function(mode) { return mode.value === sess.approvalMode; });
      approvalInline.title = modeInfo ? modeInfo.desc : '';
    }
  });
  approvalModes.forEach(function(mode) {
    approvalSelect.appendChild(el('option', {
      value: mode.value,
      selected: mode.value === currentApprovalMode
    }, mode.label));
  });
  approvalInline.appendChild(approvalSelect);

  const box = el('div', { class: 'composer-box' });

  // v11: 附件预览区
  if (sess && sess.pendingAttachments && sess.pendingAttachments.length > 0) {
    const attArea = el('div', { class: 'attachment-preview-area' });
    for (let i = 0; i < sess.pendingAttachments.length; i++) {
      const att = sess.pendingAttachments[i];
      const item = el('div', { class: 'attachment-thumb' });
      if (att.type === 'image') {
        item.appendChild(el('img', { src: att.url, class: 'attachment-thumb-img', alt: att.name }));
      }
      item.appendChild(el('span', { class: 'attachment-thumb-name' }, att.name));
      item.appendChild(el('button', { class: 'attachment-thumb-remove', onclick: function() { removeAttachment(sess, i); } }, '×'));
      attArea.appendChild(item);
    }
    box.appendChild(attArea);
  }

  const textarea = el('textarea', {
    class: 'composer-input',
    id: 'composer-input',
    placeholder: '给智能体发送消息',
    rows: '1',
    oninput: function(e) {
      autoResize(e);
      handlePluginMention(this, this.selectionStart);
    },
    onkeydown: function(e) {
      if (handleMentionKeys(this, e)) return;
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && e.keyCode !== 229) {
        e.preventDefault();
        sendMessage();
      }
    },
    onkeyup: function(e) {
      if (hasActivePluginMention() && ['ArrowDown', 'ArrowUp', 'Enter', 'Tab', 'Escape'].includes(e.key)) {
        return;
      }
      if (e.key === '/' || /[a-z]/i.test(e.key)) {
        handleSlashCommand(this, this.selectionStart);
      }
      if (e.key === '@' || /[A-Za-z0-9._-]/i.test(e.key) || e.key === 'Backspace') {
        handlePluginMention(this, this.selectionStart);
      }
    },
    onpaste: function(e) { handleFilePaste(e, sess); },
    ondragover: function(e) { e.preventDefault(); },
    ondrop: function(e) { handleFileDrop(e, sess); }
  });
  box.appendChild(textarea);

  const toolbar = el('div', { class: 'composer-toolbar' });

  const left = el('div', { class: 'composer-left' });
  const attachBtn = el('button', { class: 'composer-attach-btn', onclick: function() {
    const fileInput = el('input', { type: 'file', accept: 'image/*', multiple: 'true', style: 'display:none', onchange: function() {
      for (const file of fileInput.files) {
        addAttachment(sess, file);
      }
    } });
    document.body.appendChild(fileInput);
    fileInput.click();
    document.body.removeChild(fileInput);
  }, title: '添加图片' }, '+');
  left.appendChild(attachBtn);
  left.appendChild(approvalInline);

  toolbar.appendChild(left);

  // Right: channel selector + send/cancel
  const right = el('div', { class: 'composer-right' });

  const modelSel = el('div', { class: 'composer-model-selector' });
  modelSel.appendChild(el('span', { class: 'dot' }));
  const select = el('select', {
    onchange: function(e) {
      state.currentChannel = e.target.value;
      localStorage.setItem('currentChannel', e.target.value);
    }
  });
  if (state.availableModels.length === 0) {
    select.appendChild(el('option', { value: '' }, '当前激活渠道'));
  } else {
    for (const ms of state.availableModels) {
      const val = ms.channelCode || ms.modelCode || 'unknown';
      const label = (ms.displayName || ms.channelCode || '默认渠道') + ' · ' + (ms.modelCode || 'unknown');
      const opt = el('option', { value: val, selected: val === state.currentChannel }, label);
      select.appendChild(opt);
    }
  }
  if (state.currentChannel) select.value = state.currentChannel;
  modelSel.appendChild(select);
  right.appendChild(modelSel);

  // v31: 上下文用量指示圈 — hover 显示已用 tokens
  right.appendChild(renderContextUsageRing(sess));

  // Send / Cancel button
  if (sess && sess.sending) {
    right.appendChild(el('button', { class: 'composer-cancel-btn', onclick: cancelAgent, title: '停止生成' }, '■'));
  } else {
    right.appendChild(el('button', { class: 'composer-send-btn', onclick: sendMessage, title: '发送' }, '↑'));
  }
  toolbar.appendChild(right);

  box.appendChild(toolbar);
  inner.appendChild(box);
  composer.appendChild(inner);
  return composer;
}

// ── 上下文用量指示圈 ────────────────────────
function renderContextUsageRing(sess) {
  const wrap = el('div', { class: 'context-ring-wrap', title: '上下文用量' });

  // 计算用量：优先取 stats，否则从消息内容估算
  const stats = sess ? sess.stats : { inputTokens: 0, outputTokens: 0 };
  let usedTokens = (stats.inputTokens || 0) + (stats.outputTokens || 0);
  if (usedTokens === 0 && sess && sess.messages && sess.messages.length > 0) {
    usedTokens = sess.messages.reduce(function(sum, m) {
      return sum + estimateTokens(typeof m.content === 'string' ? m.content : '');
    }, 0);
  }
  const maxTokens = 128000; // 默认 128K，可从模型配置获取
  const pct = maxTokens > 0 ? Math.min(100, Math.max(0, (usedTokens / maxTokens) * 100)) : 0;

  // 格式化数字
  function fmtTokens(t) {
    if (t >= 1000000) return (t / 1000000).toFixed(1) + 'M';
    if (t >= 1000) return (t / 1000).toFixed(1) + 'K';
    return String(t);
  }

  // SVG 圆环：周长 ≈ 2πr ≈ 62.8，用 stroke-dasharray 模拟百分比
  const r = 9;
  const circumference = 2 * Math.PI * r; // ≈ 56.5
  const dashLen = (pct / 100) * circumference;
  const dashStr = dashLen + ' ' + circumference;

  const svgNS = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(svgNS, 'svg');
  svg.setAttribute('class', 'context-ring-svg');
  svg.setAttribute('viewBox', '0 0 22 22');

  const bgCircle = document.createElementNS(svgNS, 'circle');
  bgCircle.setAttribute('class', 'context-ring-bg');
  bgCircle.setAttribute('cx', '11');
  bgCircle.setAttribute('cy', '11');
  bgCircle.setAttribute('r', String(r));
  svg.appendChild(bgCircle);

  const fgCircle = document.createElementNS(svgNS, 'circle');
  fgCircle.setAttribute('class', 'context-ring-fg');
  fgCircle.setAttribute('cx', '11');
  fgCircle.setAttribute('cy', '11');
  fgCircle.setAttribute('r', String(r));
  fgCircle.setAttribute('stroke-dasharray', dashStr);
  fgCircle.setAttribute('stroke-dashoffset', '0');
  svg.appendChild(fgCircle);

  wrap.appendChild(svg);

  // 高量警告
  if (pct >= 80) wrap.classList.add('ring-danger');
  else if (pct >= 50) wrap.classList.add('ring-warn');

  // Tooltip
  const tooltip = el('div', { class: 'context-ring-tooltip' },
    pct.toFixed(1) + '% · ' + fmtTokens(usedTokens) + ' / ' + fmtTokens(maxTokens) + ' 上下文已使用'
  );
  wrap.appendChild(tooltip);

  return wrap;
}

// ── 设置工作台 ─────────────────────────────
function openSettingsPage() {
  if (arguments.length > 0 && typeof arguments[0] === 'string') state.activeSettingsTab = arguments[0];
  state.activeMainTab = 'settings';
  state.activeSettingsTab = state.activeSettingsTab || 'models';
  render();
}

function renderSettingsPage() {
  const page = el('div', { class: 'settings-app' });
  const nav = el('aside', { class: 'settings-nav' });
  const brand = el('div', { class: 'settings-brand' });
  brand.appendChild(icon('brand', 'settings-brand-icon'));
  brand.appendChild(el('div', null, el('span', { class: 'settings-brand-name' }, 'Harness'),
    el('span', { class: 'settings-brand-caption' }, '设置')));
  nav.appendChild(brand);
  const navGroups = el('div', { class: 'settings-nav-groups' });
  const tabConfigs = [
    { id: 'models', label: '模型配置', desc: '接入、默认模型与 API' },
    { id: 'plugins', label: '插件管理', desc: '安装、启停与参数配置' },
    { id: 'presets', label: '预设', desc: '会话默认行为' },
    { id: 'permissions', label: '权限', desc: '工具审批边界' },
  ];
  for (const item of tabConfigs) {
    const active = state.activeSettingsTab === item.id;
    navGroups.appendChild(el('button', {
      class: 'settings-nav-item' + (active ? ' active' : ''),
      onclick: function() { openSettingsPage(item.id); }
    },
      el('span', { class: 'settings-nav-label' }, item.label),
      el('span', { class: 'settings-nav-desc' }, item.desc)
    ));
  }
  nav.appendChild(navGroups);
  const navFooter = el('div', { class: 'settings-nav-footer' });
  navFooter.appendChild(el('button', { class: 'settings-app-return', onclick: function() { state.activeMainTab = 'chat'; render(); } },
    '← 返回应用'));
  nav.appendChild(navFooter);

  const main = el('main', { class: 'settings-main' });
  const topbar = el('header', { class: 'settings-topbar' });
  const current = tabConfigs.find(function(item) { return item.id === state.activeSettingsTab; }) || tabConfigs[0];
  topbar.appendChild(el('div', null,
    el('h1', { class: 'settings-page-title' }, current.label),
    el('p', { class: 'settings-page-subtitle' }, current.desc)
  ));
  topbar.appendChild(el('div', { class: 'settings-topbar-actions' },
    el('button', { class: 'btn btn-secondary', onclick: function() { state.activeMainTab = 'chat'; render(); } }, '返回应用')
  ));
  main.appendChild(topbar);
  const body = el('section', { class: 'settings-body' });
  switch (state.activeSettingsTab) {
    case 'models': renderModelSettings(body, null); break;
    case 'plugins': renderPluginSettings(body, null); break;
    case 'presets': renderPresetSettings(body, null); break;
    case 'permissions': renderPermissionSettings(body, null); break;
  }
  main.appendChild(body);
  page.appendChild(nav);
  page.appendChild(main);
  return page;
}

function renderModelSettings(body, footer) {
  const syncState = { discovered: [], presets: [] };
  const formPanel = el('section', { class: 'settings-panel' });
  formPanel.appendChild(el('div', { class: 'settings-panel-head' },
    el('div', null,
      el('h2', { class: 'settings-panel-title' }, '接入模型'),
      el('p', { class: 'settings-panel-desc' }, '选择渠道模板自动填充协议与地址；支持 OpenAI 兼容、Anthropic、Ollama 协议，可先同步模型再保存。')),
    el('span', { class: 'settings-pill' }, '多协议')
  ));

  const form = el('div', { class: 'settings-form-grid' });

  form.appendChild(wrapInput('displayName', '渠道名称', '例如：公司代理、本地 Ollama'));

  // 渠道模板下拉：选中后自动填充 providerCode / protocol / baseUrl 与推荐模型
  const presetGroup = el('div', { class: 'form-group' });
  presetGroup.appendChild(el('label', { class: 'form-label' }, '渠道模板'));
  const presetSelect = el('select', { class: 'form-input' },
    el('option', { value: '' }, '— 选择渠道模板快捷填充 —'));
  presetGroup.appendChild(presetSelect);
  form.appendChild(presetGroup);

  // Provider 由渠道模板自动填充，不向用户展示；隐藏字段仅作为提交载体
  const providerGroup = wrapInput('providerCode', 'Provider', 'custom');
  providerGroup.style.display = 'none';
  providerGroup.querySelector('input').value = 'custom';
  form.appendChild(providerGroup);
  form.appendChild(wrapInput('baseUrl', 'BaseUrl', 'https://your-openai-compatible-endpoint/v1'));
  form.appendChild(wrapInput('apiKeyRef', 'API Key', 'sk-…'));

  const protocolGroup = el('div', { class: 'form-group' });
  protocolGroup.appendChild(el('label', { class: 'form-label' }, '协议'));
  const protocolSelect = el('select', { class: 'form-input' },
    el('option', { value: 'openai' }, 'OpenAI 兼容'),
    el('option', { value: 'anthropic' }, 'Anthropic Messages'),
    el('option', { value: 'ollama' }, 'Ollama 原生'));
  protocolGroup.appendChild(protocolSelect);
  form.appendChild(protocolGroup);

  const modelGroup = el('div', { class: 'form-group' });
  modelGroup.appendChild(el('label', { class: 'form-label' }, '模型'));
  const modelSelect = el('select', { class: 'form-input' },
    el('option', { value: '' }, '— 请先同步或手动输入 —'));
  const modelInput = el('input', { class: 'form-input', placeholder: '或手动输入模型编码', style: 'display:none;margin-top:8px' });
  modelGroup.appendChild(modelSelect);
  modelGroup.appendChild(modelInput);
  form.appendChild(modelGroup);
  formPanel.appendChild(form);

  // 异步加载渠道模板
  (async function loadPresets() {
    try {
      const presets = await apiRequest(CHANNEL_PRESETS_API);
      syncState.presets = Array.isArray(presets) ? presets : [];
      presetSelect.innerHTML = '';
      presetSelect.appendChild(el('option', { value: '' }, '— 选择渠道模板快捷填充 —'));
      for (const p of syncState.presets) {
        presetSelect.appendChild(el('option', { value: p.id }, p.displayName + '（' + p.protocol + '）'));
      }
    } catch (e) {
      // 模板加载失败不阻塞手动填写
    }
  })();

  presetSelect.onchange = function() {
    const preset = syncState.presets.find(function(p) { return p.id === presetSelect.value; });
    if (!preset) return;
    const displayNameInput = form.querySelector('[data-key="displayName"]');
    if (!displayNameInput.value.trim()) displayNameInput.value = preset.displayName || '';
    form.querySelector('[data-key="providerCode"]').value = preset.id;
    if (preset.baseUrl) form.querySelector('[data-key="baseUrl"]').value = preset.baseUrl;
    protocolSelect.value = preset.protocol || 'openai';
    // 预填推荐模型，便于未同步上游时直接选择
    if (preset.modelSuggestions && preset.modelSuggestions.length) {
      modelSelect.innerHTML = '';
      modelSelect.appendChild(el('option', { value: '' }, '— 推荐模型 —'));
      for (const id of preset.modelSuggestions) {
        modelSelect.appendChild(el('option', { value: id }, id));
      }
    }
    showToast('已填充 ' + preset.displayName + ' 模板，请补全 API Key');
  };

  const actions = el('div', { class: 'settings-panel-actions' });
  const syncBtn = el('button', { class: 'btn btn-secondary', title: '从上游同步可用模型' }, '⟳ 同步模型');
  const toggleInputBtn = el('button', { class: 'btn btn-secondary', title: '切换手动输入' }, '⌨ 手动输入');
  const addBtn = el('button', { class: 'btn btn-primary' }, '添加模型');
  actions.appendChild(syncBtn);
  actions.appendChild(toggleInputBtn);
  actions.appendChild(addBtn);
  formPanel.appendChild(actions);
  body.appendChild(formPanel);

  syncBtn.onclick = async function() {
    const baseUrl = form.querySelector('[data-key="baseUrl"]').value.trim();
    const apiKey = form.querySelector('[data-key="apiKeyRef"]').value.trim();
    const protocol = protocolSelect.value;
    if (!baseUrl) { showToast('请先填写 Base URL'); return; }
    syncBtn.textContent = '同步中…';
    syncBtn.disabled = true;
    try {
      const discovered = await apiRequest(MODEL_DISCOVER_API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ baseUrl, apiKeyRef: apiKey, protocol })
      });
      syncState.discovered = Array.isArray(discovered) ? discovered : [];
      modelSelect.innerHTML = '';
      if (syncState.discovered.length === 0) {
        modelSelect.appendChild(el('option', { value: '' }, '未发现可用模型'));
      } else {
        for (const id of syncState.discovered) {
          modelSelect.appendChild(el('option', { value: id }, id));
        }
        showToast('发现 ' + syncState.discovered.length + ' 个模型');
      }
    } catch (e) {
      showToast('同步失败（不影响已有模型列表）: ' + e.message);
    } finally {
      syncBtn.textContent = '⟳ 同步';
      syncBtn.disabled = false;
    }
  };

  toggleInputBtn.onclick = function() {
    const hidden = modelInput.style.display === 'none';
    modelInput.style.display = hidden ? 'block' : 'none';
    modelSelect.style.display = hidden ? 'none' : 'block';
    toggleInputBtn.textContent = hidden ? '↓' : '⌨';
  };

  addBtn.onclick = async function() {
    const displayName = form.querySelector('[data-key="displayName"]').value.trim();
    const providerCode = form.querySelector('[data-key="providerCode"]').value.trim();
    const baseUrl = form.querySelector('[data-key="baseUrl"]').value.trim();
    const apiKeyRef = form.querySelector('[data-key="apiKeyRef"]').value.trim();
    const modelCode = modelInput.style.display !== 'none' && modelInput.value.trim()
      ? modelInput.value.trim()
      : modelSelect.value;
    if (!displayName) { showToast('请填写渠道名称'); return; }
    if (!modelCode) { showToast('请选择或输入模型'); return; }
      const data = { displayName, providerCode: providerCode || 'default', modelCode, baseUrl, apiKeyRef, protocol: protocolSelect.value, enabled: true };
      addBtn.textContent = '添加中…';
      addBtn.disabled = true;
      try {
        await apiRequest(SETTINGS_API, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data)
        });
        await refreshModelSettingsUI();
        showToast('模型添加成功');
        addBtn.textContent = '添加模型';
        addBtn.disabled = false;
      } catch (e) {
      showToast('添加失败: ' + e.message);
      addBtn.textContent = '添加模型';
      addBtn.disabled = false;
    }
  };

  const listHeader = el('div', { class: 'settings-list-header' });
  listHeader.appendChild(el('div', null,
    el('h2', { class: 'settings-panel-title' }, '已配置模型'),
    el('p', { class: 'settings-panel-desc' }, '配置一个或多个模型渠道，点「使用」切换全局当前渠道。')));
  listHeader.appendChild(el('span', { class: 'settings-count' }, String(state.modelSettings.length)));
  body.appendChild(listHeader);

  const modelGrid = el('div', { class: 'settings-cards' });
  for (const ms of state.modelSettings) {
    modelGrid.appendChild(renderEditableModelCard(ms, syncState));
  }
  if (!state.modelSettings.length) modelGrid.appendChild(el('div', { class: 'empty' }, '还没有模型配置'));
  body.appendChild(modelGrid);
}

function renderEditableModelCard(ms, syncState) {
  const card = el('div', { class: 'model-card' + (ms.enabled === false ? ' model-card-disabled' : '') });

  const top = el('div', { class: 'settings-card-top' });
  const info = el('div', { class: 'settings-card-info' });
  info.appendChild(el('div', { class: 'settings-card-title' }, ms.displayName || ms.channelCode || '默认渠道'));
  info.appendChild(el('div', { class: 'settings-card-sub' }, '模型 · ' + (ms.modelCode || ms.providerCode)));
  top.appendChild(info);
  const badges = el('div', { style: 'display:flex;gap:6px;align-items:center' });
  if (ms.active === true) {
    badges.appendChild(el('span', { class: 'model-badge-tag' }, '使用中'));
  }
  badges.appendChild(el('span', { class: 'model-badge-tag' + (ms.enabled === false ? ' disabled' : '') },
    ms.enabled === false ? '已停用' : '启用中'));
  top.appendChild(badges);

  const actions = el('div', { class: 'settings-card-actions' });
  if (ms.active !== true) {
    const activateBtn = el('button', { class: 'btn btn-primary btn-sm' }, '使用');
    activateBtn.onclick = async function() {
      try {
        await apiRequest(SETTINGS_API + '/active', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ channelCode: ms.channelCode })
        });
        state.currentChannel = ms.channelCode;
        localStorage.setItem('currentChannel', ms.channelCode);
        await refreshModelSettingsUI();
        showToast('已切换为当前模型渠道');
      } catch (e) { showToast('切换失败: ' + e.message); }
    };
    actions.appendChild(activateBtn);
  }
  const toggleBtn = iconBtn(ms.enabled !== false ? '⏸' : '▶', ms.enabled !== false ? '禁用' : '启用', async function() {
    try {
      await apiRequest(SETTINGS_API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          channelCode: ms.channelCode,
          displayName: ms.displayName,
          providerCode: ms.providerCode,
          modelCode: ms.modelCode,
          baseUrl: ms.baseUrl,
          apiKeyRef: ms.apiKeyRef,
          protocol: ms.protocol || 'openai',
          enabled: ms.enabled === false
        })
      });
      await refreshModelSettingsUI();
      showToast(ms.enabled !== false ? '已禁用' : '已启用');
    } catch (e) { showToast('操作失败: ' + e.message); }
  });
  const editBtn = el('button', { class: 'btn btn-secondary btn-sm' }, '编辑');
  editBtn.onclick = function() { toggleEdit(card); };
  const deleteBtn = el('button', { class: 'btn btn-secondary btn-sm' }, '删除');
  deleteBtn.onclick = async function() {
    try {
      await apiRequest(
        SETTINGS_API + '/' + encodeURIComponent(ms.channelCode || 'default'),
        { method: 'DELETE' });
      await refreshModelSettingsUI();
      showToast('已删除');
    } catch (e) { showToast('删除失败: ' + e.message); }
  };
  actions.appendChild(toggleBtn);
  actions.appendChild(editBtn);
  actions.appendChild(deleteBtn);
  const meta = el('div', { class: 'settings-card-meta' });
  meta.appendChild(el('div', null, el('span', { class: 'settings-meta-label' }, '协议'),
    el('span', { class: 'settings-meta-value' }, ms.protocol || 'openai')));
  meta.appendChild(el('div', null, el('span', { class: 'settings-meta-label' }, 'Base URL'),
    el('span', { class: 'settings-meta-value' }, ms.baseUrl || '—')));
  meta.appendChild(el('div', null, el('span', { class: 'settings-meta-label' }, 'API Key'),
    el('span', { class: 'settings-meta-value' }, ms.apiKeyRef ? '已配置' : '未配置')));
  card.appendChild(top);
  card.appendChild(meta);
  card.appendChild(actions);

  const editForm = el('div', { class: 'model-edit-form', style: 'display:none' });
  const editGrid = el('div', { class: 'settings-form-grid' });
  editGrid.appendChild(wrapEditInput('displayName', '渠道名称', ms.displayName || ms.channelCode || ''));
  editGrid.appendChild(wrapEditInput('modelCode', '模型', ms.modelCode || ''));
  editGrid.appendChild(wrapEditInput('baseUrl', 'BaseUrl', ms.baseUrl || ''));
  editGrid.appendChild(wrapEditInput('apiKeyRef', 'API Key', ms.apiKeyRef || ''));
  editForm.appendChild(editGrid);

  const saveBtn = el('button', { class: 'btn btn-primary', style: 'margin-top:12px' }, '保存修改');
  saveBtn.onclick = async function() {
    const inputs = editForm.querySelectorAll('input[data-key]');
    const updated = {};
    inputs.forEach(function(i) { updated[i.dataset.key] = i.value.trim(); });
    if (!updated.modelCode) { showToast('模型不能为空'); return; }
    saveBtn.textContent = '保存中…';
    saveBtn.disabled = true;
    try {
      await apiRequest(SETTINGS_API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          channelCode: ms.channelCode,
          displayName: updated.displayName || ms.displayName || ms.channelCode,
          providerCode: updated.providerCode || ms.providerCode,
          modelCode: updated.modelCode,
          baseUrl: updated.baseUrl || ms.baseUrl,
          apiKeyRef: updated.apiKeyRef || ms.apiKeyRef,
          protocol: ms.protocol || 'openai',
          enabled: ms.enabled !== false
        })
      });
      await refreshModelSettingsUI();
      showToast('保存成功');
    } catch (e) {
      showToast('保存失败: ' + e.message);
      saveBtn.textContent = '保存修改';
      saveBtn.disabled = false;
    }
  };
  const editActions = el('div', { class: 'settings-panel-actions' });
  editActions.appendChild(saveBtn);
  editForm.appendChild(editActions);
  card.appendChild(editForm);

  return card;
}

function toggleEdit(card) {
  const editForm = card.querySelector('.model-edit-form');
  const isOpen = editForm.style.display !== 'none';
  editForm.style.display = isOpen ? 'none' : 'block';
  card.classList.toggle('model-card-editing', !isOpen);
}

function wrapEditInput(key, label, value) {
  const group = el('div', { class: 'form-group' });
  group.appendChild(el('label', { class: 'form-label' }, label));
  group.appendChild(el('input', { class: 'form-input form-input-sm', 'data-key': key, value: value }));
  return group;
}

function wrapInput(key, label, placeholder) {
  const group = el('div', { class: 'form-group' });
  group.appendChild(el('label', { class: 'form-label' }, label));
  group.appendChild(el('input', { class: 'form-input', 'data-key': key, placeholder: placeholder }));
  return group;
}

// ── 插件管理 ───────────────────────────────
function renderPluginSettings(body, footer) {
  const query = (state.pluginSearch || '').trim().toLowerCase();
  const all = (state.plugins || []).filter(function(plugin) {
    return plugin.status !== 'UNINSTALLED';
  });
  const list = query ? all.filter(function(plugin) {
    return [plugin.pluginId, plugin.displayName, plugin.runtimeType, plugin.status]
      .some(function(value) { return String(value || '').toLowerCase().includes(query); });
  }) : all;

  const summary = el('div', { class: 'settings-metrics' });
  const metrics = [
    ['已安装', list.length],
    ['运行中', list.filter(function(plugin) { return plugin.status === 'ACTIVE'; }).length],
    ['已停用', list.filter(function(plugin) { return plugin.status === 'DISABLED'; }).length],
    ['失败', list.filter(function(plugin) { return plugin.status === 'FAILED'; }).length],
  ];
  for (const [label, value] of metrics) {
    summary.appendChild(el('div', { class: 'settings-metric' },
      el('span', { class: 'settings-metric-label' }, label),
      el('span', { class: 'settings-metric-value' }, String(value))
    ));
  }
  body.appendChild(summary);

  renderPluginInstall(body);

  const listPanel = el('section', { class: 'settings-panel' });
  const header = el('div', { class: 'settings-list-header' });
  header.appendChild(el('div', null,
    el('h2', { class: 'settings-panel-title' }, '已安装插件'),
    el('p', { class: 'settings-panel-desc' }, '点击“配置”维护键值参数。')));
  header.appendChild(el('span', { class: 'settings-count' }, String(list.length)));
  const search = el('input', {
    class: 'settings-search',
    placeholder: '搜索插件名称、ID 或状态',
    value: state.pluginSearch || ''
  });
  search.oninput = function() { state.pluginSearch = search.value; };
  search.onchange = render;
  header.appendChild(search);
  listPanel.appendChild(header);
  if (list.length === 0) {
    listPanel.appendChild(el('div', { class: 'empty' }, query ? '没有匹配的插件' : '暂无插件'));
  }
  const pluginGrid = el('div', { class: 'settings-cards' });
  for (const plugin of list) {
    pluginGrid.appendChild(renderPluginCard(plugin));
  }
  listPanel.appendChild(pluginGrid);
  body.appendChild(listPanel);
  const selected = list.find(function(plugin) { return plugin.pluginId === state.selectedPluginId; });
  if (selected) body.appendChild(renderPluginConfigEditor(selected));
}

function renderPluginInstall(body) {
  const panel = el('section', { class: 'settings-panel' });
  panel.appendChild(el('div', { class: 'settings-panel-head' },
    el('div', null,
      el('h2', { class: 'settings-panel-title' }, '安装插件'),
      el('p', { class: 'settings-panel-desc' }, '选择本地 JAR，确认后即可直接安装并启用。')),
    el('span', { class: 'settings-pill' }, 'Auto Analyze')
  ));

  const modeTabs = el('div', { class: 'settings-mode-tabs' });
  const modes = [
    { id: 'jar', label: '选择 JAR' },
    { id: 'maven', label: 'POM 配置' },
  ];
  modes.forEach(function(mode) {
    modeTabs.appendChild(el('button', {
      class: 'settings-mode-tab' + ((state.pluginInstallMode || 'jar') === mode.id ? ' active' : ''),
      onclick: function() { state.pluginInstallMode = mode.id; render(); }
    }, mode.label));
  });
  panel.appendChild(modeTabs);

  const mode = state.pluginInstallMode || 'jar';
  if (mode === 'jar') {
    const drop = el('label', { class: 'settings-drop' });
    const input = el('input', { type: 'file', accept: '.jar', style: 'display:none' });
    const dropText = el('div', { class: 'settings-drop-text' },
      el('strong', null, '点击选择 JAR 包'),
      el('span', null, '系统会读取 META-INF/plugin.yaml，识别后直接使用'));
    drop.appendChild(input);
    drop.appendChild(dropText);
    panel.appendChild(drop);
    input.onchange = async function() {
      const file = input.files && input.files[0];
      if (!file) return;
      state.pluginCandidate = null;
      state.pluginAnalyzeMessage = '分析中…';
      render();
      try {
        const response = await fetch(PLUGIN_API + '/analyze-jar', { method: 'POST', body: (() => { const form = new FormData(); form.append('file', file); return form; })() });
        const payload = await response.json();
        if (!response.ok || payload.code !== '00000') throw new Error(payload.info || ('HTTP ' + response.status));
        state.pluginCandidate = payload.data;
        state.pluginAnalyzeMessage = null;
      } catch (e) {
        state.pluginCandidate = null;
        state.pluginAnalyzeMessage = e.message;
      }
      render();
    };
  } else {
    const textarea = el('textarea', {
      class: 'settings-textarea',
      placeholder: '<dependency>\n  <groupId>com.example</groupId>\n  <artifactId>example-plugin</artifactId>\n  <version>1.0.0</version>\n</dependency>'
    });
    textarea.value = state.pluginPomXml || '';
    textarea.oninput = function() { state.pluginPomXml = textarea.value; };
    panel.appendChild(textarea);
    const actions = el('div', { class: 'settings-panel-actions' });
    const analyzeBtn = el('button', { class: 'btn btn-secondary' }, '分析 POM');
    analyzeBtn.onclick = async function() {
      analyzeBtn.textContent = '分析中…';
      analyzeBtn.disabled = true;
      try {
        const data = await apiRequest(PLUGIN_API + '/analyze-maven', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ pomXml: textarea.value })
        });
        state.pluginCandidates = Array.isArray(data) ? data : [];
        const valid = state.pluginCandidates.find(function(candidate) { return candidate.valid; });
        state.pluginCandidate = valid || null;
      } catch (e) {
        showToast('POM 分析失败: ' + e.message);
      } finally {
        analyzeBtn.textContent = '分析 POM';
        analyzeBtn.disabled = false;
        render();
      }
    };
    actions.appendChild(analyzeBtn);
    panel.appendChild(actions);
  }

  const selectedCandidate = state.pluginCandidate;
  const candidates = state.pluginCandidates || [];
  if (candidates.length > 1) {
    const candidateList = el('div', { class: 'settings-candidate-list' });
    candidates.forEach(function(candidate, index) {
      candidateList.appendChild(renderCandidateCard(candidate, function() {
        state.pluginCandidate = candidate.valid ? candidate : null;
        state.selectedCandidateIndex = index;
        render();
      }));
    });
    panel.appendChild(candidateList);
  } else if (selectedCandidate || state.pluginAnalyzeMessage) {
    panel.appendChild(renderCandidateCard(selectedCandidate, null, state.pluginAnalyzeMessage));
  }

  if (selectedCandidate && selectedCandidate.valid) {
    const installActions = el('div', { class: 'settings-panel-actions' });
    const installBtn = el('button', { class: 'btn btn-primary' }, '安装并启用');
    installBtn.onclick = async function() {
      installBtn.textContent = '安装中…';
      installBtn.disabled = true;
      try {
        await apiRequest(PLUGIN_API + '/install', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pluginId: selectedCandidate.pluginId,
            displayName: selectedCandidate.displayName,
            pluginVersion: selectedCandidate.pluginVersion,
            runtimeType: selectedCandidate.runtimeType,
            sourcePath: selectedCandidate.sourcePath,
            entrypoint: selectedCandidate.entrypoint
          })
        });
        await apiRequest(PLUGIN_API + '/activate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ pluginId: selectedCandidate.pluginId })
        });
        state.installedPluginId = selectedCandidate.pluginId;
        state.pluginCandidate = null;
        state.pluginCandidates = [];
        await loadPlugins();
        render();
        showToast('插件已安装并启用');
      } catch (e) {
        showToast('安装失败: ' + e.message);
      } finally {
        installBtn.textContent = '安装并启用';
        installBtn.disabled = false;
      }
    };
    installActions.appendChild(installBtn);
    panel.appendChild(installActions);
  }

  body.appendChild(panel);
  body.appendChild(renderPluginUsageGuide(state.installedPluginId));
}

function renderCandidateCard(candidate, onClick, message) {
  const valid = Boolean(candidate && candidate.valid);
  const card = el('div', { class: 'candidate-card' + (valid ? ' valid' : ' invalid') + (onClick ? ' clickable' : '') });
  if (onClick) card.onclick = onClick;
  card.appendChild(el('div', { class: 'candidate-head' },
    el('div', null,
      el('div', { class: 'candidate-title' }, candidate ? (candidate.displayName || candidate.artifactId || '未识别') : '自动识别'),
      el('div', { class: 'candidate-sub' }, candidate ? [candidate.pluginId, candidate.pluginVersion, candidate.sourcePath].filter(Boolean).join(' · ') : '—')
    ),
    el('span', { class: 'candidate-tag ' + (valid ? 'ok' : 'bad') }, valid ? '插件' : '无效')
  ));
  if (candidate && valid) {
    card.appendChild(el('div', { class: 'candidate-meta' },
      el('div', null, el('span', { class: 'candidate-label' }, '入口'),
        el('span', null, candidate.entrypoint || 'Java SPI 自动发现')),
      el('div', null, el('span', { class: 'candidate-label' }, '作者'),
        el('span', null, candidate.author || '—')),
      el('div', null, el('span', { class: 'candidate-label' }, '说明'),
        el('span', null, candidate.description || '—'))
    ));
  } else {
    card.appendChild(el('div', { class: 'candidate-message' }, message || (candidate && candidate.message) || '请选择有效的插件文件'));
  }
  return card;
}

function renderPluginUsageGuide(installedPluginId) {
  const panel = el('section', { class: 'settings-panel usage-panel' });
  panel.appendChild(el('div', { class: 'settings-panel-head' },
    el('div', null,
      el('h2', { class: 'settings-panel-title' }, '安装后如何使用'),
      el('p', { class: 'settings-panel-desc' }, '插件会作为 Agent 工具自动注册，无需手动编写调用代码。'))
  ));
  const lines = installedPluginId
    ? [
        '插件 ' + installedPluginId + ' 已激活。',
        '工具注册名格式：plugin__' + installedPluginId + '__<toolName>。',
        '在对话中直接描述目标，例如“查一下北京天气”，Agent 会自动选择插件工具。',
        '需要运行参数时，回到插件卡片点“配置”。',
      ]
    : [
        '插件激活后，能力说明会进入 Agent 系统提示词。',
        '每个工具注册为 plugin__<插件ID>__<toolName>。',
        '对话中直接描述目标即可；Agent 会自动选择匹配工具。',
        '插件参数通过卡片上的“配置”维护。',
      ];
  panel.appendChild(el('ul', { class: 'usage-list' }, lines.map(function(line) { return el('li', null, line); })));
  return panel;
}

function renderPluginCard(p) {
  const card = el('div', { class: 'plugin-card settings-plugin-card' + (p.pluginId === state.selectedPluginId ? ' selected' : '') });
  const top = el('div', { class: 'settings-card-top' });
  const info = el('div', { class: 'settings-card-info' });
  info.appendChild(el('div', { class: 'settings-card-title' }, p.displayName || p.pluginId));
  const subParts = [p.pluginId, p.pluginVersion && ('v' + p.pluginVersion)].filter(Boolean);
  info.appendChild(el('div', { class: 'settings-card-sub' }, subParts.join(' · ') || ' '));
  top.appendChild(info);
  top.appendChild(el('div', { class: 'settings-card-badges' },
    el('span', { class: 'plugin-status-tag ' + pluginStatusClass(p.status) }, pluginStatusLabel(p.status)),
    el('span', { class: 'plugin-runtime-tag' }, p.runtimeType || '—')
  ));

  const actions = el('div', { class: 'settings-card-actions' });
  const configBtn = el('button', { class: 'btn btn-secondary btn-sm' }, '配置');
  configBtn.onclick = function(event) { event.stopPropagation(); selectPlugin(p.pluginId); };
  actions.appendChild(configBtn);
  if (p.status === 'ACTIVE' || p.status === 'REGISTERED') {
    const disableBtn = el('button', { class: 'btn btn-secondary btn-sm' }, '停用');
    disableBtn.onclick = async function(event) {
      event.stopPropagation();
      try {
        await apiRequest(PLUGIN_API + '/' + p.pluginId + '/disable', { method: 'POST' });
        await loadPlugins(); render(); showToast('已停用');
      } catch (e) { showToast('操作失败: ' + e.message); }
    };
    actions.appendChild(disableBtn);
  } else if (p.status === 'DISABLED' || p.status === 'FAILED') {
    const enableBtn = el('button', { class: 'btn btn-secondary btn-sm' }, '启用');
    enableBtn.onclick = async function(event) {
      event.stopPropagation();
      try {
        await apiRequest(PLUGIN_API + '/activate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ pluginId: p.pluginId })
        });
        await loadPlugins(); render(); showToast('已启用');
      } catch (e) { showToast('操作失败: ' + e.message); }
    };
    actions.appendChild(enableBtn);
  }
  const uninstallBtn = el('button', { class: 'btn btn-danger btn-sm' }, '卸载');
  uninstallBtn.onclick = async function(event) {
    event.stopPropagation();
    try {
      await apiRequest(PLUGIN_API + '/' + p.pluginId + '/uninstall', { method: 'POST' });
      if (state.selectedPluginId === p.pluginId) state.selectedPluginId = null;
      await loadPlugins(); render(); showToast('已卸载');
    } catch (e) { showToast('卸载失败: ' + e.message); }
  };
  actions.appendChild(uninstallBtn);

  card.appendChild(top);
  card.appendChild(el('div', { class: 'settings-card-meta' },
    el('div', null, el('span', { class: 'settings-meta-label' }, '包路径'),
      el('span', { class: 'settings-meta-value' }, p.sourcePath || '—')),
    el('div', null, el('span', { class: 'settings-meta-label' }, '入口'),
      el('span', { class: 'settings-meta-value' }, p.entrypoint || '—'))
  ));
  card.appendChild(actions);
  return card;
}

function selectPlugin(pluginId) {
  state.selectedPluginId = pluginId;
  loadPluginConfig(pluginId).then(render);
}

function renderPluginConfigEditor(plugin) {
  const pluginId = plugin.pluginId;
  const configs = state.pluginConfigs[pluginId];
  const panel = el('section', { class: 'settings-panel plugin-config-panel' });
  panel.appendChild(el('div', { class: 'settings-panel-head' },
    el('div', null,
      el('h2', { class: 'settings-panel-title' }, '插件配置 · ' + (plugin.displayName || pluginId)),
      el('p', { class: 'settings-panel-desc' }, '键值参数会注入插件 configure(context)，保存后可用。')),
    el('button', { class: 'btn btn-secondary btn-sm', onclick: function() { state.selectedPluginId = null; render(); } }, '收起')
  ));

  if (!Array.isArray(configs)) {
    panel.appendChild(el('div', { class: 'empty' }, '配置加载中…'));
    return panel;
  }
  const rows = el('div', { class: 'plugin-config-rows' });
  if (configs.length === 0) rows.appendChild(el('div', { class: 'empty' }, '暂无配置，点击“添加配置”。'));
  configs.forEach(function(item, index) {
    const row = el('div', { class: 'plugin-config-row' });
    const keyInput = el('input', { class: 'form-input', placeholder: 'key', value: item.key || '' });
    const valueInput = el('input', { class: 'form-input', placeholder: 'value', value: item.value || '' });
    keyInput.oninput = function() { item.key = keyInput.value; };
    valueInput.oninput = function() { item.value = valueInput.value; };
    row.appendChild(keyInput);
    row.appendChild(valueInput);
    const removeBtn = el('button', { class: 'btn btn-danger btn-sm' }, '删除');
    removeBtn.onclick = function() { configs.splice(index, 1); render(); };
    row.appendChild(removeBtn);
    rows.appendChild(row);
  });
  panel.appendChild(rows);

  const actions = el('div', { class: 'settings-panel-actions' });
  const addBtn = el('button', { class: 'btn btn-secondary' }, '添加配置');
  addBtn.onclick = function() { configs.push({ key: '', value: '' }); render(); };
  const saveBtn = el('button', { class: 'btn btn-primary' }, '保存配置');
  saveBtn.onclick = async function() {
    const payload = configs.filter(function(item) { return item.key && item.key.trim(); });
    saveBtn.textContent = '保存中…';
    saveBtn.disabled = true;
    try {
      await apiRequest(PLUGIN_API + '/' + encodeURIComponent(pluginId) + '/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ configs: payload })
      });
      await loadPluginConfig(pluginId);
      render();
      showToast('配置已保存');
    } catch (e) {
      showToast('配置保存失败: ' + e.message);
    } finally {
      saveBtn.textContent = '保存配置';
      saveBtn.disabled = false;
    }
  };
  actions.appendChild(addBtn);
  actions.appendChild(saveBtn);
  panel.appendChild(actions);
  return panel;
}

function iconBtn(glyph, title, handler) {
  return el('button', { class: 'model-card-btn', title: title, onclick: handler }, glyph);
}

function pluginStatusClass(status) {
  if (status === 'ACTIVE') return 'is-active';
  if (status === 'FAILED') return 'is-failed';
  if (status === 'DISABLED') return 'is-disabled';
  return 'is-idle';
}

function pluginStatusLabel(status) {
  const map = { REGISTERED: '已注册', ACTIVE: '运行中', FAILED: '失败', DISABLED: '已禁用', UNINSTALLED: '已卸载' };
  return map[status] || status || '未知';
}

// ── 预设管理 ───────────────────────────────
function renderPresetSettings(body, footer) {
  body.appendChild(el('div', { class: 'form-label' }, 'Agent 预设'));
  for (const preset of state.agentPresets) {
    const card = el('div', { class: 'model-card' });
    card.appendChild(el('div', { style: 'font-weight:500;font-size:13px' }, preset.name));
    card.appendChild(el('div', { style: 'font-size:11px;color:var(--text-muted);margin-top:2px' }, preset.desc));
    if (preset.systemPrompt) {
      card.appendChild(el('div', { style: 'font-size:11px;color:var(--text-muted);margin-top:4px;font-family:var(--mono)' }, 'system: ' + preset.systemPrompt.slice(0, 80)));
    }
    body.appendChild(card);
  }
}

// ── 权限预设 ───────────────────────────────
function renderPermissionSettings(body, footer) {
  body.appendChild(el('div', { class: 'form-label' }, '权限预设'));
  for (const preset of state.permissionPresets) {
    const card = el('div', { class: 'permission-preset-card' });
    card.appendChild(el('div', { style: 'font-weight:500;font-size:13px' }, preset.name));
    card.appendChild(el('div', { style: 'font-size:11px;color:var(--text-muted);margin-top:2px' }, preset.desc));
    const permList = el('div', { class: 'permission-list' });
    for (const [k, v] of Object.entries(preset.permissions)) {
      permList.appendChild(el('span', { class: 'permission-tag ' + (v ? 'allowed' : 'denied') }, k + ': ' + (v ? '✓' : '✗')));
    }
    card.appendChild(permList);
    body.appendChild(card);
  }
}

// ── Toast ──────────────────────────────────
function showToast(msg) {
  const existing = $('.toast');
  if (existing) existing.remove();
  const toast = el('div', { class: 'toast' }, msg);
  document.body.appendChild(toast);
  setTimeout(function() { toast.remove(); }, 2500);
}

// ── 会话操作 ───────────────────────────────
function newChat() {
  const sess = new ChatSession('agent-' + Date.now());
  sess.workspace = state.currentWorkspace || '';
  state.sessions.unshift(sess);
  state.currentSessionId = sess.agentId;
  state.activeMainTab = 'chat';
  render();
  saveSessionsToStorage();
  const input = $('#composer-input');
  if (input) { input.focus(); autoResize({ target: input }); }
}

function quickSend(text) {
  const input = $('#composer-input');
  if (input) { input.value = text; autoResize({ target: input }); sendMessage(); }
}

function selectSession(agentId) {
  state.currentSessionId = agentId;
  state.activeMainTab = 'chat';
  render();
  saveSessionsToStorage();
  const input = $('#composer-input');
  if (input) { input.focus(); autoResize({ target: input }); }
  setTimeout(scrollToBottom, 50);
  // v30: 历史会话懒加载消息
  const sess = getSession(agentId);
  if (sess && sess.messages.length === 0 && sess.sessionId) {
    loadHistoryMessages(sess);
  }
}

function autoResize(e) {
  const ta = e && e.target ? e.target : (typeof this === 'object' && this.tagName ? this : null);
  if (!ta || !ta.style) return;
  ta.style.height = 'auto';
  ta.style.height = Math.min(ta.scrollHeight, 336) + 'px';
}

// 确保消息列表滚到底部
function scrollToBottom() {
  const messagesContainer = $('.messages');
  if (messagesContainer) {
    messagesContainer.scrollTo({ top: messagesContainer.scrollHeight, behavior: 'smooth' });
  }
}

function formatTime(iso) {
  try {
    const d = new Date(iso);
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    return h + ':' + m;
  } catch (_) { return ''; }
}

function formatSessionAge(iso) {
  try {
    const target = new Date(iso);
    const now = new Date();
    const targetStart = new Date(target.getFullYear(), target.getMonth(), target.getDate()).getTime();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const days = Math.max(0, Math.floor((todayStart - targetStart) / 86400000));
    if (days === 0) return '今天';
    return days + '天';
  } catch (_) { return ''; }
}

// P2: 统一的时间戳格式化（用于消息 meta 和导出）
function formatTimestamp(iso) {
  try {
    const d = new Date(iso);
    const M = String(d.getMonth() + 1).padStart(2, '0');
    const D = String(d.getDate()).padStart(2, '0');
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    return M + '-' + D + ' ' + h + ':' + m;
  } catch (_) { return ''; }
}

function estimateTokens(text) {
  if (!text) return 0;
  return Math.ceil(text.length / 3);
}

function copyToClipboard(text) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).catch(function() {});
  } else {
    const ta = document.createElement('textarea');
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); } catch (_) {}
    document.body.removeChild(ta);
  }
}

function copyCodeBlock(btn) {
  const code = btn.parentElement.querySelector('code');
  if (code) copyToClipboard(code.textContent);
  btn.textContent = '✓';
  setTimeout(function() { btn.textContent = '复制'; }, 2500);
}

// ═══════════════════════════════════════════
// 消息发送
// ═══════════════════════════════════════════
async function sendMessage(overrideText) {
  // overrideText may be a MouseEvent from onclick — coerce to string
  if (overrideText && typeof overrideText !== 'string') overrideText = undefined;
  let sess = getCurrentSession();
  if (!sess) {
    // Save input text before newChat() resets the DOM
    const savedText = overrideText || ($('#composer-input')?.value?.trim() || '');
    newChat();
    return sendMessage(savedText || undefined);
  }
  if (sess.sending) {
    // v11: 排队消息
    const text = overrideText || ($('#composer-input')?.value?.trim() || '');
    if (text) {
      sess.queue.push(text);
      const input = $('#composer-input');
      if (input) input.value = '';
      render();
      showToast('消息已排队');
    }
    return;
  }

  const input = $('#composer-input');
  const text = overrideText || (input?.value?.trim() || '');
  if (!text) return;

  // v11: 斜杠命令
  if (text.startsWith('/') && !overrideText) {
    if (input) input.value = '';
    const handled = await executeSlashCommand(text, sess);
    if (handled) return;
  }

  if (input && !overrideText) input.value = '';
  if (input) autoResize({ target: input });

  // 设置标题
  if (sess.title === '新对话') {
    sess.title = text.slice(0, 30);
  }
  sess.lastMessage = text.slice(0, 40);

  // v11: 附件处理
  const msgAttachments = sess.pendingAttachments || [];
  sess.pendingAttachments = [];

  sess.messages.push({ role: 'user', content: text, time: formatTime(new Date().toISOString()), attachments: msgAttachments });

  // 图片附件随本次请求发给后端（sendWithRetry 里读取 lastRequestAttachments）
  sess.lastRequestAttachments = msgAttachments;

  let assistantMsg = { role: 'assistant', content: '', streaming: true, meta: null };
  sess.messages.push(assistantMsg);

  // v28: 轨迹日志 — 记录用户输入
  trajPush(sess, 'user', text, { input: estimateTokens(text) });

  saveSessionsToStorage();

  sess.sending = true;
  sess.streaming = true;
  sess.error = null;
  sess.waitStartTime = Date.now();
  startWaitTimer(sess);
  render();

  const startTime = Date.now();
  let firstChunkTime = null;
  let fullContent = '';

  await sendWithRetry(sess, text, assistantMsg, startTime, firstChunkTime, fullContent, 0);

  // v11: 处理排队消息
  if (sess.queue && sess.queue.length > 0 && !sess.sending) {
    const nextMsg = sess.queue.shift();
    sendMessage(nextMsg);
  }
}

function startWaitTimer(sess) {
  stopWaitTimer(sess);
  sess.waitTimer = setInterval(function() {
    const el = $('#wait-text');
    if (el && sess.waitStartTime) {
      const elapsedSeconds = (Date.now() - sess.waitStartTime) / 1000;
      const elapsed = formatElapsedDuration(elapsedSeconds);
      el.textContent = elapsedSeconds > 15
        ? '正在思考… ' + elapsed + '（模型响应较慢，请稍候）'
        : '正在思考… ' + elapsed;
    }
  }, 100);
}

function stopWaitTimer(sess) {
  if (sess && sess.waitTimer) {
    clearInterval(sess.waitTimer);
    sess.waitTimer = null;
  }
}

// ═══════════════════════════════════════════
// SSE 请求 + 自动重试
// ═══════════════════════════════════════════
async function sendWithRetry(sess, text, assistantMsg, startTime, firstChunkTime, fullContent, retryCount) {
  try {
    const requestBody = { agentId: sess.agentId, message: text };
    if (state.currentWorkspace) requestBody.cwd = state.currentWorkspace;
    if (state.currentChannel) requestBody.channelCode = state.currentChannel;
    if (sess.approvalMode) requestBody.approvalMode = sess.approvalMode;
    // 图片附件：随请求发送 data URL 列表，后端转 ImageBlock 供多模态模型识别
    const imgAttachments = (sess.lastRequestAttachments || []).filter(function(a) { return a.type === 'image' && a.url; });
    if (imgAttachments.length > 0) requestBody.images = imgAttachments.map(function(a) { return a.url; });

    sess.abortController = new AbortController();
    const timeoutId = setTimeout(function() { sess.abortController.abort(); }, SSE_TIMEOUT_MS);

    const response = await fetch(API + '/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody),
      signal: sess.abortController.signal,
    });

    clearTimeout(timeoutId);
    startApprovalPolling(sess);

    if (!response.ok) {
      const errText = await response.text().catch(function() { return ''; });
      throw new Error(translateError('HTTP ' + response.status + ': ' + (errText || response.statusText)));
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let receivedChunks = false;
    // P0: 跨 reader.read() 持久化 eventType。
    // 修复：当 TCP 把 `event:chunk\n` 行切到上一个 read、而 `data:...` 行留在下一个 read 时，
    // 下个 read 顶部 `let eventType = ''` 会让 data 行失去对应的 event 类型，
    // 导致 chunk / done / error 等事件被静默丢弃，前端累积 fullContent 时丢字、最终渲染残缺。
    // 用 lastEventType 在 read 之间保留，只有见到新的 event: 行才覆盖。
    let lastEventType = '';

    while (true) {
      const result = await reader.read();
      if (result.done) break;

      buffer += decoder.decode(result.value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      // P0: 用上次的 eventType 兜底（仅当本次 read 内没出现新的 event: 行才用）。
      // 这里不能直接覆盖 lastEventType — 让下面的 for 循环见到 event: 行再更新。
      let eventType = lastEventType;
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.slice(6).trim();
          // P0: 同步到 lastEventType，让跨 read 的 data 行也能找到正确的类型
          lastEventType = eventType;
        } else if (line.startsWith('data:')) {
          const dataStr = line.slice(5).trim();
          if (!dataStr) continue;

          try {
            const data = JSON.parse(dataStr);

            if (eventType === 'meta') {
              sess.currentStage = '连接模型';
              // v28: 轨迹日志 — 记录请求参数
              trajPush(sess, 'system', '请求: channel=' + (state.currentChannel || 'active') + ', agentId=' + (data.agentId || sess.agentId) + (state.currentWorkspace ? ', workspace=' + state.currentWorkspace : ''), {});
            } else if (eventType === 'chunk') {
              receivedChunks = true;
              stopWaitTimer(sess);
              sess.currentStage = '生成回复';
              if (firstChunkTime === null) firstChunkTime = Date.now();
              var deltaText = data.content || '';
              // 向后兼容旧的字符串标记方式（\u0001TOOL_CALL:xxx\u0001）
              if (deltaText.indexOf('\u0001TOOL_CALL:') === 0) {
                var oldToolName = deltaText.replace(/^\u0001TOOL_CALL:/, '').replace(/\u0001$/, '');
                finishAssistantMessage(sess, assistantMsg, fullContent);
                insertToolCard(sess, { toolName: oldToolName, args: {}, status: 'running' });
                assistantMsg = newStreamingAssistant();
                fullContent = '';
              } else {
                fullContent += deltaText;
                assistantMsg.content = fullContent;
                if (assistantMsg.blocks && assistantMsg.blocks.length > 0) {
                  var textBlock = assistantMsg.blocks[assistantMsg.blocks.length - 1];
                  if (textBlock.kind !== 'text') {
                    textBlock = { kind: 'text', text: '' };
                    assistantMsg.blocks.push(textBlock);
                  }
                  textBlock.text = fullContent;
                }
                if (state.currentSessionId === sess.agentId) {
                  updateStreamingMessage(assistantMsg);
                }
              }
            } else if (eventType === 'reasoning') {
              receivedChunks = true;
              stopWaitTimer(sess);
              sess.currentStage = '思考中';
              const reasoningText = data.content || '';
              assistantMsg.reasoning = (assistantMsg.reasoning || '') + reasoningText;
              if (!assistantMsg.blocks) assistantMsg.blocks = [];
              var reasoningBlock = assistantMsg.blocks[assistantMsg.blocks.length - 1];
              if (!reasoningBlock || reasoningBlock.kind !== 'reasoning') {
                reasoningBlock = { kind: 'reasoning', text: '' };
                assistantMsg.blocks.push(reasoningBlock);
              }
              reasoningBlock.text = assistantMsg.reasoning;
              if (state.currentSessionId === sess.agentId) updateStreamingMessage(assistantMsg);
            } else if (eventType === 'step_break') {
              // 工具调用事件（独立 SSE 事件）。
              // 完成当前 assistant 消息 → 插入工具卡片 → 创建新的 streaming 消息
              receivedChunks = true;
              stopWaitTimer(sess);
              sess.currentStage = '执行工具 · ' + (data.toolName || 'unknown');
              if (firstChunkTime === null) firstChunkTime = Date.now();
              // v29: 轨迹 — 记录刚结束的中间 assistant 步骤文本
              if (fullContent) {
                trajPush(sess, 'message', fullContent, {
                  duration: firstChunkTime ? ((Date.now() - firstChunkTime) / 1000).toFixed(1) + 's' : null,
                  tokens: estimateTokens(fullContent),
                  output: estimateTokens(fullContent),
                  t0: firstChunkTime || startTime,
                  t1: Date.now(),
                  status: 'step',
                });
              }
              // v29: 轨迹 — 记录工具调用（running，done 时回填结果）
              trajPush(sess, 'tool', '', {
                toolName: data.toolName || 'unknown',
                status: 'running',
                t0: Date.now(),
              });
              finishAssistantMessage(sess, assistantMsg, fullContent);
              insertToolCard(sess, { toolName: data.toolName, args: data.args, status: data.status || 'running' });
              assistantMsg = newStreamingAssistant();
              // P1: 关键修复 — 把新 assistant 消息 push 到 sess.messages，
              // 否则后续 chunks 走 updateStreamingMessage() 时会命中 DOM 里
              // 上一轮的旧 assistant 气泡，导致第二轮正文覆盖第一轮气泡、
              // 且第二轮气泡根本没被 render() 创建出来。
              sess.messages.push(assistantMsg);
              fullContent = '';
              // P1: 重置 updateStreamingMessage 的缓存，避免旧值干扰新消息的流式更新
              updateStreamingMessage._lastHtml = null;
              updateStreamingMessage._lastTime = null;
              if (state.currentSessionId === sess.agentId) render();
            } else if (eventType === 'tool_result') {
              receivedChunks = true;
              stopWaitTimer(sess);
              const toolMsg = sess.messages.find(function(m) {
                return m.role === 'tool' && (m.callId || '') === (data.callId || '');
              });
              if (toolMsg) {
                toolMsg.expanded = false;
                toolMsg.result = data.result;
                toolMsg.status = data.status || 'success';
                toolMsg.duration = data.durationMs != null
                  ? (data.durationMs / 1000).toFixed(1) + 's'
                  : toolMsg.duration;
              }
              sess.currentStage = (data.status === 'error' ? '工具失败 · ' : '工具完成 · ')
                + (data.toolName || 'unknown');
              if (state.currentSessionId === sess.agentId) render();
            } else if (eventType === 'finish') {
              receivedChunks = true;
              stopWaitTimer(sess);
              stopApprovalPolling(sess);
              sess.currentStage = null;
              assistantMsg.streaming = false;
              stopStreamReveal(assistantMsg);
              if (fullContent) assistantMsg.content = fullContent;
              const finishTime = Date.now();
              const finishDuration = finishTime - startTime;
              const finishFirstToken = firstChunkTime ? firstChunkTime - startTime : 0;
              assistantMsg.meta = {
                time: new Date().toISOString(),
                duration: (finishDuration / 1000).toFixed(1) + 's',
                firstToken: finishFirstToken > 0 ? (finishFirstToken / 1000).toFixed(1) + 's' : '-',
                tokens: estimateTokens(fullContent),
              };
              sess.stats.turns++;
              sess.stats.steps++;
              sess.stats.llmTimeMs += finishDuration;
              sess.stats.firstTokenMs += finishFirstToken;
              sess.stats.outputTokens += estimateTokens(fullContent);
              sess.stats.inputTokens += estimateTokens(text);
              sess.sending = false;
              sess.streaming = false;
              sess.abortController = null;
              if (state.currentSessionId === sess.agentId) {
                updateStreamingMessage._lastHtml = null;
                render();
                updateSidebarStatus();
              } else {
                updateSidebarStatus();
              }
              await sleep(0);
            } else if (eventType === 'done') {
              receivedChunks = true;
              stopWaitTimer(sess);
              stopApprovalPolling(sess);
              sess.currentStage = null;
              // done 事件：优先用 extractAssistantTextFromDone 提取，若为空则保留 chunk 累积的内容
              const extracted = extractAssistantTextFromDone(data);
              if (extracted) fullContent = extracted;
              assistantMsg.content = fullContent;

              // v30: 记录后端 sessionId，用于历史会话消息回查
              if (data.sessionId && !sess.sessionId) {
                sess.sessionId = data.sessionId;
                saveSessionsToStorage();
              }

              // Auto-fallback: if LLM call failed and we haven't already fallen back, retry with default model
              const fallbackChannel = getPreferredChannel();
              if (fullContent && fullContent.indexOf('LLM 调用失败') !== -1 && retryCount < MAX_RETRIES && state.currentChannel !== fallbackChannel) {
                showToast('模型渠道 ' + state.currentChannel + ' 不可用，自动切换到 ' + fallbackChannel);
                state.currentChannel = fallbackChannel;
                localStorage.setItem('currentChannel', fallbackChannel);
                await sleep(RETRY_DELAY_MS);
                return sendWithRetry(sess, text, assistantMsg, startTime, firstChunkTime, '', retryCount + 1);
              }

              const endTime = Date.now();
              const duration = endTime - startTime;
              const firstToken = firstChunkTime ? firstChunkTime - startTime : 0;

              assistantMsg.streaming = false;
              stopStreamReveal(assistantMsg);
              assistantMsg.meta = {
                time: new Date().toISOString(),
                duration: (duration / 1000).toFixed(1) + 's',
                firstToken: firstToken > 0 ? (firstToken / 1000).toFixed(1) + 's' : '-',
                tokens: estimateTokens(fullContent),
              };

              if (!assistantMsg.meta) {
                sess.stats.turns++;
                sess.stats.steps++;
                sess.stats.llmTimeMs += duration;
                sess.stats.firstTokenMs += firstToken;
                sess.stats.outputTokens += estimateTokens(fullContent);
                sess.stats.inputTokens += estimateTokens(text);
              }

              // v28: 轨迹日志 — 记录模型回复（v29: 带精确 t0/t1 驱动时间线）
              trajPush(sess, 'message', fullContent, {
                duration: (duration / 1000).toFixed(1) + 's',
                tokens: estimateTokens(fullContent),
                output: estimateTokens(fullContent),
                input: estimateTokens(text),
                status: data.status || 'completed',
                t0: startTime,
                t1: endTime,
              });

              sess.sending = false;
              sess.streaming = false;
              sess.abortController = null;

              // 立即释放输入区，避免收尾数据处理阻塞停止按钮。
              if (state.currentSessionId === sess.agentId) {
                updateStreamingMessage._lastHtml = null;
                render();
                updateSidebarStatus();
              } else {
                updateSidebarStatus();
              }

              await sleep(0);

              // v11: 解析 done 中的工具调用、计划等信息
              if (data.messages) {
                parseDoneExtras(sess, data);
                // 从 done 事件构建轨迹
                buildTrajectoryFromDone(sess, data);
                // v29: 回填 running 状态工具条目的结果/耗时
                trajSettleTools(sess, data.messages);
              }
              saveSessionsToStorage();

              // Sync full message history from done event so multi-step
              // turns (tool calls with intermediate assistant text) are
              // not lost.  The streaming chunks only carry the final
              // step's deltas; earlier steps' text lives exclusively in
              // done.messages.
              if (data.messages && Array.isArray(data.messages) && data.messages.length > 0) {
                // Rebuild sess.messages from the authoritative server list.
                // 每个 step 都保留自己的 meta（不再共享 assistantMsg.meta 引用）。
                // tool 消息由 done 完整提供（name+args+result+status）。
                var rebuilt = [];
                var userPushed = false;
                var stepIndex = 0;
                var assistantCount = 0;
                for (var di = 0; di < data.messages.length; di++) {
                  var dm = data.messages[di];
                  if (dm.role === 'user') {
                    if (!userPushed) {
                      var existingUser = sess.messages.find(function(m) { return m.role === 'user'; });
                      if (existingUser) rebuilt.push(existingUser);
                      else rebuilt.push({ role: 'user', content: dm.content || '', time: formatTime(new Date().toISOString()) });
                      userPushed = true;
                    }
                  } else if (dm.role === 'assistant') {
                    var aText = '';
                    if (typeof dm.content === 'string') aText = dm.content;
                    else if (Array.isArray(dm.content)) {
                      for (var ci = 0; ci < dm.content.length; ci++) {
                        if (dm.content[ci] && dm.content[ci].type === 'text') aText += dm.content[ci].text || '';
                      }
                    }
                    assistantCount++;
                    var isLast = di === data.messages.length - 1;
                    var assistantModel = {
                      role: 'assistant',
                      content: aText,
                      blocks: dm.blocks,
                      streaming: false,
                      // 每个 step 用自己的 meta：只把最后一条设完整 meta，
                      // 其他条至少有 time 字段保证渲染可见。
                      meta: isLast ? assistantMsg.meta : {
                        time: new Date().toISOString(),
                        duration: '-',
                        firstToken: '-',
                        tokens: estimateTokens(aText),
                      },
                      time: formatTime(new Date().toISOString()),
                    };
                    if (!Array.isArray(assistantModel.blocks) || assistantModel.blocks.length === 0) {
                      assistantModel.blocks = aText ? [{ kind: 'text', text: aText }] : [];
                    }
                    rebuilt.push(assistantModel);
                  } else if (dm.role === 'tool') {
                    rebuilt.push({
                      role: 'tool',
                      toolName: dm.toolName || 'unknown',
                      callId: dm.callId,
                      args: dm.args,
                      result: dm.result,
                      status: dm.status || 'success',
                      expanded: false,
                    });
                  }
                }
                // If the last assistant message from done has empty content
                // but we accumulated chunks, fall back to fullContent.
                if (rebuilt.length > 0) {
                  var lastRebuilt = rebuilt[rebuilt.length - 1];
                  if (lastRebuilt.role === 'assistant' && (!lastRebuilt.content || lastRebuilt.content === '')) {
                    lastRebuilt.content = fullContent;
                  }
                }
                sess.messages = rebuilt;
              } else {
                assistantMsg.streaming = false;
                stopStreamReveal(assistantMsg);
              }

              if (state.currentSessionId === sess.agentId) {
                // Full re-render to ensure all messages (including
                // intermediate assistant steps and tool calls) are shown.
                updateStreamingMessage._lastHtml = null;
                render();
                // 更新侧边栏状态（发送按钮等）
                updateSidebarStatus();
              } else {
                updateSidebarStatus();
              }
            } else if (eventType === 'error') {
              receivedChunks = true;
              stopWaitTimer(sess);
              const errMsg = translateError(data.message || data.error || 'SSE error');
              if (!fullContent && retryCount < MAX_RETRIES) {
                showRetryNotice(retryCount + 1);
                await sleep(RETRY_DELAY_MS);
                return sendWithRetry(sess, text, assistantMsg, startTime, firstChunkTime, fullContent, retryCount + 1);
              }
              // v28: 轨迹日志 — 记录错误
              trajPush(sess, 'error', errMsg, { error: errMsg });
              throw new Error(errMsg);
            } else if (eventType === 'approval') {
              // v11: 审批请求
              state.pendingApprovals.push({
                approvalId: data.approvalId,
                sessionId: data.sessionId || sess.agentId,
                toolName: data.toolName || 'unknown',
                args: data.args,
              });
              // P1: 局部更新审批角标，不全量 render
              if (state.currentSessionId === sess.agentId) render();
              else updateSidebarStatus();
            } else if (eventType === 'plan') {
              // v11: 计划更新
              sess.plan = data.plan || sess.plan;
              if (state.currentSessionId === sess.agentId && state.activePanel === 'plan') render();
            } else if (eventType === 'goal') {
              sess.goal = data.goal || sess.goal;
              if (state.currentSessionId === sess.agentId && state.activePanel === 'goal') render();
            } else if (eventType === 'todo') {
              sess.todos = data.todos || sess.todos;
              if (state.currentSessionId === sess.agentId && state.activePanel === 'todos') render();
            } else if (eventType === 'subagent') {
              const sa = data.subagent;
              if (sa) {
                const existing = sess.subagents.find(s => s.id === sa.id);
                if (existing) Object.assign(existing, sa);
                else sess.subagents.push(sa);
                if (state.currentSessionId === sess.agentId && state.activePanel === 'subagents') render();
              }
            } else if (eventType === 'job') {
              const job = data.job;
              if (job) {
                const existing = sess.jobs.find(j => j.id === job.id);
                if (existing) Object.assign(existing, job);
                else sess.jobs.push(job);
                if (state.currentSessionId === sess.agentId && state.activePanel === 'jobs') render();
              }
            } else if (eventType === 'deliverable') {
              const d = data.deliverable;
              if (d) sess.deliverables.push(d);
              if (state.currentSessionId === sess.agentId && state.activePanel === 'deliverables') render();
            } else if (eventType === 'trajectory') {
              if (data.node) sess.trajectory.push(data.node);
              if (state.currentSessionId === sess.agentId && state.activePanel === 'trajectory') render();
            } else if (eventType === 'compaction') {
              sess.compactionInfo = data;
              // P1: 局部更新 compaction 信息，不全量 render
              if (state.currentSessionId === sess.agentId) {
                const compactionEl = $('.compaction-info');
                if (compactionEl) {
                  compactionEl.textContent = (data.status || '') + (data.saved ? ' 节省 ' + data.saved + ' 条' : '');
                } else {
                  render();
                }
              }
            }
          } catch (parseErr) {
            if (parseErr.message && parseErr.message.includes('retry')) return;
          }
          eventType = '';
        }
      }
    }

    if (!receivedChunks && !fullContent && retryCount < MAX_RETRIES) {
      stopWaitTimer(sess);
      showRetryNotice(retryCount + 1);
      await sleep(RETRY_DELAY_MS);
      return sendWithRetry(sess, text, assistantMsg, startTime, firstChunkTime, fullContent, retryCount + 1);
    }

    if (assistantMsg.streaming) {
      stopWaitTimer(sess);
      const endTime = Date.now();
      assistantMsg.streaming = false;
      await waitForStreamReveal(assistantMsg);
      assistantMsg.meta = {
        time: new Date().toISOString(),
        duration: ((endTime - startTime) / 1000).toFixed(1) + 's',
        firstToken: firstChunkTime ? ((firstChunkTime - startTime) / 1000).toFixed(1) + 's' : '-',
        tokens: estimateTokens(fullContent),
      };
      sess.stats.turns++;
      sess.stats.steps++;
      sess.stats.llmTimeMs += (endTime - startTime);
      sess.stats.outputTokens += estimateTokens(fullContent);
      sess.stats.inputTokens += estimateTokens(text);
      sess.sending = false;
      sess.streaming = false;
      sess.abortController = null;
      if (state.currentSessionId === sess.agentId) render();
      else updateSidebarStatus();
    }
  } catch (e) {
    stopWaitTimer(sess);
    if (e.name === 'AbortError') {
      if (!fullContent && retryCount < MAX_RETRIES) {
        showRetryNotice(retryCount + 1);
        await sleep(RETRY_DELAY_MS);
        return sendWithRetry(sess, text, assistantMsg, startTime, firstChunkTime, fullContent, retryCount + 1);
      }
      assistantMsg.streaming = false;
      await waitForStreamReveal(assistantMsg);
      assistantMsg.meta = {
        time: new Date().toISOString(),
        duration: ((Date.now() - startTime) / 1000).toFixed(1) + 's',
        firstToken: '-', tokens: estimateTokens(fullContent),
      };
    } else {
      if (!fullContent && retryCount < MAX_RETRIES) {
        showRetryNotice(retryCount + 1);
        await sleep(RETRY_DELAY_MS);
        return sendWithRetry(sess, text, assistantMsg, startTime, firstChunkTime, fullContent, retryCount + 1);
      }
      sess.error = translateError(e.message || '请求失败');
      if (!fullContent) {
        sess.messages = sess.messages.filter(function(m) { return m !== assistantMsg; });
      } else {
        assistantMsg.streaming = false;
        await waitForStreamReveal(assistantMsg);
      }
    }
    sess.sending = false;
    sess.streaming = false;
    sess.abortController = null;
    if (state.currentSessionId === sess.agentId) render();
    else updateSidebarStatus();
  } finally {
    // 仅在非重试场景下清理（重试时递归调用会自行处理）
    if (!retryCount) {
      sess.sending = false;
      sess.streaming = false;
      sess.abortController = null;
      stopWaitTimer(sess);
      stopApprovalPolling(sess);
      if (state.currentSessionId === sess.agentId) {
        render();
        if ($('#composer-input')) $('#composer-input').focus();
      }
      // v11: 处理排队消息
      if (sess.queue && sess.queue.length > 0) {
        const nextMsg = sess.queue.shift();
        sendMessage(nextMsg);
      }
    }
  }
}

// v11: 解析 done 事件中的额外信息
function parseDoneExtras(sess, data) {
  for (const msg of data.messages || []) {
    if (msg.role === 'tool' && msg.toolName) {
      // 工具调用记录
      const toolMsg = {
        role: 'tool',
        toolName: msg.toolName,
        args: msg.args,
        result: msg.result,
        status: msg.status || 'success',
        duration: msg.duration,
      };
      // 插入到 assistant 消息之前
      const lastIdx = sess.messages.length - 1;
      sess.messages.splice(lastIdx, 0, toolMsg);
    }
  }
}

// v11: 从 done 事件构建轨迹
function buildTrajectoryFromDone(sess, data) {
  if (!data.messages || !Array.isArray(data.messages)) return;
  sess.trajectory = sess.trajectory || [];
  for (const msg of data.messages) {
    if (msg.role === 'user') {
      sess.trajectory.push({ type: 'user', text: (msg.content || '').slice(0, 120), depth: 0 });
    } else if (msg.role === 'assistant') {
      const text = typeof msg.content === 'string' ? msg.content : (Array.isArray(msg.content) ? msg.content.map(c => c.text || '').join('') : '');
      sess.trajectory.push({ type: 'llm', text: text.slice(0, 200), depth: 0 });
    } else if (msg.role === 'tool') {
      sess.trajectory.push({ type: 'tool', text: msg.toolName || 'tool-call', depth: 1 });
    }
  }
}

// v11: 处理工具调用事件 — P1: 避免全量 render
function handleToolCallEvent(sess, data) {
  const toolMsg = {
    role: 'tool',
    toolName: data.toolName || 'unknown',
    args: data.args,
    status: data.status || 'running',
    expanded: false,
  };
  // 插入到最后的 assistant 消息之前
  const lastIdx = sess.messages.length - 1;
  if (lastIdx >= 0 && sess.messages[lastIdx].role === 'assistant' && sess.messages[lastIdx].streaming) {
    sess.messages.splice(lastIdx, 0, toolMsg);
  } else {
    sess.messages.push(toolMsg);
  }
  // P1: 局部插入而非全量 render
  if (state.currentSessionId === sess.agentId) {
    const msgsInner = $('.messages-inner');
    if (msgsInner) {
      // 在错误卡片（如果有）之前插入工具调用
      const errorCard = msgsInner.querySelector('.error-card');
      const newEl = renderToolCall(toolMsg);
      if (errorCard) msgsInner.insertBefore(newEl, errorCard);
      else msgsInner.appendChild(newEl);
      scrollToBottom();
    } else {
      render();
    }
  }
}

// ── 流式阶段辅助：完成当前 assistant 消息 ────────────
function finishAssistantMessage(sess, msg, content) {
  if (!msg) return;
  msg.streaming = false;
  if (content && !msg.content) msg.content = content;
  if (!msg.meta) {
    msg.meta = {
      time: new Date().toISOString(),
      duration: '-',
      firstToken: '-',
      tokens: estimateTokens(msg.content || ''),
    };
  }
}

// ── 流式阶段辅助：新建一个 streaming assistant 消息 ───
function newStreamingAssistant() {
  return { role: 'assistant', content: '', streaming: true, meta: null };
}

// ── 流式阶段辅助：插入工具卡片（step 边界） ───────────
function insertToolCard(sess, toolData) {
  handleToolCallEvent(sess, toolData);
}

// ── 侧边栏状态更新 ─────────────────────────
function updateSidebarStatus() {
  const sidebar = $('.sidebar');
  if (sidebar) {
    const newSidebar = renderSidebar();
    sidebar.replaceWith(newSidebar);
  }
}

function showRetryNotice(n) { showToast('连接异常，正在重试 (' + n + '/' + MAX_RETRIES + ')…'); }
function sleep(ms) { return new Promise(function(resolve) { setTimeout(resolve, ms); }); }

// ── 流式更新 ───────────────────────────────
function updateStreamingMessage(msg) {
  startStreamReveal(msg);
}

function extractAssistantTextFromDone(data) {
  if (data.messages && Array.isArray(data.messages)) {
    // 只取最后一条 assistant 消息的文本。
    // 多 step（工具调用）场景下 done.messages 会有多条 assistant 消息，
    // 合并它们会导致文本拼接混乱；重建逻辑（下方 sess.messages = rebuilt）
    // 会正确拆分为多条消息，这里只需提供 fallback。
    let lastText = '';
    for (const m of data.messages) {
      if (m.role === 'assistant') {
        lastText = '';
        if (typeof m.content === 'string') {
          lastText = m.content;
        } else if (Array.isArray(m.content)) {
          for (const item of m.content) {
            if (item && item.type === 'text' && item.text) lastText += item.text;
          }
        }
      }
    }
    return lastText;
  }
  return '';
}

// ── 取消 ───────────────────────────────────
async function cancelAgent() {
  const sess = getCurrentSession();
  if (!sess) return;
  sess.sending = false;
  sess.streaming = false;
  stopWaitTimer(sess);
  var lastMsg = sess.messages[sess.messages.length - 1];
  if (lastMsg && lastMsg.streaming) {
    lastMsg.streaming = false;
    if (lastMsg.content) lastMsg.content += '\n\n_[已取消]_';
    else lastMsg.content = '_[已取消]_';
  }
  render();
  if (sess.abortController) { sess.abortController.abort(); return; }
  try { await apiRequest(API + '/' + sess.agentId + '/cancel', { method: 'POST' }); }
  catch (e) { /* ignore */ }
}

// ── 加载数据 ───────────────────────────────
async function loadWorkspaces() {
  try {
    const data = await apiRequest(API + '/workspaces');
      state.workspaces = Array.isArray(data)
        ? data.map(function(w) { return (typeof w === 'string') ? { name: w.split('/').filter(Boolean).pop(), path: w } : w; })
        : [];
      localStorage.setItem('workspaces', JSON.stringify(state.workspaces));
  } catch (e) { /* ignore */ }
}

async function loadModelSettings() {
  try {
    const data = await apiRequest(SETTINGS_API);
      state.modelSettings = Array.isArray(data) ? data : [];
  } catch (e) { /* ignore */ }
}

async function refreshModelSettingsUI() {
  await Promise.all([loadModelSettings(), loadAvailableModels()]);
  openSettingsPage();
}

function getAvailableChannelCodes() {
  return state.availableModels.map(m => m.channelCode).filter(Boolean);
}

function getPreferredChannel() {
  const availableCodes = getAvailableChannelCodes();
  return availableCodes.length > 0 ? availableCodes[0] : DEFAULT_CHANNEL;
}

function syncCurrentChannel() {
  const saved = localStorage.getItem('currentChannel');
  const availableCodes = getAvailableChannelCodes();
  if (availableCodes.length === 0) {
    state.currentChannel = DEFAULT_CHANNEL;
  } else if (saved && availableCodes.includes(saved)) {
    state.currentChannel = saved;
  } else {
    state.currentChannel = getPreferredChannel();
  }
  localStorage.setItem('currentChannel', state.currentChannel);
}

async function loadAvailableModels() {
  try {
    const data = await apiRequest(MODEL_CATALOG_API);
      state.availableModels = Array.isArray(data) ? data : [];
      syncCurrentChannel();
  } catch (e) { /* ignore */ }
}

async function loadPlugins() {
  try {
    const data = await apiRequest(PLUGIN_API);
      state.plugins = Array.isArray(data) ? data : [];
  } catch (e) { /* ignore */ }
}

async function loadPluginConfig(pluginId) {
  try {
    const data = await apiRequest(PLUGIN_API + '/' + encodeURIComponent(pluginId) + '/config');
    state.pluginConfigs[pluginId] = Array.isArray(data) ? data : [];
  } catch (e) {
    state.pluginConfigs[pluginId] = [];
    showToast('读取插件配置失败');
  }
}

// v11: 加载审批列表
async function loadPendingApprovals() {
  try {
      const data = await apiRequest(APPROVAL_API + '/runtime/pending');
      state.pendingApprovals = Array.isArray(data) ? data : [];
      const current = getCurrentSession();
      const pending = state.pendingApprovals.find(function(item) {
        return current && (item.sessionId || '') === (current.sessionId || current.agentId);
      });
      if (current) {
        current.currentStage = pending
          ? '等待审批 · ' + (pending.toolName || 'unknown')
          : current.currentStage;
      }
      if (pending && current && state.currentSessionId === current.agentId) render();
  } catch (e) { /* ignore */ }
}

// v11: 加载会话历史 — P1: 恢复会话列表
async function loadSessionHistory() {
  try {
    const limit = state.sessionHistoryLimit || 20;
    const offset = state.sessionHistory.length;
    const data = await apiRequest(CONSOLE_API + '/sessions?limit=' + limit + '&offset=' + offset);
      const page = Array.isArray(data) ? data : [];
      const known = new Set(state.sessionHistory.map(function(item) {
        return item.sessionId || item.agentId;
      }));
      state.sessionHistory = state.sessionHistory.concat(page.filter(function(item) {
        return !known.has(item.sessionId || item.agentId);
      }));
      state.hasMoreSessions = page.length === limit;
      // P1: 将历史会话恢复到 state.sessions 中
      for (const hist of page) {
        // 避免重复添加已存在的会话
        const existing = state.sessions.find(s => s.agentId === hist.agentId || s.agentId === hist.sessionId);
        if (!existing) {
          const sess = new ChatSession(hist.agentId || hist.sessionId || ('agent-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6)));
          sess.sessionId = hist.sessionId || null;
          sess.title = hist.title || hist.lastMessage || '历史对话';
          sess.lastMessage = hist.lastMessage || '';
          sess.createdAt = hist.createdAt || hist.startTime || new Date().toISOString();
          sess.messages = []; // 历史消息按需懒加载（见 selectSession）
          state.sessions.push(sess);
        }
      }
      // 按创建时间倒序
      state.sessions.sort(function(a, b) {
        return new Date(b.createdAt) - new Date(a.createdAt);
      });
  } catch (e) { /* ignore */ }
}

async function loadMoreSessions() {
  if (state.loadingMoreSessions) return;
  state.loadingMoreSessions = true;
  state.sidebarSessionLimit += 10;
  render();
  if (state.hasMoreSessions) {
    await loadSessionHistory();
  }
  state.loadingMoreSessions = false;
  render();
}

// ── 初始化 ─────────────────────────────────
// 全局键盘快捷键
document.addEventListener('keydown', function(e) {
  // Esc 关闭模态框
  if (e.key === 'Escape') {
    closeSessionMenu();
    const overlay = $('.modal-overlay');
    if (overlay) { overlay.remove(); return; }
    const slash = $('#slash-suggestions');
    if (slash) { slash.remove(); return; }
  }
  // Cmd/Ctrl+K 新建对话
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault();
    newChat();
  }
});

async function init() {
  initTheme();
  loadSessionsFromStorage(); // v28: 先从 localStorage 恢复
  await loadWorkspaces();
  await loadAvailableModels();
  await loadModelSettings();
  await loadPlugins();
  await loadPendingApprovals();
  await loadSessionHistory(); // P1: 恢复会话列表
  render();
  if ($('#composer-input')) $('#composer-input').focus();
}

init();
