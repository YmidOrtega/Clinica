import { createPublicKey, verify } from 'node:crypto';

const [, , token, jwksJson] = process.argv;
const [header, payload, signature] = token.split('.');
const decode = (part) => JSON.parse(Buffer.from(part, 'base64url').toString('utf8'));
const { kid, alg } = decode(header);
const jwk = JSON.parse(jwksJson).keys.find((key) => key.kid === kid);

if (alg !== 'ES256' || !jwk) {
  console.error(`No ES256 key ${kid} in the JWKS`);
  process.exit(1);
}

const valid = verify('sha256', Buffer.from(`${header}.${payload}`), { key: createPublicKey({ key: jwk, format: 'jwk' }), dsaEncoding: 'ieee-p1363' },
  Buffer.from(signature, 'base64url'));

if (!valid) {
  console.error('Invalid signature');
  process.exit(1);
}

console.log(JSON.stringify({ kid, ...decode(payload) }));
