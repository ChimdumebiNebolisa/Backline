# Backline site

This is the standalone public landing page for the Backline rollout compatibility verifier:

- the site has its own `package.json` and lockfile;
- Vite builds static assets into `dist/`;
- no site source imports the Go CLI or starts Docker;
- the page uses observed output from the deterministic rollout fixtures;
- no Backline server, database, worker, or language runtime is required to build or test it.

## Local commands

From this directory:

```bash
npm ci
npm audit
npm run typecheck
npm run lint
npm test
npm run build
npm run browser:test
npm run lighthouse
```

`npm run browser:test` starts a local Vite server through Playwright and checks the rendered page. It does not execute Backline or Docker.
`npm run lighthouse` runs three audits, requires 95 performance, 100 accessibility, 95 best-practices, and 95 SEO scores in every run, and writes JSON reports under `.lighthouseci/`.
