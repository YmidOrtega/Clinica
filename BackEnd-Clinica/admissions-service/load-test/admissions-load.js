import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8099';
const TOKEN = __ENV.TOKEN;
const LOCATION_UUID = __ENV.LOCATION_UUID;
const CONFIGURED_SERVICE_UUID = __ENV.CONFIGURED_SERVICE_UUID;
const EPISODE_UUID = __ENV.EPISODE_UUID;
const MULTIPLIER = Number(__ENV.MULTIPLIER || 1);
const DURATION = __ENV.DURATION || '3m';
const SUMMARY_FILE = __ENV.SUMMARY_FILE;

const PEAK_RATES_PER_SECOND = {
  readEpisode: 20,
  readCensus: 10,
  readQueue: 10,
  searchEpisodes: 8,
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
    readEpisode: scenario('readEpisode', PEAK_RATES_PER_SECOND.readEpisode),
    readCensus: scenario('readCensus', PEAK_RATES_PER_SECOND.readCensus),
    readQueue: scenario('readQueue', PEAK_RATES_PER_SECOND.readQueue),
    searchEpisodes: scenario('searchEpisodes', PEAK_RATES_PER_SECOND.searchEpisodes),
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{scenario:readEpisode}': ['p(95)<200'],
    'http_req_duration{scenario:readCensus}': ['p(95)<300'],
    'http_req_duration{scenario:readQueue}': ['p(95)<300'],
    'http_req_duration{scenario:searchEpisodes}': ['p(95)<400'],
  },
};

export function setup() {
  if (!TOKEN) {
    fail('Define TOKEN con un token de recepción, enfermería o administración');
  }
  if (!LOCATION_UUID || !CONFIGURED_SERVICE_UUID || !EPISODE_UUID) {
    fail('Define LOCATION_UUID, CONFIGURED_SERVICE_UUID y EPISODE_UUID con datos ya cargados (ver README)');
  }
}

export function readEpisode() {
  const response = http.get(`${BASE_URL}/api/v1/admissions/episodes/${EPISODE_UUID}`, { headers });
  check(response, { 'episode 200': (r) => r.status === 200 });
}

export function readCensus() {
  const response = http.get(`${BASE_URL}/api/v1/admissions/locations/${LOCATION_UUID}/census`, { headers });
  check(response, { 'census 200': (r) => r.status === 200 });
}

export function readQueue() {
  const response = http.get(`${BASE_URL}/api/v1/admissions/configured-services/${CONFIGURED_SERVICE_UUID}/queue`,
    { headers });
  check(response, { 'queue 200': (r) => r.status === 200 });
}

export function searchEpisodes() {
  const response = http.post(`${BASE_URL}/api/v1/admissions/episodes/search`,
    JSON.stringify({ status: 'ACTIVE', configurationServiceUuid: CONFIGURED_SERVICE_UUID }), { headers });
  check(response, { 'search 200': (r) => r.status === 200 });
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
