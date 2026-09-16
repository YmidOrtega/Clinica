import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8087';
const TOKEN = __ENV.TOKEN;
const CONTRACT_UUID = __ENV.CONTRACT_UUID;
const PAYER_UUID = __ENV.PAYER_UUID;
const QUOTE_DATE = __ENV.QUOTE_DATE || '2026-03-01';
const MULTIPLIER = Number(__ENV.MULTIPLIER || 1);
const DURATION = __ENV.DURATION || '3m';
const SUMMARY_FILE = __ENV.SUMMARY_FILE;

const PEAK_RATES_PER_SECOND = {
  quotePrice: 25,
  readContract: 8,
  searchPayer: 4,
  coverage: 3,
};

const CUPS = ['890201', '903841', '881235', '890301'];
const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };

function scenario(exec, peakRate) {
  const rate = peakRate * MULTIPLIER;
  return {
    executor: 'ramping-arrival-rate',
    exec,
    startRate: 0,
    timeUnit: '1s',
    preAllocatedVUs: Math.max(10, Math.ceil(rate * 2)),
    maxVUs: Math.max(50, Math.ceil(rate * 10)),
    stages: [
      { target: rate, duration: '30s' },
      { target: rate, duration: DURATION },
      { target: 0, duration: '15s' },
    ],
  };
}

export const options = {
  scenarios: {
    quotePrice: scenario('quotePrice', PEAK_RATES_PER_SECOND.quotePrice),
    readContract: scenario('readContract', PEAK_RATES_PER_SECOND.readContract),
    searchPayer: scenario('searchPayer', PEAK_RATES_PER_SECOND.searchPayer),
    coverage: scenario('coverage', PEAK_RATES_PER_SECOND.coverage),
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{scenario:quotePrice}': ['p(95)<400'],
    'http_req_duration{scenario:readContract}': ['p(95)<200'],
    'http_req_duration{scenario:searchPayer}': ['p(95)<300'],
    'http_req_duration{scenario:coverage}': ['p(95)<300'],
  },
};

export function setup() {
  if (!TOKEN) {
    fail('Define TOKEN con un token de un usuario con permisos de contratación');
  }
  if (!CONTRACT_UUID || !PAYER_UUID) {
    fail('Define CONTRACT_UUID y PAYER_UUID con datos ya cargados (ver README)');
  }
}

export function quotePrice() {
  const services = [{ cupsCode: CUPS[Math.floor(Math.random() * CUPS.length)], quantity: 1 + Math.floor(Math.random() * 3) }];
  const response = http.post(`${BASE_URL}/api/v1/price-quotes`,
    JSON.stringify({ contractUuid: CONTRACT_UUID, on: QUOTE_DATE, services }), { headers });
  check(response, { 'quote 200': (r) => r.status === 200 });
}

export function readContract() {
  const response = http.get(`${BASE_URL}/api/v1/contracts/${CONTRACT_UUID}`, { headers });
  check(response, { 'contract 200': (r) => r.status === 200 });
}

export function searchPayer() {
  const response = http.post(`${BASE_URL}/api/v1/payers/search`, JSON.stringify({ socialReason: 'E' }), { headers });
  check(response, { 'search 200': (r) => r.status === 200 });
}

export function coverage() {
  const document = String(1000000000 + Math.floor(Math.random() * 1000000));
  const response = http.get(
    `${BASE_URL}/api/v1/capitated-members/coverage?documentType=CEDULA_DE_CIUDADANIA&documentNumber=${document}&on=${QUOTE_DATE}`,
    { headers });
  check(response, { 'coverage 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  const output = { stdout: JSON.stringify(summary(data), null, 2) };
  if (SUMMARY_FILE) {
    output[SUMMARY_FILE] = JSON.stringify(data, null, 2);
  }
  return output;
}

function summary(data) {
  const durations = {};
  for (const [name, metric] of Object.entries(data.metrics)) {
    if (name.startsWith('http_req_duration{scenario:')) {
      durations[name.replace('http_req_duration{scenario:', '').replace('}', '')] = {
        p95: Math.round(metric.values['p(95)']),
        avg: Math.round(metric.values.avg),
      };
    }
  }
  return {
    multiplier: MULTIPLIER,
    requests: data.metrics.http_reqs.values.count,
    failureRate: data.metrics.http_req_failed.values.rate,
    p95ByScenario: durations,
  };
}
