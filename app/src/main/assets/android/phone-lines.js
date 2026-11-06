// Her lines, for a phone: the desktop's words (right-click, Ctrl+Alt+C, your PC, your windows,
// Windows) said the way they work here. Applied over lines.js before her page reads them, in the
// languages the app speaks (Spanish and English); the others keep the desktop's.

import { LINES } from '../capri/src/renderer/js/lines.js';

const PHONE = {
  es: {
    'chat.gone': 'Ese modelo ya no está disponible allí. Elige otro: mantén pulsado sobre mí › Modelo de IA.',
    'chat.notFound': 'No veo «{app}» abierta en tu teléfono.',
    'chat.notInstalled': 'No encuentro «{app}» en tu teléfono.',
    'chat.filesNone': 'Nada para «{query}» en tu teléfono.',
    'chat.offlineHow': 'Para hablar conmigo sin internet, abre Ollama en tu PC y conéctame por Wi-Fi desde la app (Ajustes › Su IA).',
    'showcase.possess': 'Esta tarjeta ahora es mía.',
    'showcase.chip': 'Toca «Guardarla» en mi notificación y me guardo aquí.',
    'guide.icon.quote': 'Mi notificación es mi panel: Menú, Guardarla y Salir. Mantenerme pulsada abre el mismo menú.',
    'guide.icon.hidden': 'Baja la barra de notificaciones: ahí estoy. O mantenme pulsada: es el mismo menú.',
    'guide.folder.hookHow': 'Para desconectarme: mantenme pulsada › «Quitar los hooks de Claude Code». Los archivos .capri-backup son copias hechas antes de que lo tocara.',
    'custom.savedHow': 'Puedes cambiarlo todo cuando quieras: mantenme pulsada › Personalizarla.',
    'welcome.alone': 'Aquí no hay Claude Code ni trabajo: me quedo en tu teléfono contigo.',
    'welcome.about': 'Vivo en tu teléfono, encima de tus apps, y reacciono a lo que hace Claude Code (en Termux o en tu PC por Wi-Fi). Sin él, simplemente ando por aquí, y también está bien.',
    'welcome.connect': '¿Quieres que me conecte a Claude Code? Un toque, y luego cierra Claude Code por completo y vuelve a abrirlo.',
    'welcome.noClaude': 'No encuentro Claude Code en este teléfono (en Termux, y necesita una suscripción de Claude). Sin él, me quedo aquí viviendo mi vida.',
    'welcome.menu': 'Mantenme pulsada para abrir mi menú. «Guardarla», en mi notificación, me guarda.',
    'welcome.readyHow': 'Cuando trabaje, te enseño lo que hace. Mantenme pulsada para mi menú.',
    'welcome.afterInstall': 'Cuando lo tengas, mantenme pulsada › «Instalar los hooks de Claude Code».',
    'online': ['Capri en línea.', 'De vuelta en tu pantalla, choom.'],
    'thrown.ledge': ['La atrapé. Buen tiro.', 'Esa tarjeta es mía.'],
    'motion.line': 'Tu teléfono tiene las animaciones desactivadas, así que apenas me muevo.',
    'setup.readyHow': 'Tócame cuando quieras charlar.',
    'look.noAI': 'Necesito una IA para mirar: mantenme pulsada › Modelo de IA.',
    'look.noVision': '{model} no puede ver imágenes. Para que mire tu pantalla ponme Gemini (su clave es gratis) u otra IA que vea: mantenme pulsada › Modelo de IA.',
  },
  en: {
    'chat.gone': 'That model isn’t available there anymore. Pick another one: long-press me › AI model.',
    'chat.notFound': 'I don’t see “{app}” open on your phone.',
    'chat.notInstalled': 'I can’t find “{app}” on your phone.',
    'chat.filesNone': 'Nothing for “{query}” on your phone.',
    'chat.offlineHow': 'To talk with me without internet, open Ollama on your PC and connect me over Wi-Fi from the app (Settings › Her AI).',
    'showcase.possess': 'This card’s mine now.',
    'showcase.chip': 'Tap “Put away” in my notification and I tuck myself away here.',
    'guide.icon.quote': 'My notification is my panel: Menu, Put away and Quit. Long-pressing me opens the same menu.',
    'guide.icon.hidden': 'Pull down your notifications: I’m in there. Or long-press me: same menu.',
    'guide.folder.hookHow': 'To unplug me: long-press me › “Remove Claude Code hooks”. The .capri-backup files are copies made before I touched it.',
    'custom.savedHow': 'You can change any of it later: long-press me › Make her yours.',
    'welcome.alone': 'No Claude Code here, no work: I’ll just hang out on your phone with you.',
    'welcome.about': 'I live on your phone, over your apps, and react to what Claude Code does (in Termux, or on your PC over Wi-Fi). Without it, I just hang out here, and that’s fine too.',
    'welcome.connect': 'Want me to plug into Claude Code? One tap, then close Claude Code completely and open it again.',
    'welcome.noClaude': 'I can’t find Claude Code on this phone (in Termux, and it needs a Claude subscription). Without it, I’ll just hang out and live my life.',
    'welcome.menu': 'Long-press me for my menu. “Put away”, in my notification, tucks me away.',
    'welcome.readyHow': 'Whenever it works, I’ll show you what it’s doing. Long-press me for my menu.',
    'welcome.afterInstall': 'Once you have it, long-press me › “Install Claude Code hooks”.',
    'online': ['Capri online.', 'Back on your screen, choom.'],
    'thrown.ledge': ['Caught it. Nice throw.', 'Got that card.'],
    'motion.line': 'Your phone has its animations turned off, so I’m barely moving.',
    'setup.readyHow': 'Tap me whenever you want to chat.',
    'look.noAI': 'I need an AI to look: long-press me › AI model.',
    'look.noVision': '{model} can’t see pictures. To look at your screen, give me Gemini (its key is free) or another AI that sees: long-press me › AI model.',
  },
};

for (const [lang, lines] of Object.entries(PHONE)) {
  for (const [path, value] of Object.entries(lines)) {
    const keys = path.split('.');
    const last = keys.pop();
    const at = keys.reduce((o, k) => o?.[k], LINES[lang]);
    if (at && last in at) at[last] = value;
  }
}
