// The phone turned: she turns with it. Her window is already the new size (app.js setView keeps
// her on screen when she stands); here she spins round as the screen does, and if she was sitting
// or peeking somewhere that moved, she beams to the same place on the turned screen first.

const LINES = {
  es: ['¡Ey! Avisa antes de girar.', 'Todo da vueltas… vale, ya.', 'Otra vista. Me gusta.'],
  en: ['Hey! Warn me before you turn.', 'Everything’s spinning… okay, done.', 'New view. I like it.'],
  pt: ['Ei! Avisa antes de girar.', 'Tudo girando… pronto, passou.', 'Outra vista. Gostei.'],
  fr: ['Hé ! Préviens avant de tourner.', 'Tout tourne… bon, ça va.', 'Nouvelle vue. J’aime bien.'],
  ru: ['Эй! Предупреждай, когда крутишь.', 'Всё кружится… ладно, всё.', 'Новый вид. Мне нравится.'],
};
const SAY_EVERY = 2 * 60 * 1000;

export function followTurns(lang) {
  let size = { w: innerWidth, h: innerHeight };
  let landscape = innerWidth > innerHeight;
  let saidAt = 0;
  let timer = 0;

  const turned = () => {
    const now = innerWidth > innerHeight;
    const before = size;
    size = { w: innerWidth, h: innerHeight };
    if (now === landscape) return;
    landscape = now;
    const dev = window.dev;
    const capri = dev?.capri;
    if (!capri || capri.stowed || capri.dormant || capri.hidden || capri.dragging) return;
    const side = 150 * capri.scale + 30;
    const x = Math.min(Math.max((capri.pos.x / before.w) * size.w, side), size.w - side);
    const spin = () => capri.perform('spin', x < size.w / 2 ? 1 : -1);
    if (capri.stance === 'stand' && !capri.busy) spin();
    else Promise.resolve(capri.teleport({ x, y: capri.view.floor }, { stance: 'stand' })).then(() => setTimeout(spin, 400));
    const panel = dev.panel;
    if (panel && !panel.current && Date.now() - saidAt > SAY_EVERY && Math.random() < 0.6) {
      saidAt = Date.now();
      const list = LINES[lang()] || LINES.en;
      setTimeout(() => panel.say({ kind: 'info', face: 'wide', line: list[Math.floor(Math.random() * list.length)], hold: 3500 }), 900);
    }
  };

  // the turn comes as one resize or several: act once it has settled
  const settle = () => {
    clearTimeout(timer);
    timer = setTimeout(turned, 350);
  };
  window.addEventListener('resize', settle);
  screen.orientation?.addEventListener('change', settle);
}
