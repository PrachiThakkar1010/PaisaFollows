// Login page: POST /api/auth/login, then go to the dashboard
import { api } from '../api.js';
import { $, errorMessage } from '../util.js';

// Already logged in? Skip the form.
api.me().then(() => { window.location.href = '/dashboard.html'; }).catch(() => {});

$('#login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;
  const button = $('button', form);
  $('#error').textContent = '';
  button.disabled = true;
  button.textContent = 'Logging in…';
  try {
    await api.login(form.email.value.trim(), form.password.value);
    window.location.href = '/dashboard.html';
  } catch (err) {
    $('#error').textContent = errorMessage(err);
    button.disabled = false;
    button.textContent = 'Log in';
  }
});