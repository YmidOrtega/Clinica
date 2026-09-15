import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { check, fail, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

const AUTH = __ENV.AUTH_URL || 'http://auth-service:8086';
const OPENBAO = __ENV.OPENBAO_URL || 'https://openbao:8200';
const OPENBAO_TOKEN = __ENV.OPENBAO_TOKEN;
const ISSUER = __ENV.AUTH_ISSUER || 'http://localhost:8080/auth';
const REDIRECT_URI = __ENV.REDIRECT_URI || 'http://localhost:8080/login/oauth2/code/clinica';
const PASSWORD = __ENV.LOAD_PASSWORD || 'frase de la prueba de carga del turno';
const MULTIPLIER = Number(__ENV.MULTIPLIER || 1);
const DURATION = __ENV.DURATION || '5m';
const REFRESH_SESSIONS = 300 * MULTIPLIER;
const EXCHANGE_SESSIONS = 50 * MULTIPLIER;
const SIGN_IN_VUS = 100 * MULTIPLIER;
const PREPARED_SESSIONS = SIGN_IN_VUS + REFRESH_SESSIONS + EXCHANGE_SESSIONS;
const TOKEN_EXCHANGE = 'urn:ietf:params:oauth:grant-type:token-exchange';
const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';

const staff = new SharedArray('staff', () => open(__ENV.STAFF_FILE || '/scripts/staff.csv').trim().split('\n').slice(1)
  .map((line) => { const [uuid, email, role, secret] = line.split(','); return { uuid, email, role, secret }; }));

const signIn = new Trend('clinica_sign_in', true);
const tokenIssued = new Trend('clinica_token_authorization_code', true);
const refreshed = new Trend('clinica_token_refresh', true);
const exchanged = new Trend('clinica_token_exchange', true);
const failures = new Counter('clinica_failures');

export const options = {
  setupTimeout: '20m',
  insecureSkipTLSVerify: true,
  scenarios: {
    shiftChange: {
      executor: 'constant-arrival-rate', exec: 'shiftChange', rate: 80 * MULTIPLIER, timeUnit: '1m', duration: DURATION,
      preAllocatedVUs: 20, maxVUs: SIGN_IN_VUS,
    },
    sustainedRefresh: {
      executor: 'constant-vus', exec: 'sustainedRefresh', vus: REFRESH_SESSIONS, duration: DURATION,
    },
    tokenExchange: {
      executor: 'constant-arrival-rate', exec: 'tokenExchange', rate: 50 * MULTIPLIER, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: EXCHANGE_SESSIONS, maxVUs: EXCHANGE_SESSIONS,
    },
  },
  thresholds: {
    clinica_failures: ['count<1'],
    'http_req_duration{name:login}': ['p(95)<800'],
    'http_req_duration{name:second-factor}': ['p(95)<500'],
    clinica_sign_in: ['p(95)<2500'],
    clinica_token_authorization_code: ['p(95)<500'],
    clinica_token_refresh: ['p(95)<300'],
    clinica_token_exchange: ['p(95)<300'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

const b64url = (value) => encoding.b64encode(value, 'rawurl');

const base32 = (secret) => {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bytes = [];
  let bits = 0;
  let value = 0;
  for (const character of secret) {
    value = (value << 5) | alphabet.indexOf(character);
    bits += 5;
    if (bits >= 8) {
      bytes.push((value >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }
  return new Uint8Array(bytes).buffer;
};

const totp = (secret) => {
  const counter = Math.floor(Date.now() / 30000);
  const message = new Uint8Array(8);
  let remaining = counter;
  for (let index = 7; index >= 0; index--) {
    message[index] = remaining & 255;
    remaining = Math.floor(remaining / 256);
  }
  const digest = new Uint8Array(crypto.hmac('sha1', base32(secret), message.buffer, 'binary'));
  const offset = digest[digest.length - 1] & 15;
  const binary = ((digest[offset] & 127) << 24) | (digest[offset + 1] << 16) | (digest[offset + 2] << 8) | digest[offset + 3];
  return String(binary % 1000000).padStart(6, '0');
};

const assertion = (clientId, transitKey) => {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: 'ES256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify({ iss: clientId, sub: clientId, aud: ISSUER, iat: now, exp: now + 60,
    jti: `${Date.now()}-${Math.random()}` }));
  const signed = http.post(`${OPENBAO}/v1/transit/sign/${transitKey}`,
    JSON.stringify({ input: encoding.b64encode(`${header}.${payload}`), hash_algorithm: 'sha2-256', marshaling_algorithm: 'jws' }),
    { headers: { 'X-Vault-Token': OPENBAO_TOKEN, 'Content-Type': 'application/json' }, tags: { name: 'transit-sign' } });
  if (signed.status !== 200) {
    fail(`transit sign ${signed.status}: ${signed.body}`);
  }
  return `${header}.${payload}.${signed.json('data.signature').split(':')[2]}`;
};

const tokenRequest = (clientId, transitKey, grant, name) => http.post(`${AUTH}/oauth2/token`, Object.assign({
  client_id: clientId,
  client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
  client_assertion: assertion(clientId, transitKey),
}, grant), { tags: { name } });

const query = (url, key) => {
  const match = url.match(new RegExp(`[?&]${key}=([^&]*)`));
  return match ? decodeURIComponent(match[1]) : null;
};

const signInAs = (member, measured) => {
  const jar = new http.CookieJar();
  const verifier = b64url(crypto.randomBytes(32));
  const challenge = b64url(crypto.sha256(verifier, 'binary'));
  const state = b64url(crypto.randomBytes(12));
  const started = Date.now();
  const authorize = `${AUTH}/oauth2/authorize?response_type=code&client_id=api-gateway&scope=openid%20profile&state=${state}`
    + `&code_challenge=${challenge}&code_challenge_method=S256&redirect_uri=${encodeURIComponent(REDIRECT_URI)}`;
  http.get(authorize, { jar, redirects: 0, tags: { name: 'authorize' } });
  const csrf = http.get(`${AUTH}/api/v1/session`, { jar, tags: { name: 'session' } }).json('csrf');
  const headers = { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token };
  const login = http.post(`${AUTH}/api/v1/login`, JSON.stringify({ email: member.email, password: PASSWORD }), { jar, headers, tags: { name: 'login' } });
  if (!check(login, { 'password accepted': (response) => response.status === 200 })) {
    return null;
  }
  const secondFactor = http.post(`${AUTH}/api/v1/login/second-factor`, JSON.stringify({ code: totp(member.secret) }),
    { jar, headers, tags: { name: 'second-factor' } });
  if (!check(secondFactor, { 'second factor accepted': (response) => response.status === 200 })) {
    return null;
  }
  const callback = http.get(secondFactor.json('continueUrl'), { jar, redirects: 0, tags: { name: 'authorize-with-session' } });
  const code = query(callback.headers.Location || '', 'code');
  if (!check(code, { 'authorization code issued': (value) => value !== null })) {
    return null;
  }
  const tokens = tokenRequest('api-gateway', 'api-gateway-client',
    { grant_type: 'authorization_code', code, redirect_uri: REDIRECT_URI, code_verifier: verifier }, 'token-authorization-code');
  if (measured) {
    tokenIssued.add(tokens.timings.duration);
  }
  if (!check(tokens, { 'tokens issued': (response) => response.status === 200 })) {
    return null;
  }
  if (measured) {
    signIn.add(Date.now() - started);
  }
  return { access: tokens.json('access_token'), refresh: tokens.json('refresh_token'), renewAt: Date.now() + 240000 };
};

export function setup() {
  if (!OPENBAO_TOKEN) {
    fail('OPENBAO_TOKEN is required to sign client assertions in transit');
  }
  const sessions = [];
  for (let index = 0; index < PREPARED_SESSIONS; index++) {
    const session = signInAs(staff[index], false);
    if (!session) {
      fail(`could not prepare the session of ${staff[index].email}`);
    }
    sessions.push(session);
  }
  return { sessions };
}

export function shiftChange() {
  const offset = PREPARED_SESSIONS;
  const member = staff[offset + (exec.scenario.iterationInTest % (staff.length - offset))];
  if (!signInAs(member, true)) {
    failures.add(1, { scenario: 'shiftChange' });
  }
}

let session = null;

const refresh = (current, scenario) => {
  const response = tokenRequest('api-gateway', 'api-gateway-client', { grant_type: 'refresh_token', refresh_token: current.refresh }, 'token-refresh');
  refreshed.add(response.timings.duration);
  if (!check(response, { 'refresh accepted': (res) => res.status === 200 })) {
    failures.add(1, { scenario });
    return current;
  }
  return { access: response.json('access_token'), refresh: response.json('refresh_token'), renewAt: Date.now() + 240000 };
};

export function sustainedRefresh(data) {
  if (!session) {
    sleep(Math.random() * 30);
  }
  session = refresh(session || data.sessions[exec.vu.idInTest - 1], 'sustainedRefresh');
  sleep(30);
}

let serviceToken = null;

export function tokenExchange(data) {
  session = session || data.sessions[exec.vu.idInTest - 1];
  if (Date.now() >= session.renewAt) {
    session = refresh(session, 'tokenExchange');
  }
  if (!serviceToken || Date.now() > serviceToken.renewAt) {
    const issued = tokenRequest('clinical-history-service', 'clinical-history-service-client', { grant_type: 'client_credentials' }, 'token-client-credentials');
    serviceToken = { value: issued.json('access_token'), renewAt: Date.now() + 25 * 60000 };
  }
  const response = tokenRequest('clinical-history-service', 'clinical-history-service-client', {
    grant_type: TOKEN_EXCHANGE, subject_token: session.access, subject_token_type: ACCESS_TOKEN_TYPE, actor_token: serviceToken.value,
    actor_token_type: ACCESS_TOKEN_TYPE, audience: 'patient-service',
  }, 'token-exchange');
  exchanged.add(response.timings.duration);
  if (!check(response, { 'exchange accepted': (res) => res.status === 200 })) {
    failures.add(1, { scenario: 'tokenExchange' });
  }
}
