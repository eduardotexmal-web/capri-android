// Her icon mode: she folds herself into her biochip (her green mask, eyes open) and keeps talking
// from there: her looks at your screen, her cheers, Claude's news, her chat, a word now and then.
// You put the mask wherever you like with a finger; a tap opens her menu (to talk to her, or to
// bring her back out). So she can be big when she's out, and still never in the way.
//
// Put away from her notification ("Guardarla") on top of it, she's quiet, as any time she's put away.

import { drawMask } from '../capri/src/renderer/js/fx.js';
import { clamp, rand, pick, reducedMotion } from '../capri/src/renderer/js/util.js';

const K = 0.6; // the chip's size (chip.js)

export function iconMode({ cfg, onConfig, setConfig }) {
  const dev = window.dev;
  const { capri, panel, chip, look, chat, guide, brain } = dev || {};
  if (!capri || !chip || !panel) return;

  const want = () => Boolean(cfg().icon);
  const talks = () => want() && cfg().visible !== false;
  const talking = () => talks() && capri.stowed;
  let leaving = false;
  let scanUntil = 0;

  // ---------- where the chip is: where you left it, else its corner ----------
  const maskPlace = Object.getPrototypeOf(Object.getPrototypeOf(chip)).place; // Mask's: by the middle of its face
  const setFace = (x, y) => {
    const { w, floor } = capri.view;
    const fx = clamp(x, 34, w - 34);
    const fy = clamp(y, 70, floor - 36);
    maskPlace.call(chip, fx, fy, K);
    chip.face = { x: fx, y: fy };
    anchor();
  };
  const place = chip.place.bind(chip);
  chip.place = (w, h) => {
    place(w, h);
    const at = cfg().iconAt;
    if (want() && at && capri.view) setFace(at.x * w, at.y * h);
    else anchor();
  };
  const replace = () => chip.place(capri.view.w, capri.view.floor);

  // her panel comes out of the chip, not out of where she was
  const anchor = () => {
    if (!talking()) return;
    const { x, y } = chip.center();
    panel.setAnchor({ x, y: y - 10, top: y - 100 * K - 6 }, capri.view);
  };
  const onMove = capri.onMove;
  capri.onMove = (head) => (talking() ? anchor() : onMove?.(head));

  // ---------- in and out ----------
  // in icon mode, what would bring her out (her chat, a look, a reminder) leaves her in her chip
  const unstow = capri.unstow.bind(capri);
  capri.unstow = () => (want() && !leaving ? Promise.resolve() : unstow());
  // coming out: next to where the chip is
  const spot = capri.spot.bind(capri);
  capri.spot = (name, ...rest) => {
    if (name !== 'chip' || !leaving) return spot(name, ...rest);
    const side = 150 * capri.scale + 30;
    return { x: clamp(chip.center().x, side, capri.view.w - side), y: capri.view.floor };
  };

  let busy = false;
  const sync = async () => {
    if (busy) return;
    busy = true;
    try {
      if (want() && !capri.stowed) {
        await capri.stow();
        replace();
      }
      if (talking()) {
        panel.resume();
        anchor();
        chip.show();
      } else if (capri.stowed && !panel.suspended) {
        panel.hide();
        panel.suspend();
      }
      if (!want() && capri.stowed && cfg().visible !== false) {
        leaving = true;
        try {
          await unstow();
        } finally {
          leaving = false;
          replace(); // the chip back in its corner, for the next time she's put away
        }
      }
    } finally {
      busy = false;
    }
    if (want() !== capri.stowed && cfg().visible !== false) setTimeout(sync, 600); // changed again meanwhile
  };
  let last = `${want()}/${cfg().visible}`;
  onConfig(() => {
    const now = `${want()}/${cfg().visible}`;
    if (now === last) return;
    last = now;
    setTimeout(sync, 0);
  });
  if (want()) {
    if (!capri.stowed) capri.stowNow();
    replace();
    sync();
  }

  // ---------- a finger on it: moved anywhere, a tap opens her menu ----------
  let drag = null;
  const on = (x, y) => talking() && chip.visible && chip.contains(x, y, 6);
  window.addEventListener('mousedown', (e) => {
    if (!on(e.clientX, e.clientY)) return;
    const { x, y } = chip.center();
    drag = { dx: x - e.clientX, dy: y - e.clientY, moved: false };
  }, true);
  window.addEventListener('mousemove', (e) => {
    if (!drag || !e.buttons) return;
    drag.moved = true;
    setFace(e.clientX + drag.dx, e.clientY + drag.dy);
  }, true);
  window.addEventListener('mouseup', () => {
    if (!drag) return;
    const moved = drag.moved;
    drag = null;
    if (!moved) return;
    const { x, y } = chip.center();
    setConfig({ iconAt: { x: x / capri.view.w, y: y / capri.view.floor } });
  }, true);
  // (before app.js, which would bring her out)
  window.addEventListener('click', (e) => {
    if (!on(e.clientX, e.clientY)) return;
    e.stopImmediatePropagation();
    window.host?.openMenu?.();
  }, true);

  // ---------- her eyes on it: open, blinking, on what she's saying ----------
  const draw = chip.draw.bind(chip);
  chip.draw = (now, frame) => {
    if (!talking() || chip.landing) return draw(now, frame);
    const calm = reducedMotion.matches;
    const says = panel.visible;
    const bob = calm ? 0 : [0, -1.5, -3, -1.5][Math.floor(frame / (says ? 3 : 6)) % 4];
    const blink = !calm && frame % 52 < 2;
    let at = null;
    if (Date.now() < scanUntil) at = { x: capri.view.w / 2, y: capri.view.floor / 3 };
    else if (says) {
      const r = panel.rect();
      if (r) at = { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    }
    const look = at && { x: at.x - chip.x, y: at.y - chip.y };
    drawMask(chip.ctx, chip.cx, chip.cy + bob, chip.k, { eyes: blink ? [0, 0] : [1, 1], look });
  };

  // ---------- her look at your screen, from the chip ----------
  if (look) {
    const free = look.free.bind(look);
    look.free = () => (talking() ? !chat?.open && !guide?.running && !panel.visible : free());
    const effect = look.effect.bind(look);
    look.effect = () => {
      if (!talking()) return effect();
      scanUntil = Date.now() + 1600;
      look.fx?.scan(() => ({ x: 0, y: 0, w: capri.view.w, h: capri.view.floor }));
      look.voice?.play('charge');
    };
  }

  // ---------- a word now and then, as she would out of her chip ----------
  let nextWord = Date.now() + rand(3, 6) * 60000;
  setInterval(() => {
    if (!talking() || document.hidden || cfg().gamer) return;
    if (Date.now() < nextWord) return;
    if (panel.current || chat?.open || guide?.running || look?.looking) return;
    nextWord = Date.now() + rand(5, 10) * 60000;
    const line = brain?.idleLine?.();
    if (line) panel.say({ kind: 'idle', face: pick(['look', 'smug', 'grin']), line, hold: 4500 });
  }, 10000);
}
