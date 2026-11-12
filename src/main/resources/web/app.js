'use strict';

const FILE_TYPES = ['txt', 'md', 'json', 'csv', 'xml', 'log'];
const LOG_POLL_MS = 1500;
const LOG_LIMIT = 200;

const state = {
  user: null,
  users: [],
  storage: '',
  storages: [],
  folders: [],
  files: [],
  folderId: null,
  lastLogSeq: 0,
};

const $ = (id) => document.getElementById(id);

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs)) {
    if (name === 'onclick') node.addEventListener('click', value);
    else if (name === 'class') node.className = value;
    else node.setAttribute(name, value);
  }
  node.append(...children);
  return node;
}

function button(label, onclick, extraClass = '') {
  return el('button', { type: 'button', class: `small ${extraClass}`.trim(), onclick }, label);
}

async function api(method, path, form) {
  const options = { method, headers: { 'X-User': state.user || '' } };
  if (form) {
    options.headers['Content-Type'] = 'application/x-www-form-urlencoded';
    options.body = new URLSearchParams(form).toString();
  }
  const response = await fetch(`/api/${path}`, options);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.error || `Request failed (HTTP ${response.status})`);
    error.status = response.status;
    throw error;
  }
  return data;
}

function fileUrl(file, action = '') {
  return `files/${encodeURIComponent(file.id)}${action}`;
}

function toast(message, kind = '') {
  const node = el('div', { class: `toast ${kind}` }, message);
  $('toasts').append(node);
  setTimeout(() => node.remove(), kind === 'deny' ? 8000 : 5000);
}

function showError(error) {
  toast(error.message, error.status === 403 ? 'deny' : 'error');
}

async function run(request, successMessage) {
  try {
    await request();
    toast(successMessage, 'ok');
  } catch (error) {
    showError(error);
  }
  await refresh();
  await pollLog();
}

function createInput(field) {
  if (field.type === 'select') {
    const select = el('select');
    for (const option of field.options) {
      select.append(el('option', { value: option.value }, option.label));
    }
    return select;
  }
  if (field.type === 'textarea') return el('textarea', { rows: 8 });
  return el('input', { type: 'text', autocomplete: 'off', maxlength: 100 });
}

function openDialog({ title, fields = [], text = '', okLabel = 'OK', cancelLabel = 'Cancel' }) {
  return new Promise((resolve) => {
    const dialog = $('dialog');
    const inputs = {};

    $('dialogTitle').textContent = title;
    $('dialogBody').textContent = text;
    $('dialogBody').hidden = !text;
    $('dialogOk').textContent = okLabel || '';
    $('dialogOk').hidden = !okLabel;
    $('dialogCancel').textContent = cancelLabel;

    $('dialogFields').replaceChildren();
    for (const field of fields) {
      const input = createInput(field);
      if (field.value) input.value = field.value;
      inputs[field.name] = input;
      $('dialogFields').append(el('label', {}, field.label, input));
    }

    let values = null;
    $('dialogForm').onsubmit = (event) => {
      event.preventDefault();
      values = Object.fromEntries(Object.entries(inputs).map(([name, input]) => [name, input.value]));
      dialog.close();
    };
    $('dialogCancel').onclick = () => dialog.close();
    dialog.onclose = () => resolve(values);

    dialog.showModal();
    Object.values(inputs)[0]?.focus();
  });
}

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
  try {
    await loadSession();
    state.folders = (await api('GET', 'folders')).folders;
    if (!state.folders.some((folder) => folder.id === state.folderId)) {
      state.folderId = null;
    }
    state.files = await loadFiles();
  } catch (error) {
    showError(error);
    state.folderId = null;
  }
  render();
}

async function pollLog() {
  let entries;
  try {
    entries = (await api('GET', `log?since=${state.lastLogSeq}`)).entries;
  } catch {
    return;
  }
  if (entries.length === 0) return;

  const list = $('logList');
  for (const entry of entries) {
    let kind = entry.granted ? 'granted' : 'denied';
    if (entry.text.startsWith('[BRIDGE]')) kind = 'bridge';
    list.append(el('li', { class: kind }, el('span', { class: 'time' }, entry.time), entry.text));
    state.lastLogSeq = entry.seq;
  }
  while (list.children.length > LOG_LIMIT) list.firstChild.remove();
  list.scrollTop = list.scrollHeight;
}

function otherStorage() {
  return state.storages.find((storage) => storage !== state.storage);
}

function folderName(id) {
  const folder = state.folders.find((item) => item.id === id);
  return folder ? folder.name : "(another user's folder)";
}

function viewTitle() {
  if (state.folderId) return `Folder: ${folderName(state.folderId)}`;
  return $('search').value.trim() ? 'Search results' : 'All files';
}

function render() {
  const other = otherStorage();
  $('storageName').textContent = state.storage.toLowerCase();
  $('switchStorage').textContent = other ? `Switch to ${other.toLowerCase()}` : 'Switch';

  renderFolders();
  $('viewTitle').textContent = viewTitle();
  $('fileRows').replaceChildren(...state.files.map(fileRow));
  $('emptyNote').hidden = state.files.length > 0;
  $('emptyNote').textContent = state.folderId
    ? 'This folder has nothing you can see.'
    : 'No files yet. Create one with "+ New file", or switch user to see files shared with you.';
}

function openFolder(id) {
  state.folderId = id;
  $('search').value = '';
  refresh();
}

function renderFolders() {
  const items = [
    el('li', { class: state.folderId ? '' : 'active', onclick: () => openFolder(null) },
      el('span', {}, 'All files')),
  ];
  for (const folder of state.folders) {
    const owner = folder.ownerId === state.user ? '' : `by ${folder.ownerId}`;
    const remove = el('button', {
      type: 'button',
      class: 'x',
      title: 'Delete folder (only if empty, only the owner)',
      onclick: (event) => {
        event.stopPropagation();
        deleteFolder(folder);
      },
    }, '\u00d7');
    items.push(el('li', {
      class: folder.id === state.folderId ? 'active' : '',
      onclick: () => openFolder(folder.id),
    }, el('span', {}, `${folder.name} `, el('span', { class: 'meta' }, owner)), remove));
  }
  $('folderList').replaceChildren(...items);
}

function muted(text) {
  return el('span', { class: 'muted' }, text);
}

function fileRow(file) {
  const role = file.role ? el('span', { class: `badge ${file.role.toLowerCase()}` }, file.role) : muted('none');
  const sharedWith = file.sharedWith.map((userId) => el('span', { class: 'chip' }, userId));
  const actions = el('div', { class: 'actions' },
    button('Open', () => openFile(file)),
    button('Rename', () => renameFile(file)),
    button('Share', () => shareFile(file)),
    button('Move', () => moveFile(file)),
    button('Delete', () => deleteFile(file), 'danger'));

  return el('tr', {},
    el('td', { class: 'name' }, file.displayName),
    el('td', {}, `${file.size} B`),
    el('td', {}, file.ownerId),
    el('td', {}, role),
    el('td', {}, new Date(file.createdAt).toLocaleString()),
    el('td', {}, ...(sharedWith.length ? sharedWith : [muted('-')])),
    el('td', {}, file.folderId ? folderName(file.folderId) : muted('-')),
    el('td', {}, actions));
}

async function openFile(file) {
  try {
    const { content } = await api('GET', fileUrl(file, '/content'));
    await openDialog({
      title: `${file.displayName}  (${file.size} B, owner ${file.ownerId})`,
      text: content || '(empty file)',
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
    fields: [
      { name: 'name', label: 'Name (without extension)' },
      { name: 'type', label: 'Type', type: 'select', options: FILE_TYPES.map((type) => ({ value: type, label: `.${type}` })) },
      { name: 'content', label: 'Content', type: 'textarea' },
    ],
    okLabel: 'Create',
  });
  if (values) await run(() => api('POST', 'files', values), 'File created.');
}

async function renameFile(file) {
  const values = await openDialog({
    title: `Rename ${file.displayName}`,
    fields: [{ name: 'name', label: 'New name (without extension)', value: file.name }],
    okLabel: 'Rename',
  });
  if (values) await run(() => api('POST', fileUrl(file, '/rename'), values), 'Renamed.');
}

async function shareFile(file) {
  const others = state.users.filter((user) => user.id !== state.user);
  const values = await openDialog({
    title: `Share ${file.displayName}`,
    fields: [{
      name: 'target',
      label: 'Share with (they become a VIEWER)',
      type: 'select',
      options: others.map((user) => ({ value: user.id, label: user.displayName })),
    }],
    okLabel: 'Share',
  });
  if (values) await run(() => api('POST', fileUrl(file, '/share'), values), `Shared with ${values.target}.`);
}

async function moveFile(file) {
  const myFolders = state.folders.filter((folder) => folder.ownerId === state.user);
  const values = await openDialog({
    title: `Move ${file.displayName}`,
    fields: [{
      name: 'folderId',
      label: 'Destination folder',
      type: 'select',
      options: [
        { value: '', label: '(no folder - root)' },
        ...myFolders.map((folder) => ({ value: folder.id, label: folder.name })),
      ],
    }],
    okLabel: 'Move',
  });
  if (values) await run(() => api('POST', fileUrl(file, '/move'), values), 'Moved.');
}

async function deleteFile(file) {
  const confirmed = await openDialog({
    title: `Delete ${file.displayName}?`,
    text: 'The metadata and the stored content are removed. This cannot be undone.',
    okLabel: 'Delete',
  });
  if (confirmed) await run(() => api('DELETE', fileUrl(file)), 'Deleted.');
}

async function createFolder() {
  const values = await openDialog({
    title: 'New folder',
    fields: [{ name: 'name', label: 'Folder name' }],
    okLabel: 'Create',
  });
  if (values) await run(() => api('POST', 'folders', values), 'Folder created.');
}

async function deleteFolder(folder) {
  const confirmed = await openDialog({
    title: `Delete folder "${folder.name}"?`,
    text: 'A folder that still contains files cannot be deleted - move or delete the files first.',
    okLabel: 'Delete',
  });
  if (confirmed) await run(() => api('DELETE', `folders/${encodeURIComponent(folder.id)}`), 'Folder deleted.');
}

async function switchStorage() {
  const target = otherStorage();
  if (!target) return;
  await run(() => api('POST', 'storage', { type: target }),
    `Storage switched to ${target.toLowerCase()} - content migrated, same FileManager.`);
}

function setUpUserSelect() {
  const select = $('userSelect');
  const saved = sessionStorage.getItem('user');
  state.user = state.users.some((user) => user.id === saved) ? saved : state.users[0].id;

  for (const user of state.users) {
    select.append(el('option', { value: user.id }, user.displayName));
  }
  select.value = state.user;
  select.addEventListener('change', () => {
    state.user = select.value;
    sessionStorage.setItem('user', state.user);
    state.folderId = null;
    refresh();
  });
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
}

async function init() {
  await loadSession();
  setUpUserSelect();
  setUpSearch();
  $('newFile').addEventListener('click', createFile);
  $('newFolder').addEventListener('click', createFolder);
  $('switchStorage').addEventListener('click', switchStorage);

  await refresh();
  await pollLog();
  setInterval(pollLog, LOG_POLL_MS);
}

init().catch((error) => toast(`Could not start: ${error.message}`, 'error'));
