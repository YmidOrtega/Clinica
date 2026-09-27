import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8095';
const TOKEN = __ENV.TOKEN;
const ADMISSION_NUMBER = __ENV.ADMISSION_NUMBER;
const INVOICE_UUID = __ENV.INVOICE_UUID;
const PORTFOLIO_ITEM_UUID = __ENV.PORTFOLIO_ITEM_UUID;
const MULTIPLIER = Number(__ENV.MULTIPLIER || 1);
const DURATION = __ENV.DURATION || '3m';
const ISSUE_RATE_PER_MINUTE = Number(__ENV.ISSUE_RATE_PER_MINUTE || 18);
const SUMMARY_FILE = __ENV.SUMMARY_FILE;
const API = `${BASE_URL}/api/v1/billing`;

const READ_RATE_PER_SECOND = 10;
const CHARGING_CLERKS = 4;

const issued = new Counter('invoices_issued');
const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    reads: {
      executor: 'ramping-arrival-rate',
      exec: 'reads',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 20 * MULTIPLIER,
      maxVUs: 100 * MULTIPLIER,
      stages: [
        { target: READ_RATE_PER_SECOND * MULTIPLIER, duration: '30s' },
        { target: READ_RATE_PER_SECOND * MULTIPLIER, duration: DURATION },
        { target: 0, duration: '15s' },
      ],
    },
    charges: {
      executor: 'constant-vus',
      exec: 'charges',
      vus: CHARGING_CLERKS,
      duration: DURATION,
    },
    invoicing: {
      executor: 'constant-arrival-rate',
      exec: 'invoicing',
      rate: ISSUE_RATE_PER_MINUTE,
      timeUnit: '1m',
      duration: DURATION,
      preAllocatedVUs: 5,
      maxVUs: 20,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{operation:read}': ['p(95)<300'],
    'http_req_duration{operation:charge}': ['p(95)<500'],
    'http_req_duration{operation:issue}': ['p(95)<2000'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  if (!TOKEN || !ADMISSION_NUMBER || !INVOICE_UUID || !PORTFOLIO_ITEM_UUID) {
    fail('Define TOKEN, ADMISSION_NUMBER, INVOICE_UUID y PORTFOLIO_ITEM_UUID (ver README)');
  }
}

function tagged(operation) {
  return { headers, tags: { operation } };
}

function version(response) {
  return (response.headers.Etag || response.headers.ETag || '').replaceAll('"', '');
}

export function reads() {
  const reads = [
    `${API}/accounts/${ADMISSION_NUMBER}/summary`,
    `${API}/accounts/${ADMISSION_NUMBER}/invoices`,
    `${API}/invoices/${INVOICE_UUID}`,
    `${API}/filings/pending`,
    `${API}/objections/pending`,
  ];
  const response = http.get(reads[Math.floor(Math.random() * reads.length)], tagged('read'));
  check(response, { 'lectura 200': (r) => r.status === 200 });
}

const clerk = { sale: null, version: null };

export function charges() {
  if (!clerk.sale) {
    const opened = http.post(`${API}/sales`, JSON.stringify({
      admissionNumber: ADMISSION_NUMBER, type: 'NON_SURGICAL', preloadAuthorized: false }), tagged('charge'));
    if (!check(opened, { 'venta abierta 201': (r) => r.status === 201 })) {
      return;
    }
    clerk.sale = opened.json('uuid');
    clerk.version = version(opened);
  }
  const charged = http.post(`${API}/sales/${clerk.sale}/lines`,
    JSON.stringify({ portfolioItemUuid: PORTFOLIO_ITEM_UUID, quantity: 1 }),
    { headers: { ...headers, 'If-Match': `"${clerk.version}"` }, tags: { operation: 'charge' } });
  if (check(charged, { 'cargo 200': (r) => r.status === 200 })) {
    clerk.version = version(charged);
  }
  sleep(1);
}

export function invoicing() {
  const opened = http.post(`${API}/sales`, JSON.stringify({
    admissionNumber: ADMISSION_NUMBER, type: 'NON_SURGICAL', preloadAuthorized: false }), tagged('charge'));
  if (!check(opened, { 'venta para facturar 201': (r) => r.status === 201 })) {
    return;
  }
  const sale = opened.json('uuid');
  const charged = http.post(`${API}/sales/${sale}/lines`,
    JSON.stringify({ portfolioItemUuid: PORTFOLIO_ITEM_UUID, quantity: 1 }),
    { headers: { ...headers, 'If-Match': `"${version(opened)}"` }, tags: { operation: 'charge' } });
  const confirmed = http.post(`${API}/sales/${sale}/confirmation`, null,
    { headers: { ...headers, 'If-Match': `"${version(charged)}"` }, tags: { operation: 'charge' } });
  if (!check(confirmed, { 'venta confirmada 200': (r) => r.status === 200 })) {
    return;
  }
  const drafted = http.post(`${API}/invoices`, JSON.stringify({ admissionNumber: ADMISSION_NUMBER, saleUuid: sale }),
    tagged('issue'));
  if (!check(drafted, { 'borrador 201': (r) => r.status === 201 })) {
    return;
  }
  const emitted = http.post(`${API}/invoices/${drafted.json('uuid')}/issuance`, null,
    { headers: { ...headers, 'If-Match': `"${version(drafted)}"` }, tags: { operation: 'issue' } });
  if (check(emitted, { 'factura emitida y firmada': (r) => r.status === 200 && r.json('signedAt') !== null })) {
    issued.add(1);
  }
}

export function teardown() {
  const response = http.get(`${API}/accounts/${ADMISSION_NUMBER}/invoices`, { headers });
  const numbers = response.json().filter((invoice) => invoice.number).map((invoice) => invoice.number);
  const consecutives = numbers.map((number) => Number(number.replace(/^[A-Z]+/, ''))).sort((a, b) => a - b);
  const unique = new Set(consecutives).size === consecutives.length;
  const gapless = consecutives.every((value, index) => index === 0 || value === consecutives[index - 1] + 1);
  check(consecutives, {
    'ningún consecutivo repetido': () => unique,
    'consecutivos sin huecos': () => gapless,
  });
}

export function handleSummary(data) {
  const output = { stdout: JSON.stringify(summary(data), null, 2) + '\n' };
  if (SUMMARY_FILE) {
    output[SUMMARY_FILE] = JSON.stringify(data, null, 2);
  }
  return output;
}

function summary(data) {
  const durations = {};
  for (const [name, metric] of Object.entries(data.metrics)) {
    if (name.startsWith('http_req_duration{operation:')) {
      durations[name.replace('http_req_duration{operation:', '').replace('}', '')] = {
        p95: Math.round(metric.values['p(95)']),
        avg: Math.round(metric.values.avg),
      };
    }
  }
  return {
    multiplier: MULTIPLIER,
    requests: data.metrics.http_reqs.values.count,
    failedRate: data.metrics.http_req_failed.values.rate,
    checksRate: data.metrics.checks.values.rate,
    invoicesIssued: data.metrics.invoices_issued ? data.metrics.invoices_issued.values.count : 0,
    durations,
  };
}
