// Her winks in your notification shade: what she says on screen also goes to her notification
// (CapriService.wink), with her face, so pulling the shade down shows her last word.

import { Panel } from '../capri/src/renderer/js/panel.js';
import { send } from './native.js';

const say = Panel.prototype.say;
let last = '';
let lastAt = 0;

Panel.prototype.say = function wink(msg) {
  const shown = say.call(this, msg);
  const line = typeof msg?.line === 'string' ? msg.line.trim() : '';
  // her own words only: not a progress line, not a box to type into
  if (shown !== false && line && msg.kind !== 'work' && !msg.chat && !msg.progress && line !== last && performance.now() - lastAt > 1500) {
    last = line;
    lastAt = performance.now();
    send('notify:wink', { line, face: msg.face || '' });
  }
  return shown;
};
