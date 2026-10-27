// Sign-up page: POST /api/auth/register, then log in automatically
import { api } from '../api.js';
import { $, errorMessage } from '../util.js';

$('#register-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const button = $('button', f);
  $('#error').textContent = '';
  button.disabled = true;
  button.textContent = 'Creating…';
  try {
    await api.register({
      name: f.fullName.value.trim(),  // (an input called "name" would clash with form.name)
      email: f.email.value.trim(),
      password: f.password.value,
      phone: f.phone.value.trim() || undefined,
      dateOfBirth: f.dateOfBirth.value,
      pan: f.pan.value.trim().toUpperCase(),
    });
    await api.login(f.email.value.trim(), f.password.value);
    window.location.href = '/dashboard.html';
  } catch (err) {
    $('#error').textContent = errorMessage(err);
    button.disabled = false;
    button.textContent = 'Create account';
  }
});