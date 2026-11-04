// The line to the Kotlin side (HostBridge.kt): calls that answer later, and events it sends.
// In a plain browser (tests, `tools/preview`), a stand-in can be put at window.CapriNative first.

const pending = new Map();
const listeners = new Map();
let seq = 0;

const bridge = () => window.CapriNative;

// The Kotlin side calls these through evaluateJavascript.
window.__capri = {
  reply(id, ok, json) {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    let value = null;
    try {
      value = json == null || json === '' ? null : JSON.parse(json);
    } catch {
      value = json;
    }
    if (ok) p.resolve(value);
    else p.reject(Object.assign(new Error(value?.error || String(value || 'failed')), { code: value?.code || null }));
  },
  emit(channel, json) {
    let payload = null;
    try {
      payload = json == null || json === '' ? null : JSON.parse(json);
    } catch {
      payload = json;
    }
    for (const fn of listeners.get(channel) || []) {
      try {
        fn(payload);
      } catch (err) {
        console.error(`[capri] ${channel} listener failed`, err);
      }
    }
  },
};

// A call to Kotlin that answers with a value (or throws with its error).
export function call(method, args = null) {
  const id = `c${++seq}`;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    try {
      bridge().invoke(id, method, JSON.stringify(args ?? null));
    } catch (err) {
      pending.delete(id);
      reject(err);
    }
  });
}

// A call to Kotlin nobody waits on.
export function send(method, args = null) {
  try {
    bridge().invoke('', method, JSON.stringify(args ?? null));
  } catch (err) {
    console.error(`[capri] ${method} failed`, err);
  }
}

export function on(channel, fn) {
  if (!listeners.has(channel)) listeners.set(channel, new Set());
  listeners.get(channel).add(fn);
  return () => listeners.get(channel)?.delete(fn);
}

// Events from inside the page, the same way (the menu, the touch layer)
export function emit(channel, payload) {
  window.__capri.emit(channel, JSON.stringify(payload ?? null));
}
