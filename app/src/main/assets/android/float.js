// Without gravity: she stays where you put her, even in the air. Let go of her (or fling her) and
// she just floats there; she doesn't wander off on her own, doesn't jump onto your notifications,
// and isn't thrown around, so nothing to get upset about. Where you left her is kept, for the next
// time she starts.

const SAVE_EVERY = 5000;

export function floatMode({ cfg, setConfig }) {
  const capri = window.dev?.capri;
  if (!capri) return;
  const on = () => Boolean(cfg().float) && !cfg().gamer;

  // let go of her, fast or not: she stays right there
  const throwHer = capri.throwHer.bind(capri);
  capri.throwHer = (vx, vy) => {
    if (!on()) return throwHer(vx, vy);
    capri.setStance('stand');
    capri.onTeleport?.('end');
    capri.perform('land');
  };
  const drop = capri.drop.bind(capri);
  capri.drop = () => {
    if (!on()) return drop();
    capri.perform('land');
  };
  // the card she sat on went away: she stays in the air where she was
  const fall = capri.fall.bind(capri);
  capri.fall = () => {
    if (!on()) return fall();
    if (capri.stance === 'sit') capri.setStance('stand');
  };

  // where she is, kept now and then (only when it changed)
  let saved = '';
  const spot = () => {
    const { w, floor } = capri.view;
    return { x: +(capri.pos.x / w).toFixed(3), y: +(capri.pos.y / floor).toFixed(3) };
  };
  setInterval(() => {
    if (!on() || capri.busy || capri.dragging || capri.hidden || capri.stowed || !['stand', 'sit'].includes(capri.stance)) return;
    const at = spot();
    const key = JSON.stringify(at);
    if (key === saved) return;
    saved = key;
    setConfig({ floatAt: at });
  }, SAVE_EVERY);

  // started again: back where you left her
  const back = () => {
    const at = cfg().floatAt;
    if (!on() || !at || capri.hidden || capri.stowed) return;
    const { w, floor } = capri.view;
    const to = { x: at.x * w, y: Math.min(floor, at.y * floor) };
    saved = JSON.stringify(at);
    if (Math.hypot(to.x - capri.pos.x, to.y - capri.pos.y) > 30) capri.teleport(to);
  };
  const ready = setInterval(() => {
    if (capri.busy || capri.hidden || capri.p?.alpha < 1) return;
    clearInterval(ready);
    back();
  }, 500);
}
