// Your notifications and your music, as windows for her to sit on.
//
// On the desktop she jumps onto your windows (windows.js feeds capri.setWindows). A phone has no
// windows she could see, so here two cards of her own HUD play that part: "now playing" (the music
// any app plays, with its buttons) and a notice that just came in (the app and its title, never
// its text). Both are fed to her as windows, so the desktop's own code seats her on them, rides
// along and lets her fall when one goes. CapriNotifications.kt tells us what plays and what came
// in, once you give Capri notification access.

import { call, send, on, emit } from './native.js';

const NOTE_FOR = 7000;        // how long a notice stays on screen
const MUSIC_SPOT = 'capri.musicCard'; // where you left her music card, and if it's folded
const PAUSED_FOR = 12 * 1000; // music paused this long: its card goes
const MUSIC_FOR = 15 * 1000;  // a new song, play or pause: its card shows this long, then slides away
const OUT_MS = 320;           // a card's way out (android.css .is-out)
const SAY_EVERY = 4 * 60 * 1000;

const LINES = {
  es: {
    playing: 'SONANDO', paused: 'EN PAUSA', notes: 'Notificaciones', hop: 'Saltar a los avisos', player: 'Reproductor de música',
    grant: 'Darle acceso a las notificaciones…',
    music: ['¿Qué escuchas? Súbele.', 'Buen tema. Me quedo aquí arriba.', '♪ {title}… este me lo sé.'],
    note: ['Te escribe {app}.', '{app}: algo nuevo.', 'Mira, {app}.'],
  },
  en: {
    playing: 'NOW PLAYING', paused: 'PAUSED', notes: 'Notifications', hop: 'Jump on notices', player: 'Music player',
    grant: 'Give her notification access…',
    music: ['What’s this? Turn it up.', 'Good one. I’m staying up here.', '♪ {title}… I know this one.'],
    note: ['{app} wants you.', '{app}: something new.', 'Look, {app}.'],
  },
  pt: {
    playing: 'TOCANDO', paused: 'PAUSADO', notes: 'Notificações', hop: 'Pular nos avisos', player: 'Player de música',
    grant: 'Dar acesso às notificações…',
    music: ['O que você tá ouvindo? Aumenta.', 'Boa. Vou ficar aqui em cima.', '♪ {title}… essa eu conheço.'],
    note: ['{app} tá te chamando.', '{app}: coisa nova.', 'Olha, {app}.'],
  },
  fr: {
    playing: 'EN LECTURE', paused: 'EN PAUSE', notes: 'Notifications', hop: 'Sauter sur les alertes', player: 'Lecteur de musique',
    grant: 'Lui donner l’accès aux notifications…',
    music: ['C’est quoi ? Monte le son.', 'Pas mal. Je reste là-haut.', '♪ {title}… je la connais.'],
    note: ['{app} te cherche.', '{app} : du nouveau.', 'Regarde, {app}.'],
  },
  ru: {
    playing: 'ИГРАЕТ', paused: 'ПАУЗА', notes: 'Уведомления', hop: 'Прыгать на уведомления', player: 'Музыкальный плеер',
    grant: 'Дать доступ к уведомлениям…',
    music: ['Что слушаешь? Сделай громче.', 'Хороший трек. Посижу тут.', '♪ {title}… я её знаю.'],
    note: ['{app} зовёт тебя.', '{app}: что-то новое.', 'Смотри, {app}.'],
  },
};

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const fmt = (s, v) => s.replace(/\{(\w+)\}/g, (_, k) => v[k] ?? '');

export class Notes {
  constructor({ config, lang }) {
    this.config = config; // () => her settings
    this.lang = lang;     // () => 'es' | 'en' | …
    this.media = null;    // { title, artist, app, playing }
    this.pausedAt = 0;
    this.note = null;     // { key, app, title }
    this.noteTimer = 0;
    this.saidAt = 0;
    this.allowed = false;
    this.music = this.card('music');
    this.notice = this.card('note');

    on('media:update', (m) => this.onMedia(m));
    on('note:posted', (n) => this.onNote(n));
    on('note:removed', ({ key } = {}) => key === this.note?.key && this.hideNote());
    window.addEventListener('resize', () => this.place());
    window.visualViewport?.addEventListener('resize', () => this.place());
    this.place();
    // what size her page has on this phone, for the app's log (when a card is off screen)
    send('log', { level: 'I', text: `view ${innerWidth}x${innerHeight} (visible ${Math.round(visualViewport?.height || 0)}), screen ${screen.width}x${screen.height}, dpr ${devicePixelRatio}` });
    call('media:now').then((m) => this.onMedia(m), () => {});
    this.check();
    setInterval(() => this.tick(), 5000);
  }

  get t() {
    return LINES[this.lang()] || LINES.en;
  }

  check() {
    return call('notes:allowed').then((ok) => (this.allowed = Boolean(ok)), () => false);
  }

  // ---------- the cards ----------

  // In with a glitchy slide, out the same way (android.css): never just popping on and off
  show(el) {
    clearTimeout(el._out);
    const was = el.hidden || el.classList.contains('is-out');
    el.classList.remove('is-out');
    el.hidden = false;
    if (was) {
      el.classList.remove('is-in');
      void el.offsetWidth; // restart the animation
      el.classList.add('is-in');
    }
    return was;
  }

  hide(el) {
    if (el.hidden || el.classList.contains('is-out')) return;
    el.classList.remove('is-in');
    el.classList.add('is-out');
    clearTimeout(el._out);
    el._out = setTimeout(() => {
      el.hidden = true;
      el.classList.remove('is-out');
      this.feed(); // she falls off it once it's gone
    }, OUT_MS);
  }

  // the music card for a while, then away (it's not always on screen)
  showMusicFor() {
    clearTimeout(this.musicTimer);
    if (this.config().musicStay === 'always') return; // Ajustes › Barra de música › Siempre: while it plays
    this.musicTimer = setTimeout(() => this.hide(this.music), MUSIC_FOR);
  }

  card(kind) {
    const el = document.createElement('div');
    el.className = `anote anote--${kind}`;
    el.hidden = true;
    el.innerHTML = (kind === 'music' ? '<div class="anote__grip" aria-hidden="true"></div>' : '')
      + `<div class="anote__body"><div class="anote__tag"></div><div class="anote__title"></div><div class="anote__sub"></div></div>`
      + (kind === 'music'
        ? '<div class="anote__ctl"><button data-a="unfold" class="anote__open" aria-label="open">♪</button><button data-a="previous" aria-label="previous">⏮</button><button data-a="toggle" class="anote__play" aria-label="play">⏯</button><button data-a="next" aria-label="next">⏭</button><button data-a="fold" class="anote__fold" aria-label="fold">–</button></div>'
        : '<button class="anote__x" data-a="dismiss" aria-label="dismiss">×</button>');
    if (kind === 'music') this.movable(el);
    el.addEventListener('click', (e) => {
      if (kind === 'music') this.showMusicFor(); // in use: it stays a while longer
      if (el.dataset.dragged) return; // the end of a drag, not a tap
      const a = e.target.closest('[data-a]')?.dataset.a;
      if (kind === 'music') {
        if (a === 'fold' || a === 'unfold') this.fold(a === 'fold');
        else if (a) call('media:control', { action: a }).catch(() => {});
        else call('media:open').catch(() => {});
      } else {
        if (a !== 'dismiss' && this.note) call('note:open', { key: this.note.key }).catch(() => {});
        this.hideNote();
      }
    });
    document.body.appendChild(el);
    return el;
  }

  // Where the cards go, from the top of what's visible (some phones give her window more height
  // than the screen shows): the notice near the top, the music lower down, both well clear of the
  // navigation bar, with room above each for her to sit.
  place() {
    const h = this.visible();
    // the notice up top, out of the middle of your screen, just low enough for her to sit on it
    const scale = Number(this.config().scale) || 0.6;
    this.notice.style.top = `${Math.round(Math.max(40, 232 * scale + 26))}px`;
    const spot = this.saved();
    this.music.classList.toggle('is-folded', Boolean(spot.folded));
    if (spot.x != null && spot.y != null) this.moveMusic(spot.x * innerWidth, spot.y * h);
    else {
      this.music.style.left = '';
      this.music.style.top = `${Math.round(h * 0.58)}px`;
    }
    this.feed();
  }

  visible() {
    return Math.min(innerHeight, window.visualViewport?.height || innerHeight, screen.height || innerHeight);
  }

  // ---------- the music card: moved with a finger, folded to a small chip ----------
  // where you left it and whether it's folded, kept on this phone (a fraction of the screen, so a
  // turn of the phone keeps it in the same part of it)

  saved() {
    try { return JSON.parse(localStorage.getItem(MUSIC_SPOT) || '{}') || {}; } catch { return {}; }
  }

  save(patch) {
    try { localStorage.setItem(MUSIC_SPOT, JSON.stringify({ ...this.saved(), ...patch })); } catch { /* private storage off: it just won't be remembered */ }
  }

  // inside what's visible, whatever its size
  moveMusic(x, y) {
    const el = this.music;
    const w = el.offsetWidth || 300;
    const hgt = el.offsetHeight || 64;
    const left = Math.max(4, Math.min(innerWidth - w - 8, x));
    const top = Math.max(28, Math.min(this.visible() - hgt - 12, y));
    el.style.left = `${Math.round(left)}px`;
    el.style.top = `${Math.round(top)}px`;
    return { left, top };
  }

  fold(on) {
    this.music.classList.toggle('is-folded', on);
    this.save({ folded: on });
    // folded at the right edge, it stays inside the screen
    const r = this.music.getBoundingClientRect();
    this.moveMusic(r.left, r.top);
    this.feed();
  }

  // a finger on the card (not on its buttons) that moves more than a little: the card follows it
  movable(el) {
    let start = null;
    let frame = 0;
    el.addEventListener('pointerdown', (e) => {
      if (e.target.closest('button')) return;
      const r = el.getBoundingClientRect();
      start = { x: e.clientX, y: e.clientY, left: r.left, top: r.top, moving: false };
      delete el.dataset.dragged;
      try { el.setPointerCapture(e.pointerId); } catch { /* fine without it */ }
    });
    el.addEventListener('pointermove', (e) => {
      if (!start) return;
      const dx = e.clientX - start.x;
      const dy = e.clientY - start.y;
      if (!start.moving && Math.hypot(dx, dy) < 10) return;
      start.moving = true;
      el.classList.add('is-moving');
      this.moveMusic(start.left + dx, start.top + dy);
      // she rides along if she's sitting on it
      if (!frame) frame = requestAnimationFrame(() => { frame = 0; emit('windows:update', this.snapshot()); });
    });
    const end = () => {
      if (!start) return;
      if (start.moving) {
        el.dataset.dragged = '1';
        setTimeout(() => delete el.dataset.dragged, 300); // only the click this drag ends is skipped
        el.classList.remove('is-moving');
        const r = el.getBoundingClientRect();
        this.save({ x: r.left / innerWidth, y: r.top / this.visible() });
        this.feed();
      }
      start = null;
    };
    el.addEventListener('pointerup', end);
    el.addEventListener('pointercancel', end);
  }

  fill(el, tag, title, sub) {
    el.querySelector('.anote__tag').textContent = tag;
    el.querySelector('.anote__title').textContent = title;
    el.querySelector('.anote__sub').textContent = sub;
  }

  // ---------- music ----------

  onMedia(m) {
    const before = this.media;
    this.media = m?.title ? m : null;
    if (!this.media || this.config().musicCard === false || this.config().gamer) {
      this.hide(this.music);
      return;
    }
    const { title, artist, app, playing } = this.media;
    if (!playing && !this.pausedAt) this.pausedAt = Date.now();
    if (playing) this.pausedAt = 0;
    this.fill(this.music, `♪ ${playing ? this.t.playing : this.t.paused} · ${app}`, title, artist);
    this.music.classList.toggle('is-paused', !playing);
    // only when something changed (a new song, play, pause): not every update of the same song
    const changed = !before || before.title !== title || before.playing !== playing;
    if (!changed && (this.music.hidden || this.music.classList.contains('is-out'))) return;
    const wasHidden = this.show(this.music);
    if (wasHidden) this.place(); // laid out now: kept inside the screen at its real size
    this.feed();
    if (changed || wasHidden) this.showMusicFor();
    // a new song, or the music starting: she comes to sit on it
    const fresh = playing && (wasHidden || !before?.playing || before.title !== title);
    if (fresh) {
      const line = fmt(pick(this.t.music), { title });
      this.hop('music', () => this.say(line));
    }
  }

  // Gamer mode (host.js): her cards out of the game's way; the music back after it
  gamer(on) {
    if (on) {
      this.hideNote();
      this.hide(this.music);
    } else if (this.media?.playing) this.refreshMusic();
  }

  // Ajustes changed the music bar: shown again now if a song is on, or away
  refreshMusic() {
    if (!this.media || this.config().musicCard === false || this.config().gamer) return this.hide(this.music);
    if (this.show(this.music)) this.place();
    this.feed();
    this.showMusicFor();
  }

  tick() {
    if (this.pausedAt && Date.now() - this.pausedAt > PAUSED_FOR && !this.music.hidden) this.hide(this.music);
  }

  // ---------- notices ----------

  onNote(n) {
    if (!n?.key || this.config().noteHop === false || this.config().gamer) return;
    this.note = n;
    this.fill(this.notice, n.app.toUpperCase(), n.title || n.app, '');
    // a fresh one: the card replays its glitch in, and its bar runs down while it stays
    this.notice.hidden = true;
    this.show(this.notice);
    this.notice.style.setProperty('--stay', `${NOTE_FOR}ms`);
    this.feed();
    clearTimeout(this.noteTimer);
    this.noteTimer = setTimeout(() => this.hideNote(), NOTE_FOR);
    // she just jumps on it: a line of hers would cover the very card it's about
    this.hop('note');
  }

  hideNote() {
    clearTimeout(this.noteTimer);
    this.note = null;
    this.hide(this.notice);
  }

  // ---------- her ----------

  // The cards as windows: what capri.setWindows takes on the desktop.
  snapshot() {
    const wins = [];
    for (const [id, el] of [['note', this.notice], ['music', this.music]]) {
      if (el.hidden || el.classList.contains('is-out')) continue;
      // where it rests, not where its slide-in has it right now (it starts off the right edge: she
      // used to jump there, out of sight, when a notice came in)
      const x = el.offsetLeft, y = el.offsetTop;
      wins.push({ id, x, y, w: el.offsetWidth, h: el.offsetHeight, max: false });
    }
    return { fg: wins[0]?.id || 0, taskbar: false, ai: [], pages: [], dl: false, game: null, usb: [], plug: null, wins };
  }

  feed() {
    requestAnimationFrame(() => emit('windows:update', this.snapshot()));
  }

  // She jumps onto that card if she's free (not held, not talking with you, not asleep or put away).
  hop(id, then) {
    setTimeout(() => {
      const dev = window.dev;
      const capri = dev?.capri;
      if (!capri || capri.busy || capri.dragging || capri.hidden || capri.lounge) return;
      if (capri.perch?.id === id && capri.stance === 'sit') return then?.();
      if (dev.panel?.current && !dev.panel.current.hold) return; // something she's saying to you stays
      if (dev.chat?.open) return;
      if (id === 'music' && this.music.classList.contains('is-folded')) return; // a chip: no room for her
      const win = capri.windows.wins.find((w) => w.id === id);
      if (!win || !capri.canSit(win)) return;
      // on the music card she keeps off its buttons (on its left part, over the song's name)
      const right = id === 'music' ? 190 : 120;
      const dx = Math.max(60, Math.min(win.w - right, win.w * (0.2 + Math.random() * 0.3)));
      Promise.resolve(capri.sitOn({ win, dx })).then(() => then?.());
    }, 250);
  }

  say(line) {
    const panel = window.dev?.panel;
    if (!panel || panel.current || Date.now() - this.saidAt < SAY_EVERY) return;
    this.saidAt = Date.now();
    panel.say({ kind: 'info', face: 'smug', line, hold: 4500, noWink: true }); // the shade already shows it
  }

  // Her menu's entries for this (host.js menuTemplate).
  menu(update) {
    const cfg = this.config();
    const t = this.t;
    return {
      label: t.notes,
      open: () => this.check(),
      submenu: [
        ...(this.allowed ? [] : [{ label: t.grant, click: () => send('notes:settings') }]),
        { label: t.hop, type: 'checkbox', checked: cfg.noteHop !== false, enabled: this.allowed, click: (item) => update({ noteHop: item.checked }) },
        {
          label: t.player, type: 'checkbox', checked: cfg.musicCard !== false, enabled: this.allowed,
          click: (item) => {
            update({ musicCard: item.checked });
            this.onMedia(this.media);
          },
        },
      ],
    };
  }
}
