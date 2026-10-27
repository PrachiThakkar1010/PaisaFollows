// =========================================================
// Light / dark theme.
// Loaded as a normal <script> at the very top of every page's <head>,
// so the right colours are set BEFORE the page is drawn (no white flash).
//
//  - First visit: follows the computer's setting (Windows: Settings > Personalisation > Colours).
//  - After the user clicks the switch: their choice is remembered in this browser.
//  - Any button with the attribute data-theme-toggle switches the theme.
// =========================================================
(function () {
  var KEY = 'pf-theme';
  var media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function saved() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function systemTheme() {
    return media && media.matches ? 'dark' : 'light';
  }
  function apply(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    // tell the chart (and anything else listening) that the colours changed
    window.dispatchEvent(new CustomEvent('pf-theme-change', { detail: theme }));
  }

  // 1. Set the theme straight away
  apply(saved() || systemTheme());

  // 2. Follow the computer's setting while the user has not picked one
  if (media && media.addEventListener) {
    media.addEventListener('change', function () { if (!saved()) apply(systemTheme()); });
  }

  // 3. Public helpers: window.pfTheme.current() and window.pfTheme.toggle()
  window.pfTheme = {
    current: function () { return document.documentElement.getAttribute('data-theme'); },
    toggle: function () {
      var next = this.current() === 'dark' ? 'light' : 'dark';
      try { localStorage.setItem(KEY, next); } catch (e) { /* private window: just don't remember */ }
      apply(next);
      return next;
    },
  };

  // 4. Every [data-theme-toggle] button switches the theme (works for buttons added later too)
  document.addEventListener('click', function (e) {
    if (e.target.closest && e.target.closest('[data-theme-toggle]')) window.pfTheme.toggle();
  });

  // 5. Pages without the site header (login, sign-up) get a floating switch in the corner
  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('.auth') && !document.querySelector('[data-theme-toggle]')) {
      var holder = document.createElement('div');
      holder.innerHTML = window.pfTheme.buttonHtml('floating');
      document.body.appendChild(holder.firstChild);
    }
  });

  /** HTML for the switch: a moon (shown in light mode) and a sun (shown in dark mode) */
  window.pfTheme.buttonHtml = function (extraClass) {
    return '<button type="button" class="theme-toggle ' + (extraClass || '') + '" data-theme-toggle '
      + 'aria-label="Switch between light and dark mode" title="Light / dark mode">'
      + '<svg class="moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>'
      + '<svg class="sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>'
      + '</button>';
  };
})();