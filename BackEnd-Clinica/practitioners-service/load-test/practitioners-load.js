import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8085';
const TOKEN = __ENV.TOKEN;
const PRACTITIONER_UUID = __ENV.PRACTITIONER_UUID;
const SPECIALTY_CODE = __ENV.SPECIALTY_CODE;
const ACCOUNT_UUID = __ENV.ACCOUNT_UUID;
const MULTIPLIER = Number(__ENV.MULTIPLIER || 1);
const DURATION = __ENV.DURATION || '3m';
const SUMMARY_FILE = __ENV.SUMMARY_FILE;

const PEAK_RATES_PER_SECOND = {
  readPractitioner: 20,
  searchBySpecialty: 8,
  readCatalogue: 6,
  findByAccount: 10,
};

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
    readPractitioner: scenario('readPractitioner', PEAK_RATES_PER_SECOND.readPractitioner),
    searchBySpecialty: scenario('searchBySpecialty', PEAK_RATES_PER_SECOND.searchBySpecialty),
    readCatalogue: scenario('readCatalogue', PEAK_RATES_PER_SECOND.readCatalogue),
    findByAccount: scenario('findByAccount', PEAK_RATES_PER_SECOND.findByAccount),
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{scenario:readPractitioner}': ['p(95)<200'],
    'http_req_duration{scenario:searchBySpecialty}': ['p(95)<400'],
    'http_req_duration{scenario:readCatalogue}': ['p(95)<300'],
    'http_req_duration{scenario:findByAccount}': ['p(95)<200'],
  },
};

export function setup() {
  if (!TOKEN) {
    fail('Define TOKEN con un token de talento humano o de administración');
  }
  if (!PRACTITIONER_UUID || !SPECIALTY_CODE || !ACCOUNT_UUID) {
    fail('Define PRACTITIONER_UUID, SPECIALTY_CODE y ACCOUNT_UUID con datos ya cargados (ver README)');
  }
}

export function readPractitioner() {
  const response = http.get(`${BASE_URL}/api/v1/practitioners/${PRACTITIONER_UUID}`, { headers });
  check(response, { 'practitioner 200': (r) => r.status === 200 });
}

export function searchBySpecialty() {
  const response = http.post(`${BASE_URL}/api/v1/practitioners/search`,
    JSON.stringify({ specialtyCode: SPECIALTY_CODE }), { headers });
  check(response, { 'search 200': (r) => r.status === 200 });
}

export function readCatalogue() {
  const response = http.get(`${BASE_URL}/api/v1/specialties?status=ACTIVE`, { headers });
  check(response, { 'catalogue 200': (r) => r.status === 200 });
}

export function findByAccount() {
  const response = http.post(`${BASE_URL}/api/v1/practitioners/search`,
    JSON.stringify({ authUserUuid: ACCOUNT_UUID }), { headers });
  check(response, { 'account 200': (r) => r.status === 200 });
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
