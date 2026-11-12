'use strict';

// Runs in <head> before the first paint so a saved theme choice never flashes the other theme.
// Without a saved choice the page follows the system setting (prefers-color-scheme) through CSS.
(function applySavedTheme() {
  try {
    const saved = localStorage.getItem('fm.theme');
    if (saved === 'light' || saved === 'dark') {
      document.documentElement.dataset.theme = saved;
    }
  } catch {
    // storage can be unavailable (private mode); the system theme is used then
  }
})();
