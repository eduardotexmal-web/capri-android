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
const PAUSED_FOR = 90 * 1000; // music paused this long: its card goes
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

  card(kind) {
    const el = document.createElement('div');
    el.className = `anote anote--${kind}`;
    el.hidden = true;
    el.innerHTML = `<div class="anote__body"><div class="anote__tag"></div><div class="anote__title"></div><div class="anote__sub"></div></div>`
      + (kind === 'music'
        ? '<div class="anote__ctl"><button data-a="previous" aria-label="previous">⏮</button><button data-a="toggle" class="anote__play" aria-label="play">⏯</button><button data-a="next" aria-label="next">⏭</button></div>'
        : '<button class="anote__x" data-a="dismiss" aria-label="dismiss">×</button>');
    el.addEventListener('click', (e) => {
      const a = e.target.closest('[data-a]')?.dataset.a;
      if (kind === 'music') {
        if (a) call('media:control', { action: a }).catch(() => {});
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
  // than the screen shows): the notice up in the middle, the music lower down, both well clear of
  // the navigation bar, with room above each for her to sit.
  place() {
    const h = Math.min(innerHeight, window.visualViewport?.height || innerHeight, screen.height || innerHeight);
    this.notice.style.top = `${Math.round(h * 0.3)}px`;
    this.music.style.top = `${Math.round(h * 0.58)}px`;
    this.feed();
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
    if (!this.media || this.config().musicCard === false) {
      this.music.hidden = true;
      this.feed();
      return;
    }
    const { title, artist, app, playing } = this.media;
    if (!playing && !this.pausedAt) this.pausedAt = Date.now();
    if (playing) this.pausedAt = 0;
    this.fill(this.music, `♪ ${playing ? this.t.playing : this.t.paused} · ${app}`, title, artist);
    this.music.classList.toggle('is-paused', !playing);
    const wasHidden = this.music.hidden;
    this.music.hidden = false;
    this.feed();
    // a new song, or the music starting: she comes to sit on it
    const fresh = playing && (wasHidden || !before?.playing || before.title !== title);
    if (fresh) {
      const line = fmt(pick(this.t.music), { title });
      send('notify:wink', { line: `${line}\n♪ ${title}${artist ? ` · ${artist}` : ''} (${app})`, face: 'smug' });
      this.hop('music', () => this.say(line));
    }
  }

  tick() {
    if (this.pausedAt && Date.now() - this.pausedAt > PAUSED_FOR && !this.music.hidden) {
      this.music.hidden = true;
      this.feed();
    }
  }

  // ---------- notices ----------

  onNote(n) {
    if (!n?.key || this.config().noteHop === false) return;
    this.note = n;
    this.fill(this.notice, n.app.toUpperCase(), n.title || n.app, '');
    this.notice.hidden = false;
    // a fresh one: the card replays its glitch in
    this.notice.classList.remove('is-new');
    void this.notice.offsetWidth;
    this.notice.classList.add('is-new');
    this.feed();
    clearTimeout(this.noteTimer);
    this.noteTimer = setTimeout(() => this.hideNote(), NOTE_FOR);
    const line = fmt(pick(this.t.note), { app: n.app });
    send('notify:wink', { line, face: 'look' });
    this.hop('note', () => this.say(line));
  }

  hideNote() {
    clearTimeout(this.noteTimer);
    this.note = null;
    this.notice.hidden = true;
    this.feed();
  }

  // ---------- her ----------

  // The cards as windows: what capri.setWindows takes on the desktop.
  snapshot() {
    const wins = [];
    for (const [id, el] of [['note', this.notice], ['music', this.music]]) {
      if (el.hidden) continue;
      const r = el.getBoundingClientRect();
      wins.push({ id, x: r.left, y: r.top, w: r.width, h: r.height, max: false });
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
    panel.say({ kind: 'info', face: 'smug', line, hold: 4500 });
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
