// Runs the Android page in Chromium at a phone's size, with a stand-in for the Kotlin side, and
// takes screenshots: her intro, a Claude session (the desktop's demo), her menu, a drag.
//
//   node tools/preview/run.mjs [out dir]
//
// Served from https://capri.local like the app serves it (HostBridge.kt), so paths match.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

// playwright from the project, or from wherever node finds it (NODE_PATH)
const { chromium } = createRequire(import.meta.url)('playwright');

const here = path.dirname(fileURLToPath(import.meta.url));
const ASSETS = path.resolve(here, '..', '..', 'app', 'src', 'main', 'assets');
const OUT = path.resolve(process.argv[2] || path.join(here, 'out'));
fs.mkdirSync(OUT, { recursive: true });

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.webp': 'image/webp', '.woff2': 'font/woff2', '.json': 'application/json' };

const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required'] });
const context = await browser.newContext({
  viewport: { width: 412, height: 915 }, deviceScaleFactor: 1, isMobile: false, hasTouch: true, locale: 'es-ES',
});
await context.route('https://capri.local/**', (route) => {
  const rel = decodeURIComponent(new URL(route.request().url()).pathname);
  const file = path.join(ASSETS, rel);
  if (!file.startsWith(ASSETS) || !fs.existsSync(file)) return route.fulfill({ status: 404, body: 'not found' });
  route.fulfill({ status: 200, contentType: TYPES[path.extname(file)] || 'application/octet-stream', body: fs.readFileSync(file) });
});
await context.addInitScript({ path: path.join(here, 'mock-native.js') });

const page = await context.newPage();
const errors = [];
page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));
page.on('console', (msg) => {
  if (['error', 'warning'].includes(msg.type())) errors.push(`${msg.type()}: ${msg.text()}`);
});
// a phone's wallpaper behind her, as the overlay sits over your apps
await page.goto('https://capri.local/capri/src/renderer/index.html');
await page.evaluate(() => { document.documentElement.style.background = 'linear-gradient(160deg, #2b3a55, #13131c)'; });

const shot = async (name, wait = 0) => {
  if (wait) await page.waitForTimeout(wait);
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log('shot', name);
};

await shot('01-intro', 3000);
await shot('02-intro-later', 6000);
await shot('03-welcome', 14000);

// her menu: a long press on her is a right-click
const sprite = await page.locator('#sprite').boundingBox();
const nativeLog = await page.evaluate(() => window.__mock.state.log.slice());
console.log('native calls:', [...new Set(nativeLog)].join(', '));
const regions = await page.evaluate(() => window.__mock.state.log.filter((m) => m === 'touch:regions').length);
console.log('touch:regions sent', regions, 'times; sprite at', JSON.stringify(sprite));

// close whatever she's saying, then a Claude session (the desktop's demo)
await page.evaluate(() => window.__capri.emit('capri:command', JSON.stringify({ name: 'demo' })));
await shot('04-demo-start', 3000);
await shot('05-demo-work', 6000);
await shot('06-demo-later', 9000);

// her menu
await page.evaluate(() => window.host.openMenu());
await shot('07-menu', 600);
await page.locator('.cmenu__item', { hasText: /Tamaño|Size/ }).first().click();
await shot('08-menu-size', 400);
await page.locator('.cmenu-shade').click({ position: { x: 5, y: 5 } });

console.log(errors.length ? errors.join('\n') : 'no errors');
await browser.close();
