// A tiny CommonJS loader, so the desktop's main-process modules (llm.js, ollama.js, labels.js,
// demo-sequence.js) run here unchanged. What they ask Node for is answered by small Android shims.

import { nativeFetch } from './fetch.js';

const factories = new Map();
const cache = new Map();

export function define(name, factory) {
  factories.set(name, factory);
}

// process.env as the desktop modules read it (OLLAMA_HOST comes from her settings, host.js)
export const env = {};
const processShim = { env, platform: 'android', versions: {}, execPath: '', cwd: () => '/' };

const notHere = (what) => () => {
  throw new Error(`${what} is not available on Android`);
};

// Electron, as far as launch.js uses it: links open through Android (host.js fills these in)
export const electron = {
  shell: { openExternal: async () => {}, openPath: async () => 'not on Android' },
  app: { getPath: () => '/', getApplicationNameForProtocol: () => '' },
};

const shims = {
  electron,
  apps: { powershell: () => Promise.reject(new Error('no PowerShell on Android')) },
  os: {
    homedir: () => '/',
    tmpdir: () => '/tmp',
    totalmem: () => (navigator.deviceMemory || 4) * 1024 ** 3,
    cpus: () => [],
    platform: () => 'android',
  },
  fs: {
    existsSync: () => false,
    readFileSync: notHere('fs'),
    writeFileSync: () => {},
    mkdirSync: () => {},
    readdirSync: () => [],
  },
  path: {
    join: (...parts) => parts.filter(Boolean).join('/').replace(/\/+/g, '/'),
    resolve: (...parts) => parts.filter(Boolean).join('/').replace(/\/+/g, '/'),
    dirname: (p) => String(p).replace(/\/[^/]*$/, '') || '/',
    basename: (p) => String(p).split('/').pop(),
    sep: '/',
  },
  crypto: { randomUUID: () => crypto.randomUUID() },
  child_process: { spawn: notHere('spawn'), execFile: (_c, _a, _o, cb) => (typeof _o === 'function' ? _o : cb)?.(new Error('no processes on Android')) },
};

function require(name) {
  const bare = name.replace(/^node:/, '').replace(/^\.\.?\/(.*\/)?/, '').replace(/\.js$/, '');
  if (shims[bare]) return shims[bare];
  return load(bare);
}

export function load(name) {
  if (cache.has(name)) return cache.get(name).exports;
  const factory = factories.get(name);
  if (!factory) throw new Error(`module ${name} is not bundled for Android`);
  const module = { exports: {} };
  cache.set(name, module);
  // `fetch` inside these modules is the native one: no CORS, and it streams like Node's
  factory.call(module.exports, module, module.exports, require, processShim, '/', nativeFetch);
  return module.exports;
}
