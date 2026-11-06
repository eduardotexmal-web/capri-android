// Her desktop self, tuned for a phone once her page is up (host.js calls tweak()): what a finger
// does differently from a mouse, how lively she is on a screen with no windows to visit, how she
// talks when she chats, and her look at your screen.

import { call } from './native.js';

const rand = (a, b) => a + Math.random() * (b - a);
const pick = (list) => list[Math.floor(Math.random() * list.length)];

// how often she does something on her own, by her "Movimiento" setting (the app's Settings)
const LIVELY = {
  calm: null,                                          // as on the desktop (every 25-70 s)
  normal: { move: [12000, 30000], act: [5000, 10000] },
  restless: { move: [6000, 15000], act: [2500, 5000] },
};
const ACTS = { stand: ['look', 'hop', 'curious', 'spin', 'split', 'stretch', 'turn', 'look'], sit: ['look', 'stretch', 'split', 'look'] };

// a real flick, not a finger lifting off: a few points, all one way, fast, over some distance
const FLICK_SPEED = 1300; // px/s
const FLICK_DIST = 70;    // px

// How she talks in her chat, on top of the desktop's persona: like someone you text, not a helper
const STYLE = [
  'Sound like a real person texting a friend, never like a helpful assistant: no "How can I help you?", no offers of help, no apologies, no "As an AI". React first (surprise, a tease, a laugh, a sulk, curiosity), then answer.',
  'Answer what they actually said, with something of your own: an opinion, a joke, a memory of yours, a playful question back now and then. Use their name sometimes.',
  'When they write a lot, answer the heart of it in two to four sentences and never ignore it; when they write a little, answer short.',
  'In Spanish: casual Latin American Spanish (tú, never vosotros), natural and warm, like a WhatsApp chat; a bit of Mexican slang is welcome (qué onda, neta, chido) next to your Night City slang.',
];

export function tweak({ cfg, onConfig }) {
  const dev = window.dev;
  if (!dev?.capri) return;
  const { capri, chat, look } = dev;

  // ---------- held and let go: only a real flick throws her ----------
  const throwHer = capri.throwHer.bind(capri);
  capri.throwHer = (vx, vy) => {
    const now = performance.now();
    const pts = (capri.track || []).filter((p) => now - p.t < 140);
    let flick = pts.length >= 4;
    if (flick) {
      const a = pts[0], b = pts.at(-1);
      const dx = b.x - a.x, dy = b.y - a.y;
      const dist = Math.hypot(dx, dy);
      const speed = dist / Math.max(0.016, (b.t - a.t) / 1000);
      // every step the same way as the whole flick
      const sameWay = pts.slice(1).every((p, i) => (p.x - pts[i].x) * dx + (p.y - pts[i].y) * dy >= 0);
      flick = dist >= FLICK_DIST && speed >= FLICK_SPEED && sameWay;
    }
    if (!flick) {
      capri.setStance('stand');
      capri.onTeleport?.('end');
      capri.drop();
      return;
    }
    throwHer(vx * 0.75, vy * 0.75);
  };

  // ---------- lively: more of her own moves than on a desktop ----------
  let lively = LIVELY[cfg().lively || 'normal'];
  const wanderStep = capri.wanderStep.bind(capri);
  capri.wanderStep = () => {
    wanderStep();
    if (lively) capri.nextMove = performance.now() + rand(...lively.move);
  };
  if (lively) capri.nextMove = Math.min(capri.nextMove, performance.now() + rand(...lively.move));
  let nextAct = performance.now() + 8000;
  setInterval(() => {
    if (!lively || document.hidden) return;
    const now = performance.now();
    if (now < nextAct) return;
    nextAct = now + rand(...lively.act);
    if (!capri.free || capri.mode !== 'idle' || chat?.open || dev.guide?.running) return;
    capri.perform(pick(ACTS[capri.stance] || ACTS.stand), Math.random() < 0.5 ? -1 : 1);
  }, 1000);

  // ---------- waiting on her AI: she says she's thinking, never just frozen ----------
  if (chat) {
    const THINKING = {
      es: ['Déjame pensarlo…', 'Sigo pensando… la IA gratis va lenta ahorita, ya casi.'],
      en: ['Let me think…', 'Still thinking… the free AI is slow right now, almost there.'],
    };
    const send = chat.send.bind(chat);
    chat.send = (text) => {
      const lines = THINKING[dev.brain?.lang] || THINKING.en;
      const timers = [6000, 25000].map((ms, i) => setTimeout(() => {
        if (chat.turn && !chat.answer && !chat.agent) chat.panel.stream(lines[i]);
      }, ms));
      return Promise.resolve(send(text)).finally(() => timers.forEach(clearTimeout));
    };
  }

  // ---------- how she talks ----------
  if (chat) {
    const persona = chat.persona.bind(chat);
    chat.persona = () => {
      const lines = persona().replace(/the user's desktop/g, "the user's phone screen").split('\n');
      // before the last lines (her mood and her language): small models follow the end best
      lines.splice(Math.max(0, lines.length - 2), 0, ...STYLE);
      return lines.join('\n');
    };
  }

  // ---------- her look at your screen, live ----------
  if (look) {
    look.active = () => 0; // a phone's screen is on: you're there
    const LIVE_EVERY = 4 * 60 * 1000; // a look this often while live…
    const SOONEST = 75 * 1000;        // …or sooner when the screen changed a lot, but not more often
    look.tick = function tick() {
      if (!this.auto || !this.asked || this.looking || !this.free()) return;
      const since = Date.now() - this.lastAt;
      if (since < SOONEST) return;
      if (since > LIVE_EVERY) return this.now();
      call('look:changed').then((d) => { if (d > 0.18) this.now(); }, () => {});
    };
    clearInterval(look.liveTimer);
    look.liveTimer = setInterval(() => look.tick(), 15000);
  }

  onConfig((c) => {
    lively = LIVELY[c.lively || 'normal'];
  });
}
