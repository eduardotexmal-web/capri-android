// window.host for Android: what preload.js + main.js give her renderer on the desktop, here.
//
// The renderer (src/renderer, copied unchanged) only ever talks to `window.host`. On the desktop
// that's Electron's IPC to main.js; here it's this file, which does main.js's job in the page
// itself (her settings, her chat through the desktop's own llm.js and ollama.js, her menu) and asks
// Kotlin (HostBridge.kt) for what only Android can do: storage, the network without CORS, intents,
// the overlay window, the phone's state, Claude's events from the hook server.
//
// The order and the names follow main.js's registerIpc(), so the two can be read side by side.

import { call, send, on, emit } from './native.js';
import { load, env, electron } from './cjs.js';
import './upstream/ollama.js';
import './upstream/llm.js';
import './upstream/labels.js';
import './upstream/demo-sequence.js';
import './upstream/launch.js';
import { Menu } from './menu.js';
import { Touch } from './touch.js';
import './setup.js'; // her setup without a terminal (before app.js builds it)
import { Notes } from './notes.js';
import './wink.js'; // what she says, also in her notification
import { followTurns } from './turn.js';

const llm = load('llm');
const ollama = load('ollama');
const { TRAY, LANGS } = load('labels');
const demoSteps = load('demo-sequence');
const launch = load('launch');

// launch.js opens its links through Electron's shell: here, through Android
electron.shell.openExternal = (url) => call('open:url', { url: String(url) });
electron.app.getApplicationNameForProtocol = (proto) => (proto === 'roblox://' && device.roblox ? 'Roblox' : '');

const PORT = 47823;
const OLLAMA_SITE = 'https://ollama.com';

// main.js's config.js DEFAULTS, plus what only Android has
const DEFAULTS = {
  scale: 0.6, muted: false, volume: 0.7, lang: 'auto', displayId: null, wander: true, possess: true, visible: true,
  welcomed: false, assistant: 'auto', linked: false, chatModel: null, provider: 'pollinations', apiKeys: {},
  customUrl: '', claudeCwd: null, userName: '', persona: '', myLines: [], memories: [], claudeMode: 'default',
  claude: true, rest: 'corner', theme: 'nightcity', motion: 'auto', motionAsked: false, render: 'auto', follow: true,
  jealous: true, closePages: true, reminders: [], bond: null, pcReact: true, watchScreen: false, lookAsked: false,
  // Android
  ollamaHost: '127.0.0.1:11434', // Ollama in Termux on the phone, or your PC's address on your network
  hooksCopied: false,            // the Termux command to plug her in was handed to you
  lan: false,                    // the hook server also listens on your Wi-Fi (a PC's Claude Code can reach her)
};

// setup.js's
const MODELS = [
  { name: 'gemma3:4b', bytes: 3.3e9 },
  { name: 'llama3.2', bytes: 2.0e9 },
];
const KEY_PAGES = {
  groq: 'https://console.groq.com/keys',
  gemini: 'https://aistudio.google.com/apikey',
  openrouter: 'https://openrouter.ai/keys',
  openai: 'https://platform.openai.com/api-keys',
  mistral: 'https://console.mistral.ai/api-keys',
  deepseek: 'https://platform.deepseek.com/api_keys',
};
const ASSISTANTS = {
  claude: { name: 'Claude Code', help: 'https://code.claude.com/docs/en/quickstart' },
  codex: { name: 'Codex', help: 'https://learn.chatgpt.com/docs/hooks' },
  agy: { name: 'Antigravity', help: 'https://antigravity.google/docs/hooks' },
};

// What Android can't do on the desktop's terms: the renderer gets the same "no" a Mac would.
const noop = () => {};

// ---------- her settings (config.js) ----------

let cfg = { ...DEFAULTS };
let device = { lang: 'en', ram: 4 * 1024 ** 3, sdk: 0, model: '', ip: null, termux: false };

async function loadConfig() {
  const [saved, info] = await Promise.all([call('config:load').catch(() => null), call('device:info').catch(() => null)]);
  cfg = { ...DEFAULTS, ...(saved || {}) };
  if (info) device = { ...device, ...info };
  env.OLLAMA_HOST = cfg.ollamaHost || DEFAULTS.ollamaHost;
}

function saveConfig(patch) {
  cfg = { ...cfg, ...patch };
  env.OLLAMA_HOST = cfg.ollamaHost || DEFAULTS.ollamaHost;
  send('config:save', cfg);
  return cfg;
}

const activeAssistant = () => {
  const id = ASSISTANTS[cfg.assistant] ? cfg.assistant : 'claude';
  return { id, ...ASSISTANTS[id] };
};

// 'auto' -> the phone's language if she speaks it, English otherwise
function resolveLang(setting) {
  if (LANGS.includes(setting)) return setting;
  for (const tag of [device.lang, ...(navigator.languages || [])]) {
    const base = String(tag || '').toLowerCase().split(/[-_]/)[0];
    if (LANGS.includes(base)) return base;
  }
  return 'en';
}

function rendererConfig() {
  const { apiKeys, ...rest } = cfg;
  return { ...rest, keys: Object.keys(apiKeys || {}), assistant: activeAssistant().id, lang: resolveLang(rest.lang), langSetting: rest.lang, floorGap: 0 };
}

const listeners = { config: new Set(), command: new Set() };
const sub = (set) => (fn) => {
  set.add(fn);
  return () => set.delete(fn);
};

function updateConfig(patch) {
  saveConfig(patch);
  if ('visible' in patch) send('notify:update', { visible: cfg.visible });
  if ('lan' in patch) send('bridge:lan', { on: Boolean(cfg.lan) });
  const out = rendererConfig();
  for (const fn of listeners.config) fn(out);
  return out;
}

function sendCommand(name, payload = {}) {
  for (const fn of listeners.command) fn({ name, ...payload });
}

// ---------- her memory of your chats ----------

let memory = {};
const readMemory = () => memory;
function saveMemory(patch) {
  memory = { ...memory, ...patch };
  send('memory:save', memory);
}
function forgetChats() {
  llm.resetClaude();
  saveMemory({ history: [], claude: null });
  sendCommand('forget');
}

// ---------- her hooks: Claude Code in Termux, or on a PC on your network ----------

// Termux can't be written to from here: the command that plugs her in goes on your clipboard, and
// Termux opens if it's there. It fetches her own hook (the desktop's, unchanged) from her server.
function hooksCommand() {
  const flag = { claude: '', codex: ' codex', agy: ' agy' }[activeAssistant().id];
  return `curl -fsS http://127.0.0.1:${PORT}/install.sh | sh -s --${flag}`;
}
function hooksStatus() {
  return { installed: Boolean(cfg.linked || cfg.hooksCopied), file: '~/.claude/settings.json (Termux)' };
}
async function runHooks(action, { quiet = false } = {}) {
  let outcome;
  if (action === 'install') {
    const res = await call('hooks:copy', { command: hooksCommand() }).catch((err) => ({ ok: false, error: err.message }));
    outcome = res?.ok === false ? { action, ok: false, error: res.error } : { action, ok: true, file: hooksStatus().file };
    if (outcome.ok) updateConfig({ hooksCopied: true });
  } else {
    await call('hooks:copy', { command: `curl -fsS http://127.0.0.1:${PORT}/install.sh | sh -s -- --uninstall` }).catch(noop);
    updateConfig({ hooksCopied: false, linked: false });
    outcome = { action, ok: true, file: hooksStatus().file };
  }
  if (!quiet) sendCommand('hooks', outcome);
  return outcome;
}

// ---------- her chat (main.js) ----------

let chatModels = null;
let chatError = null;
let chatErrorCode = null;
let cloud = { at: 0, account: null, free: [] };
const chats = new Map();

function pickModel(c = cfg) {
  if (!chatModels?.length) return null;
  if (chatModels.includes(c.chatModel)) return c.chatModel;
  return chatModels.find((m) => !ollama.isCloud(m)) || chatModels[0];
}

async function openKeys(apiKeys = {}) {
  const out = {};
  for (const [id, v] of Object.entries(apiKeys)) out[id] = await call('keys:open', { value: v }).catch(() => '');
  return out;
}
const llmConfig = async (c = cfg) => ({ ...c, apiKeys: await openKeys(c.apiKeys) });

async function refreshModels() {
  chatError = null;
  chatErrorCode = null;
  chatModels = await llm.models(cfg.provider, await llmConfig()).catch((err) => {
    chatError = err.message;
    chatErrorCode = err.code || null;
    return null;
  });
  if (cfg.provider === 'ollama' && chatModels) refreshCloud();
  return chatModels;
}

let cloudCheck = null;
function refreshCloud(force = false) {
  if (!force && Date.now() - cloud.at < 60000) return cloudCheck || Promise.resolve(cloud);
  const at = Date.now();
  cloud.at = at;
  cloudCheck = (async () => {
    const account = await ollama.account();
    const free = account?.name ? await ollama.freeModels() : [];
    cloud = { at, account, free };
    return cloud;
  })().finally(() => { cloudCheck = null; });
  return cloudCheck;
}

async function chatWithFallback(c, messages, sendDelta, signal, streamed) {
  let model = pickModel(c);
  const tried = new Set();
  let busy = 0;
  for (;;) {
    try {
      const text = await llm.chat(c.provider, await llmConfig(c), { model, messages }, sendDelta, signal);
      if (model !== pickModel(c)) updateConfig({ chatModel: model });
      return text;
    } catch (err) {
      if (signal.aborted || streamed() || !['busy', 'gone', 'signin'].includes(err.code)) throw err;
      if (err.code === 'busy' && busy++ === 0) {
        await new Promise((r) => setTimeout(r, 1500));
        continue;
      }
      tried.add(model);
      const next = chatModels?.find((m) => !tried.has(m) && !ollama.isCloud(m)) || chatModels?.find((m) => !tried.has(m));
      if (!next || tried.size >= 3) throw err;
      model = next;
      busy = 0;
    }
  }
}

async function pullModel(name, onProgress, signal) {
  const res = await (await import('./fetch.js')).nativeFetch(`${ollama.base()}/api/pull`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ model: name, stream: true }), signal,
  });
  if (!res.ok) throw new Error(`Ollama answered ${res.status}`);
  const pieces = new Map();
  const decoder = new TextDecoder();
  let buffer = '';
  for await (const chunk of res.body) {
    buffer += decoder.decode(chunk, { stream: true });
    let nl;
    while ((nl = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, nl).trim();
      buffer = buffer.slice(nl + 1);
      if (!line) continue;
      const msg = JSON.parse(line);
      if (msg.error) throw new Error(msg.error);
      if (msg.digest && msg.total) pieces.set(msg.digest, { completed: msg.completed || 0, total: msg.total });
      let completed = 0, total = 0;
      for (const p of pieces.values()) {
        completed += p.completed;
        total += p.total;
      }
      onProgress?.({ completed, total, status: msg.status });
      if (msg.status === 'success') return;
    }
  }
}

async function fetchModel(name, onProgress, signal) {
  await pullModel(name, onProgress, signal);
  updateConfig({ provider: 'ollama' });
  await refreshModels();
  const model = chatModels?.find((m) => m === name || m.startsWith(`${name}:`));
  if (model) updateConfig({ chatModel: model });
  return model;
}

async function pickProvider(provider) {
  updateConfig({ provider });
  await refreshModels();
  if (chatErrorCode) sendCommand('setupKey');
}

// ---------- she looks at your screen ----------

const snaps = new Map();
async function sayAboutSnap({ id, persona, prompt, history = [] }) {
  const image = snaps.get(id);
  snaps.delete(id);
  if (!image) return { ok: false, error: 'the picture is gone' };
  if (!chatModels?.length) await refreshModels();
  const model = pickModel(cfg);
  if (!model) return { ok: false, code: 'noai' };
  if (cfg.provider === 'claude') return { ok: false, code: 'novision', model: 'Claude Code' };
  if (cfg.provider === 'ollama' && !(await ollama.canSee(model))) return { ok: false, code: 'novision', model };
  const user = cfg.provider === 'ollama'
    ? { role: 'user', content: prompt, images: [image] }
    : { role: 'user', content: [{ type: 'text', text: prompt }, { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${image}` } }] };
  const before = (Array.isArray(history) ? history : []).filter((m) => typeof m?.content === 'string' && ['user', 'assistant'].includes(m.role));
  const messages = [{ role: 'system', content: persona }, ...before, user];
  for (let attempt = 0; ; attempt++) {
    try {
      const text = await llm.chat(cfg.provider, await llmConfig(), { model, messages }, noop, AbortSignal.timeout(120000));
      return { ok: true, text, model };
    } catch (err) {
      if (err.code === 'busy' && attempt === 0) {
        await new Promise((r) => setTimeout(r, 1500));
        continue;
      }
      const novision = /image|vision|multimodal|modalit|image_url|content.*(part|type)/i.test(err.message);
      return { ok: false, error: err.message, code: novision ? 'novision' : err.code || null, model };
    }
  }
}

// ---------- the demo ----------

let demoTimer = null;
function playDemo() {
  clearTimeout(demoTimer);
  const steps = demoSteps(resolveLang(cfg.lang));
  let i = 0;
  const next = () => {
    if (i >= steps.length) return;
    const [delay, evt] = steps[i++];
    demoTimer = setTimeout(() => {
      emit('claude:event', evt);
      next();
    }, delay);
  };
  next();
}

function toggleVisible(force) {
  const visible = typeof force === 'boolean' ? force : !cfg.visible;
  updateConfig({ visible });
  sendCommand('stow', { on: !visible });
}

// ---------- her menu (the tray menu of main.js, drawn in the page) ----------

function menuTemplate() {
  const t = Object.fromEntries(Object.entries(TRAY[resolveLang(cfg.lang)]).map(([key, value]) =>
    [key, typeof value === 'string' && activeAssistant().id !== 'claude' ? value.replaceAll('Claude Code', activeAssistant().name) : value]));
  const watched = activeAssistant();
  const hookState = hooksStatus();
  const xp = cfg.bond?.xp || 0;
  const stage = [0, 100, 300, 550, 850].filter((at) => xp >= at).length - 1;
  const reminders = [...(cfg.reminders || [])].sort((a, b) => a.at - b.at);
  const when = (at) => {
    const d = new Date(at);
    const sameDay = d.toDateString() === new Date().toDateString();
    return new Intl.DateTimeFormat(resolveLang(cfg.lang), sameDay ? { hour: '2-digit', minute: '2-digit' } : { weekday: 'short', hour: '2-digit', minute: '2-digit' }).format(d);
  };
  const cancelReminder = (id) => updateConfig({ reminders: (cfg.reminders || []).filter((r) => r.id !== id) });

  return [
    { label: t.online(PORT), enabled: false },
    { label: t.sync(Math.min(100, Math.floor(xp / 10)), t.stages[stage]), enabled: false },
    { type: 'separator' },
    { label: t.teleport, click: () => sendCommand('teleport') },
    { label: t.satellite, click: () => sendCommand('satellite') },
    { label: t.demo, click: playDemo },
    { label: t.help, click: () => sendCommand('welcome') },
    ...(cfg.claude ? [{ label: t.report, click: () => sendCommand('report') }] : []),
    { label: t.talk, click: () => sendCommand('chat') },
    { label: t.lookNow, click: () => sendCommand('look') },
    {
      label: t.reminders,
      submenu: reminders.length
        ? reminders.map((r) => ({
          label: `${when(r.at)} · ${(r.text || '…').slice(0, 50)}`,
          submenu: [{ label: t.cancelReminder, click: () => cancelReminder(r.id) }],
        }))
        : [{ label: t.noReminders, enabled: false }],
    },
    { label: t.custom, click: () => sendCommand('customize') },
    { label: cfg.visible ? t.hide : t.show, click: () => toggleVisible() },
    { type: 'separator' },
    {
      label: t.size,
      submenu: [[t.small, 0.45], [t.medium, 0.6], [t.large, 0.8]].map(([label, scale]) => ({
        label, type: 'radio', checked: Math.abs(cfg.scale - scale) < 0.01, click: () => updateConfig({ scale }),
      })),
    },
    {
      label: t.sound,
      submenu: [
        { label: t.mute, type: 'checkbox', checked: cfg.muted, click: (item) => updateConfig({ muted: item.checked }) },
        { type: 'separator' },
        { label: t.soundsDir, click: () => send('sounds:open') },
      ],
    },
    {
      label: t.chatModel,
      submenu: [
        { label: t.aiSetup, click: () => sendCommand('setupChat') },
        { type: 'separator' },
        ...Object.entries(llm.PROVIDERS).filter(([id]) => id !== 'claude').map(([provider, p]) => ({
          label: p.label, type: 'radio', checked: cfg.provider === provider, click: () => pickProvider(provider),
        })),
        { type: 'separator' },
        ...(chatModels?.length
          ? chatModels.map((name) => ({
            label: cloud.free.includes(name) ? `${name} · ${t.cloudFree}` : name,
            type: 'radio', checked: name === pickModel(cfg), click: () => updateConfig({ chatModel: name }),
          }))
          : [{ label: (chatError || t.noOllama).slice(0, 80), enabled: false }]),
        ...(cfg.provider === 'ollama' && chatModels ? [
          ...cloud.free.filter((name) => !chatModels.includes(name)).map((name) => ({
            label: `${name} · ${t.cloudFree}`, type: 'radio', checked: false, click: () => fetchModel(name).catch(noop),
          })),
          ...(cloud.account?.signin ? [{ label: t.cloudSignin, click: () => send('open:url', { url: cloud.account.signin }) }] : []),
        ] : []),
        { type: 'separator' },
        { label: t.forget, click: forgetChats },
        { label: t.apiKey, click: () => sendCommand('setupKey') },
      ],
      // the models, as they are now, when the menu opens on it
      open: () => refreshModels(),
    },
    { label: t.wander, type: 'checkbox', checked: cfg.wander, click: (item) => updateConfig({ wander: item.checked }) },
    { label: t.possess, type: 'checkbox', checked: cfg.possess, click: (item) => updateConfig({ possess: item.checked }) },
    { label: t.jealous, type: 'checkbox', checked: cfg.jealous !== false, click: (item) => updateConfig({ jealous: item.checked }) },
    {
      label: t.closePages, type: 'checkbox', checked: cfg.closePages !== false, enabled: cfg.jealous !== false,
      click: (item) => updateConfig({ closePages: item.checked }),
    },
    ...(notes ? [notes.menu(updateConfig)] : []),
    { label: t.pcReact, type: 'checkbox', checked: cfg.pcReact !== false, click: (item) => updateConfig({ pcReact: item.checked }) },
    { label: t.lookAuto, type: 'checkbox', checked: Boolean(cfg.watchScreen), click: (item) => sendCommand('watchScreen', { on: item.checked }) },
    {
      label: t.rest,
      submenu: [['corner', t.restCorner], ['taskbar', t.restTaskbar]].map(([rest, label]) => ({
        label, type: 'radio', checked: cfg.rest === rest, click: () => updateConfig({ rest }),
      })),
    },
    {
      label: t.watch,
      submenu: [
        ...Object.entries(ASSISTANTS).map(([id, { name }]) => ({
          label: name,
          type: 'radio',
          checked: cfg.claude && watched.id === id,
          click: () => {
            const other = watched.id !== id;
            updateConfig({ claude: true, assistant: id, ...(other && { linked: false, welcomed: false, hooksCopied: false }) });
            if (other) sendCommand('assistantChanged');
          },
        })),
        { label: t.watchNone, type: 'radio', checked: !cfg.claude, click: () => updateConfig({ claude: false }) },
      ],
    },
    {
      label: t.theme,
      submenu: [['nightcity', 'Night City'], ['classic', t.themeClassic]].map(([theme, label]) => ({
        label, type: 'radio', checked: cfg.theme === theme, click: () => updateConfig({ theme }),
      })),
    },
    {
      label: t.motion,
      submenu: [['auto', t.motionAuto], ['full', t.motionFull], ['reduced', t.motionReduced]].map(([motion, label]) => ({
        label, type: 'radio', checked: cfg.motion === motion, click: () => updateConfig({ motion, motionAsked: true }),
      })),
    },
    {
      label: t.lang,
      submenu: [[t.auto, 'auto'], ...LANGS.map((lang) => [TRAY[lang].name, lang])].map(([label, lang]) => ({
        label, type: 'radio', checked: cfg.lang === lang, click: () => updateConfig({ lang }),
      })),
    },
    {
      label: t.startup, type: 'checkbox', checked: Boolean(cfg.startOnBoot),
      click: (item) => updateConfig({ startOnBoot: item.checked }),
    },
    ...(cfg.claude ? [
      { type: 'separator' },
      hookState.installed
        ? { label: t.hooksOn, enabled: false }
        : { label: t.hooksOff, click: () => runHooks('install') },
      ...(hookState.installed ? [{ label: t.hooksRemove, click: () => runHooks('uninstall') }] : []),
    ] : []),
    { type: 'separator' },
    { label: t.quit, click: () => send('app:quit') },
  ];
}

const menu = new Menu();
const openMenu = (at) => menu.open(menuTemplate, at);

// ---------- the touch layer: the finger as her mouse, and where she can be touched ----------

const touch = new Touch();

// ---------- the phone itself: what Kotlin does for her ----------

// A call whose "no" is the desktop's own "no" for that call (null, false, [] ...)
const ask = (method, fallback) => (args) => call(method, args).catch((err) => {
  console.warn(`[capri] ${method}: ${err.message}`);
  return typeof fallback === 'function' ? fallback(err) : fallback;
});

// ---------- window.host ----------

const ready = loadConfig().then(async () => {
  memory = (await call('memory:load').catch(() => null)) || {};
  llm.restoreClaude(memory.claude);
  refreshModels().catch(noop);
});
const after = (fn) => (...args) => ready.then(() => fn(...args));

on('claude:event', (evt) => {
  if (llm.isChatSession(evt?.session_id)) return;
  if (!cfg.linked && evt?.session_id !== 'demo-session') updateConfig({ linked: true });
});
on('capri:command', (cmd) => {
  if (cmd?.name === 'menu') return openMenu();
  if (cmd?.name === 'toggle') return toggleVisible();
  if (cmd?.name === 'demo') return playDemo();
  for (const fn of listeners.command) fn(cmd);
});

window.host = {
  onClaudeEvent: (fn) => on('claude:event', fn),
  onCommand: sub(listeners.command),
  onConfig: sub(listeners.config),
  onResize: (fn) => on('overlay:resize', fn),
  onWindows: (fn) => on('windows:update', fn),
  setInteractive: (v) => touch.setInteractive(Boolean(v)),
  rearm: noop,
  cursor: async () => touch.cursor(),
  screens: async () => ({
    current: 1, origin: { x: 0, y: 0 }, list: [{ id: 1, x: 0, y: 0, w: innerWidth, h: innerHeight }],
  }),
  moveScreen: async () => null,
  getConfig: after(() => rendererConfig()),
  setConfig: after((patch) => {
    // what her renderer asks for that the desktop's main.js would act on
    if (patch && 'startOnBoot' in patch) send('boot:set', { on: Boolean(patch.startOnBoot) });
    return updateConfig(patch || {});
  }),
  listSounds: ask('sounds:list', []),
  hooksStatus: after(() => hooksStatus()),
  demoWindow: async () => false, // no little terminal for her to sit on: no windows to sit on here
  openMenu: () => openMenu(touch.lastPoint()),
  installHooks: after(() => runHooks('install', { quiet: true })),
  claudeFound: async () => true, // Claude Code may well be in Termux, or on your PC: she offers to plug in
  openClaudeHelp: () => send('open:url', { url: activeAssistant().help }),
  windowsNow: async () => notes?.snapshot() || null,
  closePage: async () => 'gone',
  trayPlace: async () => ({
    // her "tray icon" is her notification, at the top of the screen
    icon: null,
    corner: { x: Math.round(innerWidth / 2 - 70), y: 0, w: 140, h: 28 },
  }),
  openClaudeFolder: async () => ({ found: false, dir: '~/.claude (Termux)' }),
  copyImage: ask('panel:copy', false),
  chatStatus: after(async () => {
    const { provider } = cfg;
    const models = await refreshModels();
    if (!models) return { ok: false, error: chatError || 'offline', code: chatErrorCode, provider };
    if (!models.length) return { ok: false, error: 'nomodel', provider };
    const model = pickModel();
    llm.warm(provider, model);
    const local = llm.isLocal(provider, cfg, model);
    return { ok: true, provider, model, agent: provider === 'claude', local, warmup: provider === 'ollama' && local };
  }),
  chatSend: after(async ({ id, messages }) => {
    const abort = new AbortController();
    chats.set(id, abort);
    try {
      let streamed = false;
      const sendDelta = (text) => {
        streamed = true;
        emit('chat:delta', { id, text });
      };
      const text = await chatWithFallback(cfg, messages, sendDelta, abort.signal, () => streamed);
      return { ok: true, text };
    } catch (err) {
      return { ok: false, error: abort.signal.aborted ? 'stopped' : err.message, code: err.code || null };
    } finally {
      chats.delete(id);
    }
  }),
  chatStop: (id) => chats.get(id)?.abort(),
  chatMemory: after(() => readMemory().history || []),
  chatRemember: (history) => Array.isArray(history) && saveMemory({ history }),
  onChatDelta: (fn) => on('chat:delta', fn),
  lookSnap: after(async () => {
    try {
      const res = await call('look:snap');
      const id = crypto.randomUUID();
      snaps.set(id, res.image);
      setTimeout(() => snaps.delete(id), 120000);
      return { ok: true, id };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }),
  lookSay: after((request) => sayAboutSnap(request || {})),
  lookWhere: after(() => {
    const model = pickModel(cfg);
    const provider = cfg.provider === 'ollama' && ollama.isCloud(model) ? 'Ollama' : llm.PROVIDERS[cfg.provider]?.label || cfg.provider;
    return { local: llm.isLocal(cfg.provider, cfg, model), provider, model };
  }),
  cpu: ask('pc:cpu', null),
  pcDo: ask('pc:do', null),
  openOllama: () => send('open:url', { url: OLLAMA_SITE }),
  setupInfo: after(async () => {
    const { free } = (await ollama.models().catch(() => null)) ? await refreshCloud() : { free: [] };
    return { models: MODELS, cloud: free, ram: device.ram, keyed: Object.keys(KEY_PAGES) };
  }),
  ollamaState: after(async () => {
    const models = await ollama.models().catch(() => null);
    return { running: Boolean(models), installed: Boolean(models), models, winget: false };
  }),
  // your PC's Ollama, by its IP (setup.js on Android): kept only if it answers there
  setOllamaHost: after(async (address) => {
    let host = String(address || '').trim().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
    if (host && !/:\d+$/.test(host)) host += ':11434';
    const before = cfg.ollamaHost;
    updateConfig({ ollamaHost: host });
    const models = await ollama.models().catch(() => null);
    if (!models) updateConfig({ ollamaHost: before });
    return { running: Boolean(models), host };
  }),
  // Ollama on a phone runs in Termux: the command to get it goes on your clipboard
  installOllama: ask('ollama:install', (err) => ({ ok: false, error: err.message })),
  startOllama: after(async () => {
    const res = await call('ollama:start').catch((err) => ({ ok: false, error: err.message }));
    await refreshModels();
    return res;
  }),
  pullModel: after(async (name) => {
    if (!MODELS.some((m) => m.name === name) && !cloud.free.includes(name)) return { ok: false, error: 'unknown model' };
    pulling?.abort();
    const abort = new AbortController();
    pulling = abort;
    try {
      const model = await fetchModel(name, (p) => emit('setup:progress', { kind: 'pull', ...p }), abort.signal);
      return { ok: true, model };
    } catch (err) {
      return { ok: false, error: abort.signal.aborted ? 'stopped' : err.message };
    } finally {
      if (pulling === abort) pulling = null;
    }
  }),
  cancelSetup: () => pulling?.abort(),
  useProvider: after(async ({ provider, key, url }) => {
    if (!llm.PROVIDERS[provider]) return { ok: false, error: 'unknown provider' };
    const before = { provider: cfg.provider, apiKeys: cfg.apiKeys, customUrl: cfg.customUrl, ollamaHost: cfg.ollamaHost };
    const patch = { provider };
    if (key) patch.apiKeys = { ...before.apiKeys, [provider]: await call('keys:seal', { value: String(key).trim() }) };
    // an address for Ollama is where Ollama is (your PC on your network); for the others, the server
    if (url && provider === 'ollama') patch.ollamaHost = String(url).trim().replace(/^https?:\/\//, '').replace(/\/+$/, '');
    else if (url) patch.customUrl = String(url).trim();
    updateConfig(patch);
    const models = await refreshModels();
    if (models?.length) return { ok: true, model: pickModel() };
    const error = models ? 'no model' : chatError || 'offline';
    updateConfig(before);
    await refreshModels();
    return { ok: false, error };
  }),
  keyPage: (provider) => KEY_PAGES[provider] && send('open:url', { url: KEY_PAGES[provider] }),
  launchThroughOllama: ask('ollama:launch', { ok: false }),
  onSetupProgress: (fn) => on('setup:progress', fn),
  openApps: ask('apps:list', []),
  findApp: ask('apps:find', null),
  closeApp: (name, force) => call('apps:close', { name, force: Boolean(force) }).catch((err) => ({ error: err.message })),
  // the phone's apps by name, matched the desktop's way (launch.js match())
  findLaunch: async (text) => {
    const items = await call('apps:launchables').catch(() => []);
    const item = launch.match(items, String(text || ''));
    return item && { ...item, risky: false };
  },
  launch: (item) => call('apps:launch', item).then(() => ({ ok: true }), (err) => ({ ok: false, error: err.message })),
  media: ask('apps:media', false),
  music: (query) => launch.music(query).catch(() => false),
  youtube: (query) => launch.youtube(query).catch(() => null),
  roblox: (query) => launch.roblox(query).catch(() => null),
  searchFiles: ask('files:search', []),
  openFile: (file, reveal) => call('files:open', { file: String(file), reveal: Boolean(reveal) }).then(() => true, () => false),
  screenshot: ask('files:screenshot', null),
  webSearch: async (query) => {
    const q = String(query || '').trim().slice(0, 300);
    if (!q) return false;
    return call('open:url', { url: `https://www.google.com/search?q=${encodeURIComponent(q)}` }).then(() => true, () => false);
  },
  openSite: (address) => launch.site(address).catch(() => false),
  focusOverlay: (v) => send('overlay:focus', { on: Boolean(v) }),
};

let pulling = null;

// a newer version of her is out (CapriService, from the repo's releases): she says so
const UPDATE_LINES = {
  es: '¡Salió mi versión {v}! Toca mi notificación o abre la app para actualizarme.',
  en: 'My version {v} is out! Tap my notification or open the app to update me.',
  pt: 'Saiu minha versão {v}! Toque na minha notificação ou abra o app pra me atualizar.',
  fr: 'Ma version {v} est sortie ! Touche ma notification ou ouvre l’appli pour me mettre à jour.',
  ru: 'Вышла моя версия {v}! Нажми на моё уведомление или открой приложение, чтобы обновить меня.',
};
on('capri:update', ({ version, notes: news } = {}) => {
  const line = (UPDATE_LINES[resolveLang(cfg.lang)] || UPDATE_LINES.en).replace('{v}', version || '');
  const firstNews = String(news || '').split('\n')[0].replace(/^• /, '');
  window.dev?.panel?.say({ kind: 'done', face: 'laugh', line, quote: firstNews, hold: 9000 });
  window.dev?.capri?.perform('hop');
});

// your notifications and your music, as cards she jumps on (notes.js)
let notes = null;
ready.then(() => {
  notes = new Notes({ config: () => cfg, lang: () => resolveLang(cfg.lang) });
  followTurns(() => resolveLang(cfg.lang)); // the phone turned: she turns too
});

// the screen turned (or the keyboard took some of it): her window is another size
window.addEventListener('resize', () => emit('overlay:resize', { width: innerWidth, height: innerHeight, gap: 0 }));

// she's up: Kotlin can show her (the WebView stays hidden until the page has drawn once)
ready.then(() => setTimeout(() => send('overlay:ready'), 0));

// her page's errors, to the app's screen (MainActivity), so a phone with no developer tools can tell
window.addEventListener('error', (e) => send('log', { level: 'E', text: `${e.message} (${String(e.filename).split('/').pop()}:${e.lineno})` }));
window.addEventListener('unhandledrejection', (e) => send('log', { level: 'E', text: `unhandled: ${e.reason?.stack || e.reason}` }));
