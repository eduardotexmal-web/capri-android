// The finger as her mouse.
//
// Her renderer was written for a mouse: it moves over her (hover), presses on her (drag, throw),
// right-clicks her (her menu). On a phone:
//   - tap          -> the WebView's own mouse events and click (Chromium sends them after a tap)
//   - long press   -> the WebView's own contextmenu event: her menu, as a right-click
//   - drag         -> mousedown where the finger came down, then mousemove / mouseup (made here:
//                     Chromium sends no mouse events for a finger that moves)
//   - two fingers  -> mouse moves with no button: the pointer passing over her, to pet her mane
// Kotlin only lets touches reach the page where she is (TouchPads.kt), so the rest of the phone
// stays yours: this file tells it where that is, every frame something moved.

import { send } from './native.js';

const SLOP = 10; // px a finger moves before it's a drag (Chromium's tap stops there too)
const RECENT_MS = 4000; // a touch this recent still counts as "the pointer is here"

export class Touch {
  constructor() {
    this.last = null; // { x, y, at }
    this.drag = null; // { x, y, target, started }
    this.interactive = false;
    this.regions = '';
    this.bind();
    this.watch();
  }

  setInteractive(on) {
    this.interactive = on;
  }

  lastPoint() {
    return this.last && { x: this.last.x, y: this.last.y };
  }

  // host.cursor(): where the pointer is, as main.js answers it
  cursor() {
    if (!this.last) return null;
    return { x: this.last.x, y: this.last.y, inside: performance.now() - this.last.at < RECENT_MS, display: 1 };
  }

  mouse(type, x, y, target, buttons = 0) {
    const el = target || document.elementFromPoint(x, y) || document.body;
    el.dispatchEvent(new MouseEvent(type, {
      bubbles: true, cancelable: true, composed: true, view: window,
      clientX: x, clientY: y, screenX: x, screenY: y, button: 0, buttons,
    }));
  }

  bind() {
    const point = (t) => ({ x: t.clientX, y: t.clientY });
    window.addEventListener('touchstart', (e) => {
      const p = point(e.touches[0]);
      this.last = { ...p, at: performance.now() };
      if (e.touches.length >= 2) {
        // two fingers on her: a pointer passing over, no button (petting)
        this.drag = null;
        this.pet = true;
        return;
      }
      this.pet = false;
      // on her panel or her menu, the finger scrolls and taps them as any page: nothing to make up
      if (e.target.closest?.('#panel, .cmenu, .anote')) {
        this.drag = null;
        return;
      }
      // the pointer arrives over her first, as a mouse would
      this.mouse('mousemove', p.x, p.y);
      this.drag = { ...p, target: document.elementFromPoint(p.x, p.y), started: false };
    }, { passive: true });

    window.addEventListener('touchmove', (e) => {
      const p = point(e.touches[0]);
      this.last = { ...p, at: performance.now() };
      if (this.pet) {
        const a = e.touches[0], b = e.touches[1] || a;
        this.mouse('mousemove', (a.clientX + b.clientX) / 2, (a.clientY + b.clientY) / 2);
        e.preventDefault();
        return;
      }
      const d = this.drag;
      if (!d) return;
      if (!d.started && Math.hypot(p.x - d.x, p.y - d.y) < SLOP) return;
      if (!d.started) {
        d.started = true;
        this.mouse('mousedown', d.x, d.y, d.target, 1);
      }
      this.mouse('mousemove', p.x, p.y, null, 1);
      e.preventDefault();
    }, { passive: false });

    const end = (e) => {
      const d = this.drag;
      this.drag = null;
      if (e.touches?.length === 0) this.pet = false;
      if (!d?.started) return; // a tap: Chromium's own mousedown, mouseup and click
      const t = e.changedTouches[0];
      this.mouse('mouseup', t.clientX, t.clientY, null, 0);
      e.preventDefault();
    };
    window.addEventListener('touchend', end, { passive: false });
    window.addEventListener('touchcancel', end, { passive: false });
  }

  // Where touches should reach her, sent to Kotlin when it changes: her body, her panel, her biochip.
  watch() {
    const rectOf = (el) => {
      if (!el || el.hidden || el.closest('[hidden]')) return null;
      const r = el.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return null;
      // on a 16 px grid, a little wider than her: the window over her moves less often
      const q = 16;
      return [Math.floor(r.left / q) * q - q, Math.floor(r.top / q) * q - q, Math.ceil(r.right / q) * q + q, Math.ceil(r.bottom / q) * q + q];
    };
    // every frame while a finger is on her (the pads follow it), else ten times a second: her own
    // moves are slow next to that, and the pads are 16 px wider than her
    const tick = () => {
      if (this.drag || this.pet) requestAnimationFrame(tick);
      else setTimeout(() => requestAnimationFrame(tick), 90);
      const list = [
        rectOf(document.getElementById('sprite')),
        rectOf(document.getElementById('panel')),
        rectOf(document.getElementById('chip')),
        rectOf(document.getElementById('mask')),
        rectOf(document.querySelector('.cmenu:not([hidden])')),
        ...[...document.querySelectorAll('.anote:not([hidden])')].map(rectOf),
      ].filter(Boolean);
      // (a finger already on her keeps her: Android sends the whole gesture to where it began)
      const all = list;
      const key = JSON.stringify(all);
      if (key === this.regions) return;
      this.regions = key;
      send('touch:regions', { rects: all, dpr: devicePixelRatio });
    };
    requestAnimationFrame(tick);
  }
}
