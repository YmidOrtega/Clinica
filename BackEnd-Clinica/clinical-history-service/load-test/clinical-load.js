import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8091';
const TOKEN = __ENV.TOKEN;
const MULTIPLIER = Number(__ENV.MULTIPLIER || 1);
const DURATION = __ENV.DURATION || '2m';
const SEEDED_PATIENTS = Number(__ENV.SEEDED_PATIENTS || 50000);
const POOL = Number(__ENV.ENCOUNTER_POOL || 300);
const SUMMARY_FILE = __ENV.SUMMARY_FILE;
const API = `${BASE_URL}/api/v1/clinical`;

const PEAK_RATES_PER_MINUTE = {
  openEncounter: 72,
  writeNote: 300,
  readEncounter: 600,
  readNote: 300,
  readLists: 180,
  readVitalSigns: 180,
  verifyIntegrity: 12,
};

const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };

function scenario(exec, perMinute) {
  const rate = perMinute * MULTIPLIER;
  return {
    executor: 'ramping-arrival-rate',
    exec,
    startRate: 0,
    timeUnit: '1m',
    preAllocatedVUs: Math.max(5, Math.ceil(rate / 30)),
    maxVUs: Math.max(20, Math.ceil(rate / 6)),
    stages: [
      { target: rate, duration: '30s' },
      { target: rate, duration: DURATION },
      { target: 0, duration: '10s' },
    ],
  };
}

export const options = {
  setupTimeout: '10m',
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
  scenarios: Object.fromEntries(Object.entries(PEAK_RATES_PER_MINUTE).map(([name, rate]) => [name, scenario(name, rate)])),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:readEncounter}': ['p(95)<300'],
    'http_req_duration{name:readNote}': ['p(95)<300'],
    'http_req_duration{name:readLists}': ['p(95)<300'],
    'http_req_duration{name:readVitalSigns}': ['p(95)<300'],
    'http_req_duration{name:openEncounter}': ['p(95)<500'],
    'http_req_duration{name:startDraft}': ['p(95)<500'],
    'http_req_duration{name:signNote}': ['p(95)<800'],
    'http_req_duration{name:verifyIntegrity}': ['p(95)<2000'],

  },
};

const patientUuid = (n) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const randomPatient = () => patientUuid(1 + Math.floor(Math.random() * SEEDED_PATIENTS));
const pick = (items) => items[Math.floor(Math.random() * items.length)];

function post(url, body, name, extraHeaders = {}) {
  return http.post(url, body === null ? null : JSON.stringify(body), { headers: { ...headers, ...extraHeaders }, tags: { name } });
}

function progressNote() {
  return {
    content: {
      type: 'PROGRESS',
      subjective: 'Refiere mejoría del dolor, tolera vía oral',
      objective: 'Alerta, hidratada, abdomen blando, sin signos de irritación peritoneal',
      assessment: 'Evolución favorable de hipertensión arterial en control',
      plan: 'Continuar manejo, control de cifras tensionales cada 6 horas',
      diagnoses: [{ code: 'I10X', role: 'PRINCIPAL', type: 'CONFIRMED_REPEATED' }],
    },
    updates: [{
      kind: 'RECORD_VITAL_SIGNS',
      measuredAt: new Date(Date.now() - 60000).toISOString(),
      readings: [
        { kind: 'SYSTOLIC_BLOOD_PRESSURE', value: 120 + Math.floor(Math.random() * 30) },
        { kind: 'DIASTOLIC_BLOOD_PRESSURE', value: 70 + Math.floor(Math.random() * 15) },
        { kind: 'HEART_RATE', value: 60 + Math.floor(Math.random() * 40) },
      ],
    }],
  };
}

function signNote(encounter) {
  const draft = post(`${API}/encounters/${encounter.id}/drafts`, progressNote(), 'startDraft');
  if (!check(draft, { 'draft created': (response) => response.status === 201 })) {
    return null;
  }
  const signed = post(`${API}/drafts/${draft.json('id')}/signature`, null, 'signNote', { 'If-Match': '"0"' });
  check(signed, { 'note signed': (response) => response.status === 201 });
  return signed.status === 201 ? signed.json('id') : null;
}

export function setup() {
  if (!TOKEN) {
    fail('TOKEN is required');
  }
  const encounters = [];
  for (let i = 0; i < POOL; i++) {
    const opened = post(`${API}/encounters`, { patientUuid: patientUuid(1 + i), type: 'INPATIENT' }, 'setupEncounter');
    if (opened.status !== 201) {
      fail(`Could not open setup encounter: ${opened.status} ${opened.body}`);
    }
    const encounter = { id: opened.json('id'), patientUuid: patientUuid(1 + i), notes: [] };
    for (let n = 0; n < 2; n++) {
      const note = signNote(encounter);
      if (note) {
        encounter.notes.push(note);
      }
    }
    encounters.push(encounter);
  }
  return { encounters };
}

export function openEncounter() {
  const response = post(`${API}/encounters`, { patientUuid: randomPatient(), type: pick(['OUTPATIENT', 'EMERGENCY']) }, 'openEncounter');
  check(response, { 'encounter opened': (r) => r.status === 201 });
}

export function writeNote(data) {
  signNote(pick(data.encounters));
}

export function readEncounter(data) {
  const response = http.get(`${API}/encounters/${pick(data.encounters).id}`, { headers, tags: { name: 'readEncounter' } });
  check(response, { 'encounter read': (r) => r.status === 200 });
}

export function readNote(data) {
  const encounter = pick(data.encounters);
  const response = http.get(`${API}/notes/${pick(encounter.notes)}`, { headers, tags: { name: 'readNote' } });
  check(response, { 'note read': (r) => r.status === 200 });
}

export function readLists(data) {
  const response = http.get(`${API}/patients/${pick(data.encounters).patientUuid}/lists`, { headers, tags: { name: 'readLists' } });
  check(response, { 'lists read': (r) => r.status === 200 });
}

export function readVitalSigns(data) {
  const response = http.get(`${API}/patients/${pick(data.encounters).patientUuid}/vital-signs`, { headers, tags: { name: 'readVitalSigns' } });
  check(response, { 'vital signs read': (r) => r.status === 200 });
}

export function verifyIntegrity(data) {
  const response = http.get(`${API}/patients/${pick(data.encounters).patientUuid}/integrity`, { headers, tags: { name: 'verifyIntegrity' } });
  check(response, { 'integrity verified': (r) => r.status === 200 && r.json('verified') === true });
}

export function handleSummary(data) {
  const rows = ['openEncounter', 'startDraft', 'signNote', 'readEncounter', 'readNote', 'readLists', 'readVitalSigns', 'verifyIntegrity']
    .map((name) => {
      const metric = data.metrics[`http_req_duration{name:${name}}`];
      if (!metric) {
        return `${name}: sin datos`;
      }
      const v = metric.values;
      return `${name}: p50 ${v.med.toFixed(1)} ms · p95 ${v['p(95)'].toFixed(1)} ms · p99 ${(v['p(99)'] || 0).toFixed(1)} ms`;
    });
  const requests = data.metrics.http_reqs.values;
  const failed = data.metrics.http_req_failed.values.rate * 100;
  const text = [`MULTIPLIER=${MULTIPLIER}`, ...rows, `requests: ${requests.count} (${requests.rate.toFixed(1)} req/s), failed: ${failed.toFixed(2)} %`].join('\n');
  const output = { stdout: `${text}\n` };
  if (SUMMARY_FILE) {
    output[SUMMARY_FILE] = JSON.stringify(data, null, 2);
  }
  return output;
}
