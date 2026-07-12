import './style.css';

const menuToggle = document.querySelector<HTMLButtonElement>('#menu-toggle');
const siteNav = document.querySelector<HTMLElement>('#site-nav');

if (!menuToggle || !siteNav) {
  throw new Error('Site navigation is missing its toggle or navigation element');
}

const closeMenu = (): void => {
  menuToggle.setAttribute('aria-expanded', 'false');
  siteNav.classList.remove('is-open');
};

menuToggle.addEventListener('click', () => {
  const isOpen = menuToggle.getAttribute('aria-expanded') === 'true';
  menuToggle.setAttribute('aria-expanded', String(!isOpen));
  siteNav.classList.toggle('is-open', !isOpen);
});

siteNav.querySelectorAll('a').forEach((link) => {
  link.addEventListener('click', closeMenu);
});

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    closeMenu();
    menuToggle.focus();
  }
});

window.matchMedia('(min-width: 760px)').addEventListener('change', closeMenu);
