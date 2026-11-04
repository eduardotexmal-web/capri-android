// Her setup (setup.js) on a phone: no terminal anywhere.
//
// On the desktop the first choice is Ollama on this PC, which she installs with winget. A phone has
// neither, so here the first choice is the free online AI (Pollinations, one tap), then a key
// (Groq and Gemini are free, the page to get one opens in the browser), then the Ollama of your PC
// over Wi-Fi, by its address. Her panels and the rest of setup.js are the desktop's, unchanged:
// this only swaps the steps that would need a terminal, and the lines that talk about a PC.

import { Setup } from '../capri/src/renderer/js/setup.js';

const LINES = {
  es: {
    whoHow: 'Lo más fácil: en línea, gratis y sin cuenta. Un toque y listo.\nSi quieres algo mejor: una clave gratis de Groq o Gemini, o el Ollama de tu PC por Wi-Fi.',
    why: { noOllama: 'No encuentro el Ollama de tu PC.', asleep: 'No encuentro el Ollama de tu PC.', noModel: 'El Ollama de tu PC aún no tiene ningún modelo.' },
    online: 'Gratis, sin cuenta', local: 'Ollama de mi PC',
    pasteHow: 'Toca «¿Dónde la consigo?», crea la clave en el navegador, cópiala y pégala aquí. La guardo cifrada en este teléfono.',
    readyHow: 'Tócame cuando quieras charlar.',
    pcLine: '¿Cuál es la IP de tu PC?',
    pcHow: 'En tu PC: abre Ollama, en sus ajustes activa «Expose Ollama to the network», y escribe aquí la IP del PC. Los dos en el mismo Wi-Fi.',
    pcNote: 'La IP sale en el PC en Configuración › Red. Algo como 192.168.1.20.',
    pcBad: 'No contesta en esa dirección.',
  },
  en: {
    whoHow: 'Easiest: online, free, no account. One tap and done.\nFor something better: a free key from Groq or Gemini, or your PC’s Ollama over Wi-Fi.',
    why: { noOllama: 'I can’t find your PC’s Ollama.', asleep: 'I can’t find your PC’s Ollama.', noModel: 'Your PC’s Ollama has no model yet.' },
    online: 'Free, no account', local: 'My PC’s Ollama',
    pasteHow: 'Tap “Where do I get one?”, make the key in the browser, copy it and paste it here. I keep it encrypted on this phone.',
    readyHow: 'Tap me whenever you want to chat.',
    pcLine: 'What’s your PC’s IP?',
    pcHow: 'On your PC: open Ollama, turn on “Expose Ollama to the network” in its settings, and type the PC’s IP here. Both on the same Wi-Fi.',
    pcNote: 'The PC shows its IP in Settings › Network. Something like 192.168.1.20.',
    pcBad: 'Nothing answers at that address.',
  },
  pt: {
    whoHow: 'O mais fácil: online, grátis e sem conta. Um toque e pronto.\nPra algo melhor: uma chave grátis do Groq ou do Gemini, ou o Ollama do seu PC pelo Wi-Fi.',
    why: { noOllama: 'Não encontro o Ollama do seu PC.', asleep: 'Não encontro o Ollama do seu PC.', noModel: 'O Ollama do seu PC ainda não tem nenhum modelo.' },
    online: 'Grátis, sem conta', local: 'Ollama do meu PC',
    pasteHow: 'Toque em “Onde eu consigo?”, crie a chave no navegador, copie e cole aqui. Eu guardo ela criptografada neste celular.',
    readyHow: 'Me toque quando quiser conversar.',
    pcLine: 'Qual é o IP do seu PC?',
    pcHow: 'No seu PC: abra o Ollama, ative “Expose Ollama to the network” nas configurações dele e digite aqui o IP do PC. Os dois no mesmo Wi-Fi.',
    pcNote: 'O IP aparece no PC em Configurações › Rede. Algo como 192.168.1.20.',
    pcBad: 'Nada responde nesse endereço.',
  },
  fr: {
    whoHow: 'Le plus simple : en ligne, gratuit et sans compte. Un geste et c’est fait.\nPour mieux : une clé gratuite de Groq ou Gemini, ou l’Ollama de ton PC par le Wi-Fi.',
    why: { noOllama: 'Je ne trouve pas l’Ollama de ton PC.', asleep: 'Je ne trouve pas l’Ollama de ton PC.', noModel: 'L’Ollama de ton PC n’a pas encore de modèle.' },
    online: 'Gratuit, sans compte', local: 'L’Ollama de mon PC',
    pasteHow: 'Touche « Où la trouver ? », crée la clé dans le navigateur, copie-la et colle-la ici. Je la garde chiffrée sur ce téléphone.',
    readyHow: 'Touche-moi quand tu veux parler.',
    pcLine: 'Quelle est l’IP de ton PC ?',
    pcHow: 'Sur ton PC : ouvre Ollama, active « Expose Ollama to the network » dans ses réglages, et tape ici l’IP du PC. Les deux sur le même Wi-Fi.',
    pcNote: 'Le PC affiche son IP dans Paramètres › Réseau. Quelque chose comme 192.168.1.20.',
    pcBad: 'Rien ne répond à cette adresse.',
  },
  ru: {
    whoHow: 'Проще всего: онлайн, бесплатно и без аккаунта. Одно касание, и готово.\nЕсли хочешь лучше: бесплатный ключ Groq или Gemini, или Ollama на твоём ПК по Wi-Fi.',
    why: { noOllama: 'Не нахожу Ollama на твоём ПК.', asleep: 'Не нахожу Ollama на твоём ПК.', noModel: 'В Ollama на твоём ПК пока нет модели.' },
    online: 'Бесплатно, без аккаунта', local: 'Ollama на моём ПК',
    pasteHow: 'Нажми «Где его взять?», создай ключ в браузере, скопируй и вставь сюда. Я храню его зашифрованным на этом телефоне.',
    readyHow: 'Коснись меня, когда захочешь поболтать.',
    pcLine: 'Какой IP у твоего ПК?',
    pcHow: 'На ПК: открой Ollama, включи в настройках «Expose Ollama to the network» и введи здесь IP ПК. Оба в одной сети Wi-Fi.',
    pcNote: 'IP видно на ПК в Параметры › Сеть. Что-то вроде 192.168.1.20.',
    pcBad: 'По этому адресу никто не отвечает.',
  },
};

const base = Object.getOwnPropertyDescriptor(Setup.prototype, 't').get;

Object.defineProperty(Setup.prototype, 't', {
  configurable: true,
  get() {
    const t = base.call(this);
    const a = LINES[this.brain.lang] || LINES.en;
    return { ...t, ...a, why: { ...t.why, ...a.why } };
  },
});

// Who answers her: the free online one first, a key, or your PC's Ollama. True once she can talk.
Setup.prototype.choose = async function choose(status) {
  const t = this.t;
  let why = '';
  if (status && !status.ok) {
    if (status.code === 'nokey') return this.paste(status.provider);
    if (status.code === 'nourl') return this.address();
    why = status.provider === 'ollama' ? t.why.noOllama : `${t.failed} ${status.error}`;
  }
  this.capri.perform('curious', this.capri.towardCenter());
  const id = await this.guide.ask({
    face: 'smug',
    line: t.who,
    quote: [why, t.whoHow].filter(Boolean).join('\n'),
    actions: [
      { id: 'online', label: t.online, primary: true },
      { id: 'key', label: t.key },
      { id: 'local', label: t.local },
      { id: 'later', label: t.later },
    ],
  });
  if (id === 'online') return this.use('pollinations');
  if (id === 'key') return this.pickKeyed();
  if (id === 'local') return this.ollama();
  return false;
};

// Ollama awake: on a phone that's your PC's, found by its address. True once it answers.
Setup.prototype.ollamaUp = async function ollamaUp(error = '') {
  const t = this.t;
  if (!error && (await this.host.ollamaState()).running) return true;
  const got = await this.guide.askText({
    face: error ? 'wide' : 'look',
    line: t.pcLine,
    quote: error ? `${t.pcBad} ${error}` : t.pcHow,
    placeholder: '192.168.1.20',
    tag: 'IP',
    note: t.pcNote,
    actions: [{ id: 'other', label: t.other }],
  });
  if (got?.action === 'other') {
    await this.choose(null);
    return false;
  }
  if (!got?.text) return false;
  this.working(t.checking);
  const state = await this.host.setOllamaHost(got.text);
  return state.running ? true : this.ollamaUp(state.host);
};
