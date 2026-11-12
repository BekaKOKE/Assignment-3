'use strict';

/* ==========================================================================
   File Manager UI. Plain JavaScript, no build step.
   Every piece of text from the server is inserted as a text node (never as HTML).
   ========================================================================== */

const FILE_TYPES = ['txt', 'md', 'json', 'csv', 'xml', 'log'];
const LOG_POLL_MS = 1500;
const LOG_LIMIT = 200;
const MAX_NAME_LENGTH = 100;

const STORAGE_INFO = {
  LOCAL: {
    label: 'Local disk',
    icon: 'hard-drives',
    hint: 'File content is kept as regular files on this computer.',
  },
  DATABASE: {
    label: 'Database',
    icon: 'database',
    hint: 'File content is kept inside the SQLite database.',
  },
};

const FILE_ICONS = {
  txt: 'file-txt',
  md: 'file-md',
  json: 'file-code',
  csv: 'file-csv',
  xml: 'file-code',
  log: 'file-text',
};

// [past tense for allowed actions, gerund for blocked ones]
const VERBS = {
  READ: ['read', 'reading'],
  RENAME: ['renamed', 'renaming'],
  DELETE: ['deleted', 'deleting'],
  SHARE: ['shared', 'sharing'],
  MOVE: ['moved', 'moving'],
  CREATE: ['created', 'creating'],
  VIEW: ['viewed', 'viewing'],
  'MOVE a file into': ['moved a file into', 'moving a file into'],
};

const DENIAL_REASONS = {
  VIEWER: 'Viewers can only read files.',
  'NO ACCESS': 'The file is not shared with them.',
  'NOT OWNER': 'Only the folder owner can do this.',
  'UNKNOWN USER': 'This user is not known.',
};

const FORBIDDEN_IN_NAME = /[\\/:*?"<>|\u0000-\u001f]/;

const state = {
  user: null,
  users: [],
  storage: '',
  storages: [],
  folders: [],
  files: [],
  folderId: null,
  loading: true,
  loadError: '',
  switching: false,
  names: new Map(), // id -> display name, kept so the activity panel can name files that were deleted
  log: [],
  logFilter: 'all',
  rawLog: false,
  logLoaded: false,
  lastLogSeq: 0,
};

/* ---------- small helpers ---------- */

const $ = (id) => document.getElementById(id);

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs)) {
    if (value === false || value === null || value === undefined) continue;
    if (name.startsWith('on')) node.addEventListener(name.slice(2), value);
    else if (name === 'class') node.className = value;
    else node.setAttribute(name, value === true ? '' : value);
  }
  node.append(...children.flat().filter((child) => child !== false && child !== null && child !== undefined));
  return node;
}

const SVG_NS = 'http://www.w3.org/2000/svg';

function icon(name) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('class', 'icon');
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(SVG_NS, 'use');
  use.setAttribute('href', `/icons.svg#i-${name}`);
  svg.append(use);
  return svg;
}

function userName(id) {
  return state.users.find((user) => user.id === id)?.displayName ?? id;
}

function initials(name) {
  return (name || '?').trim().charAt(0).toUpperCase();
}

function avatar(name) {
  return el('span', { class: 'avatar', 'aria-hidden': 'true' }, initials(name));
}

function capitalize(text) {
  const lower = String(text).toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const DATE_SHORT = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
const DATE_LONG = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

function formatDate(iso) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return (date.getFullYear() === new Date().getFullYear() ? DATE_SHORT : DATE_LONG).format(date);
}

function storageLabel(type) {
  return STORAGE_INFO[String(type).toUpperCase()]?.label ?? String(type).toLowerCase();
}

function rememberNames() {
  for (const file of state.files) state.names.set(file.id, file.displayName);
  for (const folder of state.folders) state.names.set(folder.id, folder.name);
}

function folderName(id) {
  return state.folders.find((folder) => folder.id === id)?.name ?? "another user's folder";
}

/* ---------- server calls ---------- */

async function api(method, path, form) {
  const options = { method, headers: { 'X-User': state.user || '' } };
  if (form) {
    options.headers['Content-Type'] = 'application/x-www-form-urlencoded';
    options.body = new URLSearchParams(form).toString();
  }
  let response;
  try {
    response = await fetch(`/api/${path}`, options);
  } catch {
    throw new Error('Cannot reach the server. Check that the application is still running.');
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.error || `Request failed (HTTP ${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return data;
}

function fileUrl(file, action = '') {
  return `files/${encodeURIComponent(file.id)}${action}`;
}

/* ---------- toasts ---------- */

const TOAST_ICONS = { ok: 'check-circle', deny: 'prohibit', error: 'warning-circle', info: 'info' };

function toast(kind, title, message = '') {
  const container = $('toasts');
  const node = el('div', { class: `toast ${kind}` },
    icon(TOAST_ICONS[kind]),
    el('div', { class: 'toast-body' },
      el('div', { class: 'toast-title' }, title),
      message && el('div', { class: 'toast-text' }, message)),
    el('button', {
      type: 'button',
      class: 'icon-btn sm',
      'aria-label': 'Dismiss message',
      onclick: () => node.remove(),
    }, icon('x')));
  // a new refusal replaces the previous one, so repeated attempts do not pile up
  if (kind === 'deny') container.querySelectorAll('.toast.deny').forEach((old) => old.remove());
  container.append(node);
  while (container.children.length > 3) container.firstChild.remove();
  setTimeout(() => node.remove(), kind === 'deny' ? 8000 : 5000);
}

function showError(error) {
  if (error.status === 403) {
    toast('deny', 'Blocked by the Protection Proxy',
      `${error.message} The request was stopped before it reached the file manager.`);
  } else if (error.status === 400 || error.status === 409) {
    // a rule of the system said no (invalid input, duplicate name, non-empty folder): not a malfunction
    toast('error', "That can't be done", error.message);
  } else {
    toast('error', 'Something went wrong', error.message);
  }
}

async function run(request, title, message = '') {
  try {
    await request();
    toast('ok', title, message);
  } catch (error) {
    showError(error);
  }
  await refresh();
  await pollLog();
}

/* ---------- dialogs ---------- */

function createField(field) {
  const id = `field-${field.name}`;
  let input;
  if (field.type === 'select') {
    input = el('select', { id });
    for (const option of field.options) {
      input.append(el('option', { value: option.value }, option.label));
    }
  } else if (field.type === 'textarea') {
    input = el('textarea', { id, rows: 7, placeholder: field.placeholder, spellcheck: 'false' });
  } else {
    input = el('input', {
      id,
      type: 'text',
      autocomplete: 'off',
      maxlength: MAX_NAME_LENGTH,
      placeholder: field.placeholder,
    });
  }
  if (field.value) input.value = field.value;

  const help = field.hint && el('p', { class: 'help', id: `${id}-help` }, field.hint);
  const error = el('p', { class: 'error', id: `${id}-error`, role: 'alert', hidden: true });
  input.setAttribute('aria-describedby', [help && `${id}-help`, `${id}-error`].filter(Boolean).join(' '));

  const wrapper = el('div', { class: 'field' }, el('label', { for: id }, field.label), input, help, error);
  return { input, error, wrapper };
}

function openDialog({
  title,
  description = '',
  iconName = 'info',
  tone = '',
  meta = [],
  fields = [],
  text = null,
  emptyText = '',
  okLabel = 'OK',
  cancelLabel = 'Cancel',
}) {
  return new Promise((resolve) => {
    const dialog = $('dialog');
    const controls = {};

    $('dialogTitle').textContent = title;
    $('dialogDesc').textContent = description;
    $('dialogDesc').hidden = !description;
    $('dialogIcon').className = `dialog-icon ${tone}`.trim();
    $('dialogIcon').replaceChildren(icon(iconName));
    $('dialogMeta').replaceChildren(...meta);
    $('dialogMeta').hidden = meta.length === 0;

    const body = $('dialogBody');
    body.hidden = text === null;
    body.textContent = text || emptyText;
    body.classList.toggle('is-empty', text !== null && !text);

    $('dialogFields').replaceChildren();
    for (const field of fields) {
      const control = createField(field);
      controls[field.name] = { ...control, field };
      $('dialogFields').append(control.wrapper);
    }

    const ok = $('dialogOk');
    ok.textContent = okLabel || '';
    ok.hidden = !okLabel;
    ok.className = tone === 'danger' ? 'btn danger' : 'btn primary';
    $('dialogCancel').textContent = cancelLabel;

    let values = null;
    $('dialogForm').onsubmit = (event) => {
      event.preventDefault();
      let firstInvalid = null;
      for (const { input, error, field } of Object.values(controls)) {
        const problem = field.validate ? field.validate(input.value) : '';
        error.textContent = problem;
        error.hidden = !problem;
        input.setAttribute('aria-invalid', problem ? 'true' : 'false');
        if (problem && !firstInvalid) firstInvalid = input;
      }
      if (firstInvalid) {
        firstInvalid.focus();
        return;
      }
      values = Object.fromEntries(Object.entries(controls).map(([name, { input }]) => [name, input.value]));
      dialog.close();
    };
    $('dialogCancel').onclick = () => dialog.close();
    $('dialogClose').onclick = () => dialog.close();
    dialog.onclick = (event) => {
      if (event.target === dialog) dialog.close();
    };
    dialog.onclose = () => resolve(values);

    dialog.showModal();
    const first = Object.values(controls)[0]?.input;
    if (first) first.focus();
    else if (tone === 'danger' || !okLabel) $('dialogCancel').focus();
    else ok.focus();
  });
}

function nameField(label, value = '', placeholder = '') {
  return {
    name: 'name',
    label,
    value,
    placeholder,
    hint: `Up to ${MAX_NAME_LENGTH} characters. These are not allowed: \\ / : * ? " < > |`,
    validate: (raw) => {
      const name = raw.trim();
      if (!name) return 'Enter a name.';
      if (name.length > MAX_NAME_LENGTH) return `Use at most ${MAX_NAME_LENGTH} characters.`;
      if (name === '..' || name.endsWith('.')) return 'A name cannot end with a dot.';
      if (FORBIDDEN_IN_NAME.test(name)) return 'This name contains a character that is not allowed.';
      return '';
    },
  };
}

/* ---------- top bar ---------- */

function renderSegmented(container, { items, selected, onSelect, mode }) {
  const hadFocus = container.contains(document.activeElement);
  container.setAttribute('role', mode === 'radio' ? 'radiogroup' : 'group');

  const buttons = items.map((item) => {
    const active = item.value === selected;
    return el('button', {
      type: 'button',
      role: mode === 'radio' ? 'radio' : false,
      'aria-checked': mode === 'radio' ? String(active) : false,
      'aria-pressed': mode === 'toggle' ? String(active) : false,
      tabindex: mode === 'radio' && !active ? '-1' : false,
      title: item.title,
      disabled: item.disabled,
      onclick: () => onSelect(item.value),
    }, item.leading, item.label);
  });
  container.replaceChildren(...buttons);

  if (mode === 'radio') {
    container.onkeydown = (event) => {
      const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
      if (!step) return;
      event.preventDefault();
      const current = items.findIndex((item) => item.value === selected);
      onSelect(items[(current + step + items.length) % items.length].value);
    };
  }
  if (hadFocus) {
    (container.querySelector('[aria-checked="true"], [aria-pressed="true"]') || buttons[0])?.focus();
  }
}

function renderUserSwitch() {
  renderSegmented($('userSwitch'), {
    mode: 'radio',
    selected: state.user,
    items: state.users.map((user) => ({
      value: user.id,
      label: user.displayName,
      leading: avatar(user.displayName),
      title: `Act as ${user.displayName}`,
    })),
    onSelect: (id) => {
      if (id === state.user) return;
      state.user = id;
      sessionStorage.setItem('user', id);
      state.folderId = null;
      renderUserSwitch();
      toast('info', `Now acting as ${userName(id)}`,
        'You see the files this person owns and the files shared with them.');
      refresh();
    },
  });
}

function renderStorageSwitch() {
  renderSegmented($('storageSwitch'), {
    mode: 'toggle',
    selected: state.storage,
    items: state.storages.map((type) => ({
      value: type,
      label: storageLabel(type),
      leading: icon(STORAGE_INFO[type]?.icon ?? 'hard-drives'),
      title: STORAGE_INFO[type]?.hint ?? '',
      disabled: state.switching,
    })),
    onSelect: switchStorage,
  });
}

async function switchStorage(target) {
  if (target === state.storage || state.switching) return;
  state.switching = true;
  renderStorageSwitch();
  await run(() => api('POST', 'storage', { type: target }),
    'Storage switched',
    `Now using ${storageLabel(target)}. Your files were moved over and everything works the same.`);
  state.switching = false;
  renderStorageSwitch();
}

function systemTheme() {
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function renderThemeButton() {
  const theme = document.documentElement.dataset.theme || systemTheme();
  const next = theme === 'dark' ? 'light' : 'dark';
  $('themeIcon').setAttribute('href', `/icons.svg#i-${theme === 'dark' ? 'sun' : 'moon'}`);
  $('themeBtn').setAttribute('aria-label', `Switch to ${next} theme`);
  $('themeBtn').title = `Switch to ${next} theme`;
}

function toggleTheme() {
  const next = (document.documentElement.dataset.theme || systemTheme()) === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  try {
    localStorage.setItem('fm.theme', next);
  } catch {
    // the choice simply is not remembered
  }
  renderThemeButton();
}

/* ---------- data loading ---------- */

async function loadSession() {
  const session = await (await fetch('/api/session')).json();
  state.users = session.users;
  state.storages = session.storages;
  state.storage = session.storage;
}

async function loadFiles() {
  if (state.folderId) {
    return (await api('GET', `folders/${encodeURIComponent(state.folderId)}`)).files;
  }
  const search = $('search').value.trim();
  return (await api('GET', search ? `files?q=${encodeURIComponent(search)}` : 'files')).files;
}

async function refresh() {
  state.loadError = '';
  try {
    await loadSession();
    state.folders = (await api('GET', 'folders')).folders;
    if (!state.folders.some((folder) => folder.id === state.folderId)) {
      state.folderId = null;
    }
    state.files = await loadFiles();
  } catch (error) {
    state.files = [];
    if (error.status === 403) {
      showError(error);
      state.folderId = null;
    } else {
      state.loadError = error.message;
    }
  }
  state.loading = false;
  rememberNames();
  render();
}

/* ---------- rendering: files ---------- */

function viewTitle() {
  if (state.folderId) return folderName(state.folderId);
  return $('search').value.trim() ? 'Search results' : 'All files';
}

function viewSubtitle() {
  if (state.loading || state.loadError) return '';
  const count = state.files.length;
  const noun = `${count} ${count === 1 ? 'file' : 'files'}`;
  if (state.folderId) {
    const folder = state.folders.find((item) => item.id === state.folderId);
    const owner = folder && folder.ownerId !== state.user ? ` Folder owned by ${userName(folder.ownerId)}.` : '';
    return `${noun} you can see in this folder.${owner}`;
  }
  if ($('search').value.trim()) return `${noun} match your search.`;
  return `${noun}. You see the files you own and the files shared with you.`;
}

function render() {
  renderStorageSwitch();
  renderFolders();
  $('viewTitle').textContent = viewTitle();
  $('viewSub').textContent = viewSubtitle();
  renderFiles();
  renderLog();
}

function openFolder(id) {
  state.folderId = id;
  $('search').value = '';
  refresh();
}

function renderFolders() {
  const items = [
    el('li', {},
      el('button', {
        type: 'button',
        class: 'folder-item',
        'aria-current': state.folderId ? false : 'true',
        onclick: () => openFolder(null),
      }, icon('file'), el('span', { class: 'folder-name' }, el('span', {}, 'All files')))),
  ];

  for (const folder of state.folders) {
    const active = folder.id === state.folderId;
    const mine = folder.ownerId === state.user;
    items.push(el('li', {},
      el('button', {
        type: 'button',
        class: 'folder-item',
        'aria-current': active ? 'true' : false,
        onclick: () => openFolder(folder.id),
      },
      icon(active ? 'folder-open' : 'folder-simple'),
      el('span', { class: 'folder-name' },
        el('span', {}, folder.name),
        !mine && el('span', { class: 'folder-owner' }, `Owned by ${userName(folder.ownerId)}`))),
      el('button', {
        type: 'button',
        class: 'icon-btn sm folder-del',
        title: 'Delete folder',
        'aria-label': `Delete folder ${folder.name}`,
        onclick: () => deleteFolder(folder),
      }, icon('trash'))));
  }

  if (state.folders.length === 0 && !state.loading) {
    items.push(el('li', {}, el('p', { class: 'hint' }, 'No folders yet. Use the folder button above to create one.')));
  }
  $('folderList').replaceChildren(...items);
}

function skeletonRows() {
  return Array.from({ length: 4 }, () => el('tr', {},
    el('td', {}, el('div', { class: 'file-cell' },
      el('span', { class: 'skeleton tile' }),
      el('div', { class: 'file-info' }, el('span', { class: 'skeleton w80' }), el('span', { class: 'skeleton w60' })))),
    el('td', {}, el('span', { class: 'skeleton w60' })),
    el('td', {}, el('span', { class: 'skeleton w40' })),
    el('td', {}, el('span', { class: 'skeleton w60' })),
    el('td', {}, el('span', { class: 'skeleton w30' }))));
}

function renderFiles() {
  const rows = $('fileRows');
  const tableWrap = document.querySelector('.table-wrap');
  if (state.loading) {
    rows.replaceChildren(...skeletonRows());
    tableWrap.hidden = false;
    $('emptyState').hidden = true;
    return;
  }
  rows.replaceChildren(...state.files.map(fileRow));
  tableWrap.hidden = state.files.length === 0;
  renderEmptyState();
}

function stateBlock({ iconName, title, text, actions = [] }) {
  return [
    el('span', { class: 'state-icon', 'aria-hidden': 'true' }, icon(iconName)),
    el('h3', {}, title),
    el('p', {}, text),
    ...actions,
  ];
}

function renderEmptyState() {
  const box = $('emptyState');
  const search = $('search').value.trim();
  box.hidden = state.files.length > 0;
  if (box.hidden) return;

  box.className = 'state';
  let content;
  if (state.loadError) {
    box.classList.add('error');
    content = stateBlock({
      iconName: 'warning-circle',
      title: "Couldn't load your files",
      text: state.loadError,
      actions: [el('button', { type: 'button', class: 'btn', onclick: refresh }, icon('arrows-clockwise'), 'Try again')],
    });
  } else if (state.folderId) {
    content = stateBlock({
      iconName: 'folder-open',
      title: 'Nothing to show in this folder',
      text: 'Move a file here from its three-dot menu. Only the folder owner can add files.',
      actions: [el('button', { type: 'button', class: 'btn', onclick: () => openFolder(null) }, 'Back to all files')],
    });
  } else if (search) {
    content = stateBlock({
      iconName: 'magnifying-glass',
      title: `No files match “${search}”`,
      text: 'Check the spelling, or search for a shorter part of the name.',
      actions: [el('button', {
        type: 'button',
        class: 'btn',
        onclick: () => {
          $('search').value = '';
          refresh();
        },
      }, 'Clear search')],
    });
  } else {
    content = stateBlock({
      iconName: 'file-plus',
      title: 'No files yet',
      text: `${userName(state.user)} has no files and nothing has been shared with them. Create a file to get started, or switch to another person at the top.`,
      actions: [el('button', { type: 'button', class: 'btn primary', onclick: createFile }, icon('file-plus'), 'Create a file')],
    });
  }
  box.replaceChildren(...content);
}

function roleBadge(role) {
  if (role === 'OWNER') return el('span', { class: 'badge owner' }, icon('check-circle'), 'Owner');
  if (role === 'VIEWER') return el('span', { class: 'badge viewer' }, icon('eye'), 'Viewer');
  return el('span', { class: 'badge none' }, 'No access');
}

function fileRow(file) {
  const ownerName = userName(file.ownerId);
  const access = {
    OWNER: 'Full control',
    VIEWER: 'Read only',
  }[file.role] ?? '';

  const shared = file.sharedWith.length
    ? el('div', { class: 'shared' }, file.sharedWith.map((id) =>
      el('span', { class: 'chip' }, avatar(userName(id)), userName(id))))
    : el('span', { class: 'muted' }, 'Not shared');

  return el('tr', {},
    el('td', { class: 'cell-name' },
      el('div', { class: 'file-cell' },
        el('span', { class: 'file-tile', 'aria-hidden': 'true' }, icon(FILE_ICONS[file.type] ?? 'file')),
        el('div', { class: 'file-info' },
          el('button', { type: 'button', class: 'file-link', onclick: () => openFile(file) }, file.displayName),
          el('div', { class: 'file-meta' },
            el('span', {}, `${formatSize(file.size)}, ${formatDate(file.createdAt)}`),
            file.folderId && el('span', { class: 'folder-tag' }, icon('folder-simple'), folderName(file.folderId)))))),
    el('td', { 'data-label': 'Owner' },
      el('span', { class: 'person' }, avatar(ownerName), ownerName,
        file.ownerId === state.user && el('span', { class: 'you' }, '(you)'))),
    el('td', { 'data-label': 'Your access' },
      el('div', { class: 'access' }, roleBadge(file.role), access && el('small', {}, access))),
    el('td', { 'data-label': 'Shared with' }, shared),
    el('td', { class: 'cell-actions', 'data-label': 'Actions' },
      el('div', { class: 'row-actions' },
        el('button', { type: 'button', class: 'btn sm', onclick: () => openFile(file) }, icon('eye'), 'Open'),
        el('button', {
          type: 'button',
          class: 'icon-btn sm',
          'aria-label': `More actions for ${file.displayName}`,
          'aria-haspopup': 'menu',
          'aria-expanded': 'false',
          onclick: (event) => toggleMenu(event.currentTarget, file),
        }, icon('dots-three-vertical')))));
}

/* ---------- row action menu ---------- */

let menuTrigger = null;

function closeMenu(returnFocus = false) {
  const menu = $('menu');
  if (menu.hidden) return;
  menu.hidden = true;
  menuTrigger?.setAttribute('aria-expanded', 'false');
  if (returnFocus) menuTrigger?.focus();
  menuTrigger = null;
}

function toggleMenu(trigger, file) {
  if (menuTrigger === trigger) {
    closeMenu(true);
    return;
  }
  closeMenu();
  openMenu(trigger, file);
}

function openMenu(trigger, file) {
  const menu = $('menu');
  const isOwner = file.role === 'OWNER';

  const item = (label, iconName, action, danger = false) => el('button', {
    type: 'button',
    role: 'menuitem',
    class: `menu-item${danger ? ' danger' : ''}`,
    onclick: () => {
      closeMenu();
      action(file);
    },
  }, icon(iconName), label, !isOwner && el('span', { class: 'lock' }, icon('lock-simple'), 'Owner only'));

  menu.replaceChildren(
    !isOwner && el('p', { class: 'menu-note' }, icon('info'),
      'You are a Viewer of this file. You can try these actions, but the proxy will refuse them.'),
    item('Rename', 'pencil-simple', renameFile),
    item('Share', 'share-network', shareFile),
    item('Move to folder', 'arrow-elbow-down-right', moveFile),
    el('div', { class: 'menu-sep', role: 'separator' }),
    item('Delete', 'trash', deleteFile, true));

  menu.hidden = false;
  menuTrigger = trigger;
  trigger.setAttribute('aria-expanded', 'true');

  const rect = trigger.getBoundingClientRect();
  const { offsetWidth: width, offsetHeight: height } = menu;
  const left = Math.max(8, Math.min(rect.right - width, window.innerWidth - width - 8));
  let top = rect.bottom + 6;
  if (top + height > window.innerHeight - 8) top = Math.max(8, rect.top - height - 6);
  menu.style.left = `${left}px`;
  menu.style.top = `${top}px`;

  menu.querySelector('.menu-item').focus({ preventScroll: true });
}

function setUpMenu() {
  const menu = $('menu');
  document.addEventListener('click', (event) => {
    if (!menu.hidden && !menu.contains(event.target) && !menuTrigger?.contains(event.target)) closeMenu();
  });
  window.addEventListener('resize', () => closeMenu());
  window.addEventListener('scroll', () => closeMenu(), { passive: true, capture: true });
  menu.addEventListener('keydown', (event) => {
    const items = [...menu.querySelectorAll('.menu-item')];
    const index = items.indexOf(document.activeElement);
    if (event.key === 'ArrowDown') items[(index + 1) % items.length].focus();
    else if (event.key === 'ArrowUp') items[(index - 1 + items.length) % items.length].focus();
    else if (event.key === 'Home') items[0].focus();
    else if (event.key === 'End') items[items.length - 1].focus();
    else if (event.key === 'Escape') closeMenu(true);
    else if (event.key === 'Tab') closeMenu();
    else return;
    if (event.key !== 'Tab') event.preventDefault();
  });
}

/* ---------- rendering: activity panel ---------- */

const PROXY_LINE = /^\[PROXY\] (\S+) -> (.+?) (file|folder) '([^']*)' : (GRANTED|DENIED) \((.+)\)$/;
const PROXY_LINE_NO_TARGET = /^\[PROXY\] (\S+) -> (.+?) - : (GRANTED|DENIED) \((.+)\)$/;
const BRIDGE_LINE = /^\[BRIDGE\] (\S+) switched storage (\S+) -> (\S+) : (.+)$/;

// a tab only knows the names of files it has loaded; for others the short id is shown
function targetName(kind, id) {
  return state.names.get(id) ?? id.slice(0, 8);
}

// turns one raw audit line into { kind, iconName, title (nodes), verdict, detail }
function describeEntry(entry) {
  let match = PROXY_LINE.exec(entry.text);
  if (match) {
    const [, actor, operation, targetKind, id, outcome, role] = match;
    const [past, gerund] = VERBS[operation] ?? [operation.toLowerCase(), operation.toLowerCase()];
    const granted = outcome === 'GRANTED';
    const target = el('span', { class: 'log-ref' }, targetName(targetKind, id));
    return {
      kind: granted ? 'granted' : 'denied',
      iconName: granted ? 'check-circle' : 'prohibit',
      title: [el('strong', {}, userName(actor)), granted ? ` ${past} ${targetKind} ` : ` was blocked from ${gerund} ${targetKind} `, target],
      verdict: granted ? 'Allowed' : 'Denied',
      detail: granted ? `as ${capitalize(role)}` : (DENIAL_REASONS[role] ?? `Reason: ${role}.`),
    };
  }

  match = PROXY_LINE_NO_TARGET.exec(entry.text);
  if (match) {
    const [, actor, operation, outcome, role] = match;
    const granted = outcome === 'GRANTED';
    return {
      kind: granted ? 'granted' : 'denied',
      iconName: granted ? 'check-circle' : 'prohibit',
      title: [el('strong', {}, userName(actor)), ` tried to ${operation.toLowerCase()}`],
      verdict: granted ? 'Allowed' : 'Denied',
      detail: DENIAL_REASONS[role] ?? `Reason: ${role}.`,
    };
  }

  match = BRIDGE_LINE.exec(entry.text);
  if (match) {
    const [, actor, from, to] = match;
    return {
      kind: 'bridge',
      iconName: 'arrows-left-right',
      title: [el('strong', {}, userName(actor)), ` switched storage from ${storageLabel(from)} to ${storageLabel(to)}`],
      verdict: 'Storage',
      detail: 'Files moved over, same file manager.',
    };
  }

  return {
    kind: entry.granted ? 'granted' : 'denied',
    iconName: entry.granted ? 'check-circle' : 'prohibit',
    title: [entry.text],
    verdict: entry.granted ? 'Allowed' : 'Denied',
    detail: '',
  };
}

function logItem(entry) {
  const info = describeEntry(entry);
  const body = state.rawLog
    ? [el('p', { class: 'log-raw' }, entry.text), el('p', { class: 'log-meta' }, el('span', { class: 'log-time' }, entry.time))]
    : [
      el('p', { class: 'log-title' }, ...info.title),
      el('p', { class: 'log-meta' },
        el('span', { class: 'verdict' }, info.verdict),
        info.detail && el('span', {}, info.detail),
        el('span', { class: 'log-time' }, entry.time)),
    ];
  return el('li', { class: `log-item ${info.kind}${entry.fresh ? ' is-new' : ''}`, title: entry.text },
    el('span', { class: 'log-icon' }, icon(info.iconName)),
    el('div', { class: 'log-body' }, ...body));
}

function renderLog() {
  const list = $('logList');
  const scroll = list.scrollTop;
  const visible = state.log
    .filter((entry) => state.logFilter === 'all' || describeEntry(entry).kind === state.logFilter)
    .reverse();

  list.replaceChildren(...visible.map(logItem));
  list.scrollTop = scroll;
  for (const entry of state.log) entry.fresh = false;

  const empty = $('logEmpty');
  empty.hidden = visible.length > 0;
  if (!empty.hidden) {
    const filtered = state.log.length > 0;
    empty.replaceChildren(
      el('span', { class: 'state-icon', 'aria-hidden': 'true' }, icon(filtered ? 'funnel-simple' : 'broadcast')),
      el('strong', {}, filtered ? 'Nothing matches this filter' : 'No activity yet'),
      el('span', {}, filtered
        ? 'Choose All to see every decision.'
        : 'Open, rename or share a file and the proxy’s decision appears here.'));
  }
}

let connectionLost = false;

async function pollLog() {
  let entries;
  try {
    entries = (await api('GET', `log?since=${state.lastLogSeq}`)).entries;
  } catch (error) {
    if (!error.status) {
      connectionLost = true;
      setOnline(false);
    }
    return;
  }
  setOnline(true);

  if (connectionLost) {
    // The server was restarted: its activity log starts again from 1, so start over with it
    // (and reload the files, which may have changed while the connection was down).
    connectionLost = false;
    state.log = [];
    state.lastLogSeq = 0;
    state.logLoaded = false;
    await refresh();
    return pollLog();
  }
  if (entries.length === 0) return;

  for (const entry of entries) {
    state.log.push({ ...entry, fresh: state.logLoaded });
    state.lastLogSeq = entry.seq;
  }
  state.logLoaded = true;
  if (state.log.length > LOG_LIMIT) state.log.splice(0, state.log.length - LOG_LIMIT);
  renderLog();
}

function setOnline(online) {
  const status = $('liveStatus');
  status.textContent = online ? 'Live' : 'Reconnecting';
  status.classList.toggle('offline', !online);
}

function setUpLogControls() {
  for (const button of document.querySelectorAll('.chip-btn')) {
    button.addEventListener('click', () => {
      state.logFilter = button.dataset.filter;
      for (const other of document.querySelectorAll('.chip-btn')) {
        other.setAttribute('aria-pressed', String(other === button));
      }
      renderLog();
    });
  }
  $('rawToggle').addEventListener('change', (event) => {
    state.rawLog = event.target.checked;
    renderLog();
  });
}

/* ---------- file and folder actions ---------- */

async function openFile(file) {
  try {
    const { content } = await api('GET', fileUrl(file, '/content'));
    await openDialog({
      title: file.displayName,
      iconName: FILE_ICONS[file.type] ?? 'file',
      meta: [
        el('span', { class: 'chip' }, avatar(userName(file.ownerId)), `Owner: ${userName(file.ownerId)}`),
        el('span', { class: 'chip' }, formatSize(file.size)),
        roleBadge(file.role),
      ],
      text: content,
      emptyText: 'This file is empty.',
      okLabel: null,
      cancelLabel: 'Close',
    });
  } catch (error) {
    showError(error);
  }
  await pollLog();
}

async function createFile() {
  const values = await openDialog({
    title: 'New file',
    description: 'The file is saved in the current storage and you become its owner.',
    iconName: 'file-plus',
    fields: [
      nameField('Name', '', 'meeting-notes'),
      {
        name: 'type',
        label: 'Type',
        type: 'select',
        options: FILE_TYPES.map((type) => ({ value: type, label: `.${type}` })),
        hint: 'Added to the name automatically.',
      },
      { name: 'content', label: 'Content', type: 'textarea', hint: 'Optional. You can leave it empty.' },
    ],
    okLabel: 'Create file',
  });
  if (values) {
    await run(() => api('POST', 'files', values), 'File created', `${values.name.trim()}.${values.type} is ready.`);
  }
}

async function renameFile(file) {
  const values = await openDialog({
    title: `Rename ${file.displayName}`,
    description: 'Only owners can rename a file.',
    iconName: 'pencil-simple',
    fields: [nameField('New name', file.name)],
    okLabel: 'Rename',
  });
  if (values) {
    await run(() => api('POST', fileUrl(file, '/rename'), values), 'File renamed',
      `Now called ${values.name.trim()}.${file.type}.`);
  }
}

async function shareFile(file) {
  const others = state.users.filter((user) => user.id !== state.user);
  const values = await openDialog({
    title: `Share ${file.displayName}`,
    description: 'Choose who gets access to this file.',
    iconName: 'share-network',
    fields: [{
      name: 'target',
      label: 'Share with',
      type: 'select',
      options: others.map((user) => ({
        value: user.id,
        label: file.sharedWith.includes(user.id) ? `${user.displayName} (already has access)` : user.displayName,
      })),
      hint: 'They become a Viewer: they can read the file but not change it.',
    }],
    okLabel: 'Share',
  });
  if (values) {
    await run(() => api('POST', fileUrl(file, '/share'), values), 'File shared',
      `${userName(values.target)} can now read ${file.displayName}.`);
  }
}

async function moveFile(file) {
  const myFolders = state.folders.filter((folder) => folder.ownerId === state.user);
  const values = await openDialog({
    title: `Move ${file.displayName}`,
    description: 'Folders only organize files. They do not change who can see them.',
    iconName: 'arrow-elbow-down-right',
    fields: [{
      name: 'folderId',
      label: 'Destination folder',
      type: 'select',
      value: file.folderId || '',
      options: [
        { value: '', label: 'No folder (top level)' },
        ...myFolders.map((folder) => ({ value: folder.id, label: folder.name })),
      ],
      hint: 'You can only move files into folders you own.',
    }],
    okLabel: 'Move',
  });
  if (values) {
    const target = values.folderId ? folderName(values.folderId) : 'the top level';
    await run(() => api('POST', fileUrl(file, '/move'), values), 'File moved', `${file.displayName} is now in ${target}.`);
  }
}

async function deleteFile(file) {
  const confirmed = await openDialog({
    title: `Delete ${file.displayName}?`,
    description: 'The file and its stored content are removed for everyone. This cannot be undone.',
    iconName: 'trash',
    tone: 'danger',
    okLabel: 'Delete file',
  });
  if (confirmed) await run(() => api('DELETE', fileUrl(file)), 'File deleted', `${file.displayName} was removed.`);
}

async function createFolder() {
  const values = await openDialog({
    title: 'New folder',
    description: 'Folders help you group your own files.',
    iconName: 'folder-simple-plus',
    fields: [nameField('Folder name', '', 'Reports')],
    okLabel: 'Create folder',
  });
  if (values) await run(() => api('POST', 'folders', values), 'Folder created', `${values.name.trim()} is ready.`);
}

async function deleteFolder(folder) {
  const confirmed = await openDialog({
    title: `Delete folder “${folder.name}”?`,
    description: 'A folder that still contains files cannot be deleted. Move or delete its files first.',
    iconName: 'trash',
    tone: 'danger',
    okLabel: 'Delete folder',
  });
  if (confirmed) {
    await run(() => api('DELETE', `folders/${encodeURIComponent(folder.id)}`), 'Folder deleted', `${folder.name} was removed.`);
  }
}

/* ---------- page setup ---------- */

function setUpUserSelect() {
  const saved = sessionStorage.getItem('user');
  state.user = state.users.some((user) => user.id === saved) ? saved : state.users[0].id;
  renderUserSwitch();
}

function setUpSearch() {
  let timer;
  $('search').addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      state.folderId = null;
      refresh();
    }, 250);
  });
  document.addEventListener('keydown', (event) => {
    const typing = event.target.closest?.('input, textarea, select, [contenteditable]');
    if (event.key === '/' && !typing && !document.querySelector('dialog[open]')) {
      event.preventDefault();
      $('search').focus();
    }
  });
}

function setUpGuide() {
  const guide = $('guide');
  const open = () => guide.showModal();
  $('guideBtn').addEventListener('click', open);
  $('quickstartGuide').addEventListener('click', open);
  $('guideClose').addEventListener('click', () => guide.close());
  $('guideDone').addEventListener('click', () => guide.close());
  guide.addEventListener('click', (event) => {
    if (event.target === guide) guide.close();
  });
}

function setUpQuickstart() {
  let dismissed = false;
  try {
    dismissed = localStorage.getItem('fm.quickstart') === 'dismissed';
  } catch {
    // shown again next time
  }
  $('quickstart').hidden = dismissed;
  $('quickstartClose').addEventListener('click', () => {
    $('quickstart').hidden = true;
    try {
      localStorage.setItem('fm.quickstart', 'dismissed');
    } catch {
      // not remembered
    }
  });
}

async function init() {
  renderThemeButton();
  $('themeBtn').addEventListener('click', toggleTheme);
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', renderThemeButton);
  setUpQuickstart();
  setUpGuide();
  setUpMenu();
  setUpLogControls();
  setUpSearch();
  $('newFile').addEventListener('click', createFile);
  $('newFolder').addEventListener('click', createFolder);
  renderFiles();

  await loadSession();
  setUpUserSelect();
  await refresh();
  await pollLog();
  setInterval(pollLog, LOG_POLL_MS);
}

init().catch((error) => {
  state.loading = false;
  state.loadError = error.message;
  renderFiles();
  toast('error', 'Could not start', error.message);
});
