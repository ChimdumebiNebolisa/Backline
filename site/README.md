# Backline site

This is the standalone public landing page for Backline. It is intentionally independent from the Java/Gradle project:

- the site has its own `package.json` and lockfile;
- Vite builds static assets into `dist/`;
- no site source imports Java or backend source;
- no PostgreSQL, API server, worker, or Gradle process is required to build or test it.

## Local commands

From this directory:

```bash
npm ci
npm run typecheck
npm run lint
npm test
npm run build
npm run browser:test
```

`npm run browser:test` starts a local Vite server through Playwright and checks the rendered page. It does not call the Backline API.
