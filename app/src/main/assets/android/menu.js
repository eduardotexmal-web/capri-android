// Her menu. On the desktop it's the tray icon's menu, native, also opened by right-clicking her.
// A phone has no tray menus: the same items (built in host.js exactly as main.js builds them) are
// drawn here as one of her own HUD plates, near where you pressed her. Submenus open in place,
// with a back row at the top.

const ARROW = '›';

export class Menu {
  constructor() {
    this.el = document.createElement('nav');
    this.el.className = 'cmenu';
    this.el.hidden = true;
    this.el.setAttribute('role', 'menu');
    this.shade = document.createElement('div');
    this.shade.className = 'cmenu-shade';
    this.shade.hidden = true;
    this.shade.addEventListener('click', () => this.close());
    const add = () => document.body.append(this.shade, this.el);
    if (document.body) add();
    else document.addEventListener('DOMContentLoaded', add, { once: true });
    this.stack = [];
    this.at = null;
  }

  get isOpen() {
    return !this.el.hidden;
  }

  // `build`: () => the items, called again when a submenu has looked something up
  open(build, at) {
    this.build = build;
    this.stack = [{ items: build() }];
    this.at = at || null;
    this.render();
    this.el.hidden = false;
    this.shade.hidden = false;
  }

  close() {
    this.el.hidden = true;
    this.shade.hidden = true;
    this.stack = [];
  }

  render() {
    const level = this.stack.at(-1);
    this.el.replaceChildren();
    const list = document.createElement('ul');
    list.className = 'cmenu__list';
    if (this.stack.length > 1) {
      const back = this.row({ label: level.title }, 'back');
      back.addEventListener('click', () => {
        this.stack.pop();
        this.render();
      });
      list.append(back);
    }
    for (const item of level.items) {
      if (item.type === 'separator') {
        const sep = document.createElement('li');
        sep.className = 'cmenu__sep';
        sep.setAttribute('role', 'separator');
        list.append(sep);
        continue;
      }
      const row = this.row(item);
      if (item.enabled !== false) row.addEventListener('click', () => this.pick(item));
      list.append(row);
    }
    this.el.append(list);
    this.place();
  }

  row(item, kind = '') {
    const li = document.createElement('li');
    li.className = 'cmenu__item';
    if (kind === 'back') li.classList.add('is-back');
    if (item.enabled === false) li.classList.add('is-off');
    li.setAttribute('role', item.type === 'checkbox' ? 'menuitemcheckbox' : item.type === 'radio' ? 'menuitemradio' : 'menuitem');
    const mark = document.createElement('span');
    mark.className = 'cmenu__mark';
    if (kind === 'back') mark.textContent = '‹';
    else if (item.type === 'checkbox') mark.textContent = item.checked ? '■' : '□';
    else if (item.type === 'radio') mark.textContent = item.checked ? '◆' : '◇';
    if ('checked' in item) li.setAttribute('aria-checked', String(Boolean(item.checked)));
    const label = document.createElement('span');
    label.className = 'cmenu__label';
    label.textContent = item.label ?? '';
    li.append(mark, label);
    if (item.submenu) {
      const more = document.createElement('span');
      more.className = 'cmenu__more';
      more.textContent = ARROW;
      li.append(more);
    }
    return li;
  }

  async pick(item) {
    if (item.submenu) {
      this.stack.push({ items: item.submenu, title: item.label, source: item });
      this.render();
      if (item.open) {
        // something to look up first (her models): drawn again once it's there
        await item.open().catch(() => {});
        if (this.isOpen) this.rebuild();
      }
      return;
    }
    if (item.type === 'checkbox') item.checked = !item.checked;
    this.close();
    item.click?.(item);
  }

  // the same levels again, from fresh items, found by their titles
  rebuild() {
    const titles = this.stack.slice(1).map((l) => l.title);
    let items = this.build();
    const stack = [{ items }];
    for (const title of titles) {
      const sub = items.find((i) => i.submenu && i.label === title);
      if (!sub) break;
      items = sub.submenu;
      stack.push({ items, title, source: sub });
    }
    this.stack = stack;
    this.render();
  }

  // near the finger, kept on screen; on a narrow screen, across its width
  place() {
    const r = this.el.getBoundingClientRect();
    const w = r.width || 280;
    const h = Math.min(r.height || 400, innerHeight - 32);
    const at = this.at || { x: innerWidth / 2, y: innerHeight / 2 };
    const x = Math.max(12, Math.min(innerWidth - w - 12, at.x - w / 2));
    const y = Math.max(16, Math.min(innerHeight - h - 16, at.y - h / 3));
    this.el.style.left = `${Math.round(x)}px`;
    this.el.style.top = `${Math.round(y)}px`;
  }
}
