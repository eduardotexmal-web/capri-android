#!/usr/bin/env node
// Copies Capri's desktop code (https://github.com/mochiiwuff/capri-desktop) into the Android app, as is.
//
//   node tools/sync-upstream.mjs <path to a capri-desktop checkout, with `npm ci --omit=dev` run>
//
// What it copies, unchanged:
//   src/renderer/**                 -> assets/capri/src/renderer   (her whole visual engine)
//   assets/**                       -> assets/capri/assets         (her pictures)
//   node_modules/@fontsource fonts  -> assets/capri/node_modules   (only the files her CSS loads)
//   hooks/*.js, scripts/install-*   -> assets/capri/hooks|scripts  (served to Termux by the app)
// Patched, and only this:
//   index.html gets two extra tags before app.js: the Android host (window.host) and its stylesheet.
//   fx.js: one robustness fix, see `patch(...)` below.
// Wrapped, not edited:
//   src/main/{llm,ollama,labels,launch}.js and scripts/demo-sequence.js become CommonJS factories the
//   Android host loads in the WebView (assets/android/upstream/*.js).

import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(here, '..');
const ASSETS = path.join(ROOT, 'app', 'src', 'main', 'assets');
const OUT = path.join(ASSETS, 'capri');
const WRAPPED = path.join(ASSETS, 'android', 'upstream');

const src = path.resolve(process.argv[2] || '');
if (!process.argv[2] || !fs.existsSync(path.join(src, 'src', 'renderer', 'index.html'))) {
  console.error('usage: node tools/sync-upstream.mjs <capri-desktop checkout>');
  process.exit(1);
}

const copyDir = (from, to, filter = () => true) => {
  fs.mkdirSync(to, { recursive: true });
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const a = path.join(from, entry.name), b = path.join(to, entry.name);
    if (!filter(a, entry)) continue;
    if (entry.isDirectory()) copyDir(a, b, filter);
    else fs.copyFileSync(a, b);
  }
};

fs.rmSync(OUT, { recursive: true, force: true });
fs.rmSync(WRAPPED, { recursive: true, force: true });

// her renderer and her pictures
copyDir(path.join(src, 'src', 'renderer'), path.join(OUT, 'src', 'renderer'));
copyDir(path.join(src, 'assets'), path.join(OUT, 'assets'), (file) => !file.endsWith('.ico'));

// the fonts her CSS asks for, and nothing else
const css = fs.readFileSync(path.join(src, 'src', 'renderer', 'styles', 'capri.css'), 'utf8');
const fonts = [...css.matchAll(/url\('\.\.\/\.\.\/\.\.\/(node_modules\/[^']+)'\)/g)].map((m) => m[1]);
for (const rel of new Set(fonts)) {
  const from = path.join(src, rel);
  if (!fs.existsSync(from)) {
    console.error(`missing ${rel}: run \`npm ci --omit=dev\` in ${src} first`);
    process.exit(1);
  }
  fs.mkdirSync(path.dirname(path.join(OUT, rel)), { recursive: true });
  fs.copyFileSync(from, path.join(OUT, rel));
}
for (const pkg of ['chakra-petch', 'jetbrains-mono']) {
  const lic = path.join(src, 'node_modules', '@fontsource', pkg, 'LICENSE');
  if (fs.existsSync(lic)) fs.copyFileSync(lic, path.join(OUT, 'node_modules', '@fontsource', pkg, 'LICENSE'));
}

// her hooks and their installers, served to Termux (HookServer.kt)
copyDir(path.join(src, 'hooks'), path.join(OUT, 'hooks'));
fs.mkdirSync(path.join(OUT, 'scripts'), { recursive: true });
for (const f of ['install-hooks.js', 'install-codex-hooks.js', 'install-hooks-agy.js']) {
  fs.copyFileSync(path.join(src, 'scripts', f), path.join(OUT, 'scripts', f));
}
fs.copyFileSync(path.join(src, 'LICENSE'), path.join(OUT, 'LICENSE'));

// index.html: the Android host goes in before her app starts
const indexFile = path.join(OUT, 'src', 'renderer', 'index.html');
const index = fs.readFileSync(indexFile, 'utf8');
const anchor = '<script type="module" src="js/app.js"></script>';
if (!index.includes(anchor)) throw new Error('index.html changed upstream: update the patch in sync-upstream.mjs');
fs.writeFileSync(indexFile, index.replace(anchor,
  '<link rel="stylesheet" href="../../../android/android.css">\n'
  + '  <script type="module" src="../../../android/host.js"></script>\n  ' + anchor));

// Fixes, kept to a line each and checked against the original so a change upstream is noticed.
const patch = (rel, from, to, why) => {
  const file = path.join(OUT, rel);
  const code = fs.readFileSync(file, 'utf8');
  if (!code.includes(from)) throw new Error(`${rel} changed upstream, patch not applied: ${why}`);
  fs.writeFileSync(file, code.replace(from, to));
  console.log(`patched ${rel}: ${why}`);
};
// Her intro resolves on its "land" frame. A frame that takes longer than what's left of the intro
// (a slow phone, the screen turning off mid-intro) skips past its end: the effect is dropped without
// landing and she never appears. Landing when it ends too (resolving twice does nothing).
patch('src/renderer/js/fx.js', 'onCue, land, cued: -1 });', 'onCue, land, done: land, cued: -1 });',
  'the intro lands even when its last frames are skipped');

// main-process modules that need no Electron: wrapped as CommonJS factories (cjs.js runs them)
fs.mkdirSync(WRAPPED, { recursive: true });
const wrap = (file, name) => {
  const code = fs.readFileSync(path.join(src, file), 'utf8');
  fs.writeFileSync(path.join(WRAPPED, `${name}.js`),
    `// ${file} from capri-desktop, unchanged, wrapped by tools/sync-upstream.mjs. Do not edit.\n`
    + `import { define } from '../cjs.js';\n`
    + `define(${JSON.stringify(name)}, function (module, exports, require, process, __dirname, fetch) {\n${code}\n});\n`);
};
wrap('src/main/llm.js', 'llm');
wrap('src/main/ollama.js', 'ollama');
wrap('src/main/labels.js', 'labels');
wrap('scripts/demo-sequence.js', 'demo-sequence');
wrap('src/main/launch.js', 'launch'); // YouTube, Roblox, Spotify and websites: fetch + open a link

let commit = 'unknown';
try {
  commit = execFileSync('git', ['-C', src, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
} catch {
  // not a git checkout
}
const { version } = JSON.parse(fs.readFileSync(path.join(src, 'package.json'), 'utf8'));
fs.writeFileSync(path.join(ASSETS, 'android', 'upstream.json'),
  JSON.stringify({ repo: 'https://github.com/mochiiwuff/capri-desktop', version, commit }, null, 2) + '\n');
console.log(`synced capri-desktop ${version} (${commit.slice(0, 7)})`);
