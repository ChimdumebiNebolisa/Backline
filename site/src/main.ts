import './style.css';

const main = document.querySelector<HTMLElement>('#main-content');

if (!main) {
  throw new Error('Site shell is missing #main-content');
}

main.dataset.ready = 'true';
