import { createSign, randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

const [, , privateKeyPath, role = 'DOCTOR', subject = randomUUID(), ttlSeconds = '900'] = process.argv;

if (!privateKeyPath) {
  console.error('Usage: node generate-token.mjs <private-key.pem> [ROLE] [SUBJECT_UUID] [TTL_SECONDS]');
  process.exit(1);
}

const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
const now = Math.floor(Date.now() / 1000);
const header = encode({ alg: 'RS256', typ: 'JWT' });
const payload = encode({
  iss: 'ClinicaDeYmid',
  sub: subject,
  jti: randomUUID(),
  iat: now,
  exp: now + Number(ttlSeconds),
  user_id: 1,
  email: `${role.toLowerCase()}@clinica.local`,
  type: 'access',
  role,
});
const signature = createSign('RSA-SHA256').update(`${header}.${payload}`).sign(readFileSync(privateKeyPath), 'base64url');

console.log(`${header}.${payload}.${signature}`);
