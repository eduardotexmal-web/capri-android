// "Capri, …": what you say to her out loud (Ears.kt hears it, only while you turned it on in the
// app's Settings and the screen is on). The phone's own quick things first (the time, the date,
// the flashlight, hide, come back, her chat), then whatever her chat already does when you type it
// (music, volume, an app, a timer, a reminder, the battery…), and anything else is a question for
// her AI. She shows her answer in her panel and says it out loud.

import { on, send, call } from './native.js';
import { clean, mood } from '../capri/src/renderer/js/chat.js';

const WORDS = {
  es: {
    yes: ['¿Sí?', 'Dime.', 'Aquí estoy.', '¿Qué se te ofrece, choom?'],
    time: 'Son las {time}.',
    date: 'Hoy es {date}.',
    torchOn: 'Linterna encendida.',
    torchOff: 'Linterna apagada.',
    noTorch: 'Este teléfono no me deja usar la linterna.',
    back: 'Aquí estoy.',
    bye: 'Me escondo. Dime «Capri, ven» y regreso.',
    deaf: 'Dejo de escuchar. Lo vuelves a activar en Ajustes.',
    thinking: '…',
    failed: 'No pude contestarte ahora.',
  },
  en: {
    yes: ['Yes?', 'Go on.', 'Right here.', 'What do you need, choom?'],
    time: "It's {time}.",
    date: 'Today is {date}.',
    torchOn: 'Flashlight on.',
    torchOff: 'Flashlight off.',
    noTorch: "This phone won't let me use the flashlight.",
    back: 'Right here.',
    bye: "I'll hide. Say “Capri, come back” and I'm here.",
    deaf: "I'll stop listening. Turn it back on in Settings.",
    thinking: '…',
    failed: "I couldn't answer you right now.",
  },
};

// the phone's own quick things, in Spanish and English
const TIME_ASK = /qu[eé] hora|la hora|what time|the time/i;
const DATE_ASK = /qu[eé] d[ií]a|qu[eé] fecha|la fecha|what day|the date|what's the date/i;
const TORCH = /linterna|l[aá]mpara|flash|torch|flashlight/i;
const OFF = /apaga|desactiva|quita|turn off|switch off|\boff\b/i;
const ON = /enciende|prende|activa|pon|turn on|switch on|\bon\b/i;
const HIDE_ASK = /^(?:esc[oó]ndete|vete|desaparece|ocúltate|ocultate|hide|go away|get lost)\b/i;
const SHOW_ASK = /^(?:ven|aparece|regresa|vuelve|mu[eé]strate|sal|come back|come here|show up|appear)\b/i;
const DEAF_ASK = /deja de escuchar|no me escuches|stop listening/i;
const CHAT_ASK = /^(?:abre (?:el |tu )?chat|hablemos|quiero hablar|platiquemos|open (?:the |your )?chat|let'?s talk)\b/i;
// what's left of her name at the start, as the recognizer may write it
// (and whatever came before it: "oye Capri", "ok Kapri")
const NAME = /^.*?(?<![\p{L}])[ck]a[pb]r[iíy]s?(?![\p{L}])[\s,.!?:]*/iu;

let busy = 0;

export function listen({ toggleVisible, visible }) {
  on('capri:heard', (heard) => {
    // one at a time, but never stuck on one that never finished
    if (!window.dev || (busy && Date.now() - busy < 20000)) return;
    busy = Date.now();
    handle(String(heard?.text || '').replace(NAME, '').trim(), { toggleVisible, visible })
      .catch((err) => console.error('[capri] voice', err))
      .finally(() => { busy = 0; });
  });
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const fill = (line, vars) => line.replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? '');

async function handle(text, { toggleVisible, visible }) {
  const dev = window.dev;
  const { capri, panel, chat, brain } = dev;
  const lang = brain.lang === 'es' ? 'es' : brain.lang === 'en' ? 'en' : 'es';
  const t = WORDS[lang];
  const locale = lang === 'es' ? 'es-MX' : 'en-US';

  // her answer: in her panel, and out loud
  const answer = (line, face = 'smug', quote = text, aloud = true) => {
    if (!chat.open) panel.say({ kind: 'info', face, line, quote: quote || '', hold: 5000 + line.length * 45 });
    if (aloud) send('voice:say', { text: spoken(line), lang: locale });
  };

  if (HIDE_ASK.test(text)) {
    answer(t.bye, 'aside');
    setTimeout(() => toggleVisible(false), 1800);
    return;
  }
  // anything else, she's out for it
  if (!visible()) {
    toggleVisible(true);
    await new Promise((r) => setTimeout(r, 900));
    if (!text || SHOW_ASK.test(text)) return answer(t.back, 'laugh', '');
  }
  capri.wake?.();

  // just her name: she listens for what comes next (Ears.kt keeps the mic open a few seconds)
  if (!text) {
    // shown, not said: her voice would cover what you say next (her ears stay open for it)
    capri.perform?.('hop');
    window.dev.voice?.play?.('tick');
    return answer(pick(t.yes), 'look', '', false);
  }
  if (SHOW_ASK.test(text)) return answer(t.back, 'laugh');
  if (DEAF_ASK.test(text)) {
    answer(t.deaf, 'aside');
    send('voice:off');
    return;
  }
  if (TIME_ASK.test(text) && text.split(/\s+/).length <= 8) {
    const time = new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit' }).format(new Date());
    return answer(fill(t.time, { time }).replace(/\.\.$/, '.'), 'look');
  }
  if (DATE_ASK.test(text) && text.split(/\s+/).length <= 8) {
    const date = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
    return answer(fill(t.date, { date }), 'look');
  }
  if (TORCH.test(text)) {
    const want = OFF.test(text) ? false : ON.test(text) ? true : null;
    const res = await Promise.race([call('voice:torch', { on: want }), new Promise((r) => setTimeout(() => r(null), 4000))]).catch(() => null);
    if (res == null) return answer(t.noTorch, 'aside');
    capri.pulse?.();
    return answer(res ? t.torchOn : t.torchOff, 'smug');
  }
  if (CHAT_ASK.test(text)) return chat.start();

  // her chat open: what you said goes into it, as if you typed it
  if (chat.open) return chat.send(text);

  // what her chat does right away when you ask it plainly (music, volume, an app, a timer…)
  const asked = chat.asked(text);
  // "Capri, mira mi pantalla": her look (Android asks first)
  if (asked?.kind === 'look' || /mira (?:mi|la) pantalla|qu[ée] (?:ves|hay en mi pantalla)|look at my screen|what do you see/i.test(text)) {
    return dev.look?.once();
  }
  if (asked && !['look', 'close', 'files', 'shot'].includes(asked.kind)) {
    chat.listApps();
    const done = await chat.doNow(asked).catch(() => null);
    if (done?.line) return answer(done.line, done.face || 'smug');
  }

  // anything else: a question for her AI, answered short, to be said out loud
  const status = await window.host.chatStatus().catch(() => ({ ok: false }));
  if (!status.ok) return chat.start(); // nothing to talk with yet: her setup
  panel.say({ kind: 'info', face: 'aside', line: t.thinking, quote: text, hold: null });
  const id = Date.now();
  const note = `(Note for you, never repeat it: this was said out loud and your answer will be read out loud, so answer in one to three short sentences, plain words, no lists. ${chat.languageNote()})`;
  const messages = [
    { role: 'system', content: chat.persona() },
    ...chat.history.slice(-20),
    { role: 'user', content: `${text}\n\n${note}` },
  ];
  const res = await window.host.chatSend({ id, messages }).catch((err) => ({ ok: false, error: err.message }));
  if (!res?.ok) return answer(t.failed, 'wide');
  const said = clean(res.text) || '…';
  chat.history.push({ role: 'user', content: text });
  chat.remember({ role: 'assistant', content: said });
  answer(said, mood(said));
}

// what TTS reads: no tags, no markdown, no emoji
function spoken(line) {
  return String(line)
    .replace(/\[\[[^\]]*\]\]/g, '')
    .replace(/[*_`#>~]/g, '')
    .replace(/[\p{Extended_Pictographic}\u{FE0F}]/gu, '')
    .replace(/\s+/g, ' ')
    .trim();
}
