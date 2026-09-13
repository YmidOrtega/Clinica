import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8081';
const TOKEN = __ENV.TOKEN;
const MULTIPLIER = Number(__ENV.MULTIPLIER || 1);
const DURATION = __ENV.DURATION || '3m';
const SEEDED_PATIENTS = Number(__ENV.SEEDED_PATIENTS || 500000);
const SUMMARY_FILE = __ENV.SUMMARY_FILE;

const PEAK_RATES_PER_SECOND = {
  readPatient: 20,
  searchDocument: 6,
  searchName: 2,
  registerPatient: 1,
  updateContact: 1,
};

const NAMES = [
  { lastNames: 'gomez rojas' },
  { lastNames: 'restrepo', firstNames: 'ana' },
  { lastNames: 'martinez' },
  { lastNames: 'ortiz', firstNames: 'valentina' },
  { lastNames: 'perez muñoz', firstNames: 'carlos' },
  { lastNames: 'castro' },
];
const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };

function scenario(exec, peakRate) {
  const rate = peakRate * MULTIPLIER;
  return {
    executor: 'ramping-arrival-rate',
    exec,
    startRate: 0,
    timeUnit: '1s',
    preAllocatedVUs: Math.max(5, rate * 2),
    maxVUs: Math.max(20, rate * 10),
    stages: [
      { target: rate, duration: '30s' },
      { target: rate, duration: DURATION },
      { target: 0, duration: '10s' },
    ],
  };
}

export const options = {
  scenarios: Object.fromEntries(
    Object.entries(PEAK_RATES_PER_SECOND).map(([name, rate]) => [name, scenario(name, rate)]),
  ),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{scenario:readPatient}': ['p(95)<200'],
    'http_req_duration{scenario:searchDocument}': ['p(95)<200'],
    'http_req_duration{scenario:searchName}': ['p(95)<500'],
    'http_req_duration{scenario:registerPatient}': ['p(95)<500'],
    'http_req_duration{scenario:updateContact}': ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

const pick = (items) => items[Math.floor(Math.random() * items.length)];
const randomSeededDocument = () => String(1000000001 + Math.floor(Math.random() * SEEDED_PATIENTS));

function searchByDocument(number) {
  return http.post(
    `${BASE_URL}/api/v1/patients/search`,
    JSON.stringify({ document: { type: 'CEDULA_DE_CIUDADANIA', number } }),
    { headers, tags: { name: 'POST /patients/search (document)' } },
  );
}

export function setup() {
  if (!TOKEN) {
    fail('TOKEN is required');
  }
  const uuids = [];
  for (let i = 0; i < 300; i++) {
    const uuid = searchByDocument(randomSeededDocument()).json('content.0.uuid');
    if (uuid) {
      uuids.push(uuid);
    }
  }
  if (uuids.length === 0) {
    fail('No seeded patients found; load seed-patients.sql first');
  }
  return { uuids };
}

export function readPatient(data) {
  const response = http.get(`${BASE_URL}/api/v1/patients/${pick(data.uuids)}`, {
    headers,
    tags: { name: 'GET /patients/{uuid}' },
  });
  check(response, { 'patient returned': (r) => r.status === 200 });
}

export function searchDocument() {
  const response = searchByDocument(randomSeededDocument());
  check(response, { 'document found': (r) => r.status === 200 && r.json('page.totalElements') === 1 });
}

export function searchName() {
  const response = http.post(`${BASE_URL}/api/v1/patients/search?size=20`, JSON.stringify({ name: pick(NAMES) }), {
    headers,
    tags: { name: 'POST /patients/search (name)' },
  });
  check(response, { 'names searched': (r) => r.status === 200 });
}

export function registerPatient() {
  const number = `${Date.now()}${String(__VU % 100).padStart(2, '0')}`;
  const body = {
    document: { type: 'CEDULA_DE_CIUDADANIA', number },
    demographics: {
      firstNames: 'Paciente',
      lastNames: 'Carga Prueba',
      birthDate: '1988-06-15',
      sex: 'FEMALE',
      countryOfOrigin: 'CO',
      disability: 'NONE',
    },
    contact: { mobile: '3001234567' },
    affiliation: { regime: 'UNINSURED' },
    residence: { department: 'Santander', municipality: 'Bucaramanga', zone: 'URBAN', address: 'Calle 1 # 2-3' },
  };
  const response = http.post(`${BASE_URL}/api/v1/patients`, JSON.stringify(body), {
    headers,
    tags: { name: 'POST /patients' },
  });
  check(response, { 'patient registered': (r) => r.status === 201 });
}

export function updateContact(data) {
  const uuid = pick(data.uuids);
  const current = http.get(`${BASE_URL}/api/v1/patients/${uuid}`, { headers, tags: { name: 'GET /patients/{uuid}' } });
  const mobile = `31${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`;
  const response = http.put(`${BASE_URL}/api/v1/patients/${uuid}/contact`, JSON.stringify({ contact: { mobile } }), {
    headers: { ...headers, 'If-Match': current.headers.Etag },
    tags: { name: 'PUT /patients/{uuid}/contact' },
    responseCallback: http.expectedStatuses(200, 412),
  });
  check(response, { 'contact updated or concurrent edit detected': (r) => r.status === 200 || r.status === 412 });
}

export function handleSummary(data) {
  const format = (value) => `${value.toFixed(1)} ms`.padStart(10);
  const lines = [
    `Multiplier x${MULTIPLIER} over ${DURATION}`,
    `Requests: ${data.metrics.http_reqs.values.count} (${data.metrics.http_reqs.values.rate.toFixed(1)} req/s), failed: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`,
    `${'operation'.padEnd(18)}${'p50'.padStart(10)}${'p95'.padStart(10)}${'p99'.padStart(10)}${'max'.padStart(10)}`,
  ];
  for (const name of Object.keys(PEAK_RATES_PER_SECOND)) {
    const metric = data.metrics[`http_req_duration{scenario:${name}}`];
    if (metric) {
      const v = metric.values;
      lines.push(`${name.padEnd(18)}${format(v.med)}${format(v['p(95)'])}${format(v['p(99)'])}${format(v.max)}`);
    }
  }
  const thresholdsPassed = Object.values(data.metrics).every((metric) =>
    Object.values(metric.thresholds || {}).every((threshold) => threshold.ok));
  lines.push(`Thresholds: ${thresholdsPassed ? 'passed' : 'FAILED'}`);
  const outputs = { stdout: `${lines.join('\n')}\n` };
  if (SUMMARY_FILE) {
    outputs[SUMMARY_FILE] = JSON.stringify(data, null, 2);
  }
  return outputs;
}
