import { createHmac } from 'node:crypto';
import { setTimeout as sleep } from 'node:timers/promises';

const [, , otpauthUrl, lastUsedPeriod = '-1'] = process.argv;
const PERIOD_SECONDS = 30;
const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

const base32 = (encoded) => {
  const bytes = [];
  let buffer = 0;
  let bits = 0;
  for (const character of encoded.toUpperCase().replace(/=+$/, '')) {
    buffer = (buffer << 5) | ALPHABET.indexOf(character);
    bits += 5;
    if (bits >= 8) {
      bytes.push((buffer >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
};

const period = () => Math.floor(Date.now() / 1000 / PERIOD_SECONDS);
while (period() <= Number(lastUsedPeriod)) {
  await sleep(1000);
}

const current = period();
const counter = Buffer.alloc(8);
counter.writeBigUInt64BE(BigInt(current));
const hash = createHmac('sha1', base32(new URL(otpauthUrl).searchParams.get('secret'))).update(counter).digest();
const binary = hash.readUInt32BE(hash[hash.length - 1] & 0x0f) & 0x7fffffff;

console.log(`${String(binary % 1_000_000).padStart(6, '0')} ${current}`);
