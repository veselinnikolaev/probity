/**
 * PR-D0 — 1-replica worker baseline (Phase 4 §2.9).
 *
 * Drives the async simulation pipeline end to end:
 *   GET  /api/v1/auth/csrf                       -> XSRF-TOKEN
 *   POST /api/v1/auth/login                      -> access_token (5 req/60s — once, in setup)
 *   POST /api/v1/simulations/run-async           -> 202 + Location  (Idempotency-Key per iteration)
 *   GET  {Location}                              -> poll status until COMPLETED
 *
 * Measures:
 *   accept_latency_ms  - POST /run-async round trip (broker queue write + PENDING row)
 *   e2e_latency_ms     - POST to terminal status (full worker pipeline incl. Kafka + Monte Carlo)
 *   server_latency_ms  - updatedAt - createdAt taken from the COMPLETED status response.
 *                        createdAt is the PENDING insert; updatedAt is the timestamp the
 *                        completion write stamps (read just before that UPDATE runs). It is the
 *                        server-side wait WITHOUT the client poll floor, and a LOWER bound: the
 *                        completion UPDATE's own commit time is not included (see the docs).
 *   simulations_completed / simulations_failed   - counters
 *   status_polls       - how many status polls each simulation needed
 *
 * Run against a local 1-replica app (the PR-D0 jar) with the compose kafka/postgres/redis:
 *
 *   $PORTFOLIO_ID = <seeded portfolio uuid>
 *   Get-Content loadtest/k6/baseline.js -Raw |
 *     docker run --rm -i grafana/k6 run \
 *       -e BASE_URL=http://host.docker.internal:8080 \
 *       -e PORTFOLIO_ID=$PORTFOLIO_ID \
 *       -e VUS=6 -e ITERATIONS=60 -e PASSWORD=Password123! -e USER=admin \
 *       -
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const USERNAME = __ENV.USER || 'admin';
const PASSWORD = __ENV.PASSWORD || 'Password123!';
const PORTFOLIO_ID = __ENV.PORTFOLIO_ID || '';
if (!PORTFOLIO_ID) {
  throw new Error('PORTFOLIO_ID env var is required');
}

const completed = new Counter('simulations_completed');
const failed = new Counter('simulations_failed');
const acceptMs = new Trend('accept_latency_ms');
const e2eMs = new Trend('e2e_latency_ms');
const serverMs = new Trend('server_latency_ms');
const polls = new Trend('status_polls');

export const options = {
  scenarios: {
    baseline: {
      executor: 'shared-iterations',
      vus: __ENV.VUS ? Number(__ENV.VUS) : 6,
      iterations: __ENV.ITERATIONS ? Number(__ENV.ITERATIONS) : 60,
      maxDuration: '10m',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    // A single failed simulation means the pipeline regressed; abort the run and show why.
    'simulations_failed': [{ threshold: 'count==0', abortOnFail: true }],
  },
};

function uuidv4() {
  // RFC 4122 v4 — sufficient for one-shot idempotency keys (pattern ^[a-zA-Z0-9_-]{8,256}$).
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function setup() {
  const xsrfRes = http.get(`${BASE}/api/v1/auth/csrf`);
  check(xsrfRes, { 'csrf returned 200': (r) => r.status === 200 });
  const xsrf = xsrfRes.cookies['XSRF-TOKEN'] ? xsrfRes.cookies['XSRF-TOKEN'][0].value : '';

  const login = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({
    identifier: USERNAME,
    password: PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  check(login, { 'login returned 200': (r) => r.status === 200 });
  const access = login.cookies['access_token'] ? login.cookies['access_token'][0].value : '';

  if (!xsrf || !access) {
    throw new Error(`setup failed: xsrf=${xsrf.length > 0} access=${access.length > 0} ` +
      `loginStatus=${login.status} csrfStatus=${xsrfRes.status}`);
  }
  return { xsrf, access };
}

export default function (data) {
  const { xsrf, access } = data;
  const idempotencyKey = uuidv4();

  const body = JSON.stringify({
    portfolioId: PORTFOLIO_ID,
    numberOfSimulations: 1000,
    timeHorizonDays: 90,
    confidenceLevel: 0.95,
    assumedReturnPercent: null,
    assumedVolatilityPercent: null,
  });

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${access}`,
    // CookieCsrfTokenRepository: the token is loaded from the XSRF-TOKEN cookie and must
    // also be echoed back in X-XSRF-TOKEN. The setup() cookie jar never reaches VUs, so
    // the cookie is sent explicitly.
    'Cookie': `XSRF-TOKEN=${xsrf}`,
    'X-XSRF-TOKEN': xsrf,
    'Idempotency-Key': idempotencyKey,
  };

  const start = Date.now();
  const accept = http.post(`${BASE}/api/v1/simulations/run-async`, body, { headers });
  acceptMs.add(Date.now() - start);

  if (accept.status !== 202) {
    failed.add(1);
    console.log(`accept failed: status=${accept.status} body=${accept.body.slice(0, 300)}`);
    return;
  }

  let location = accept.headers['Location'] || accept.headers['Location'.toLowerCase()];
  if (!location) {
    failed.add(1);
    console.log('202 response missing Location header');
    return;
  }
  if (location.startsWith('/')) {
    location = `${BASE}${location}`;
  }

  const authHeader = { 'Authorization': `Bearer ${access}` };
  let status = '';
  let count = 0;
  let serverLatencyMs = null;
  for (let i = 0; i < 150; i++) {
    const res = http.get(location, { headers: authHeader });
    count++;
    if (res.status === 200) {
      try {
        const body = JSON.parse(res.body);
        status = body.status;
        // On COMPLETED, updatedAt is the completion-write timestamp and createdAt is the
        // PENDING insert; their delta is the server-side wait with no client poll floor
        // (it excludes the completion UPDATE's commit time — a lower bound).
        if (status === 'COMPLETED' && body.createdAt && body.updatedAt) {
          const createdAt = Date.parse(body.createdAt);
          const updatedAt = Date.parse(body.updatedAt);
          if (!Number.isNaN(createdAt) && !Number.isNaN(updatedAt) && updatedAt >= createdAt) {
            serverLatencyMs = updatedAt - createdAt;
          }
        }
      } catch (_) {
        // not JSON yet — keep polling
      }
      if (status === 'COMPLETED' || status === 'FAILED') {
        break;
      }
    }
    sleep(0.2);
  }

  polls.add(count);
  e2eMs.add(Date.now() - start);
  if (serverLatencyMs !== null) {
    serverMs.add(serverLatencyMs);
  }

  if (status === 'COMPLETED') {
    completed.add(1);
  } else {
    failed.add(1);
    console.log(`simulation ${idempotencyKey} did not complete: status=${status} polls=${count}`);
  }
}