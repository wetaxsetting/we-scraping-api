const crypto = require('crypto');
const IV = Buffer.alloc(16, 0);
const TEST_KEY = 'webcashvietnam0002TEST$#%$&*$&^!';
const RQ = '20240422103822';
const JSON_S = '{"bank_cd":"970424","prtn_id":"ORG000001","tar_bank_cd":"970424","tar_finc_mda_div":"A","tar_finc_mda_no":"12345678901111"}';
const c = crypto.createCipheriv('aes-256-cbc', Buffer.from(TEST_KEY,'utf8'), IV);
const ct = Buffer.concat([c.update(Buffer.from(RQ+JSON_S,'utf8')), c.final()]);
console.log('ciphertext b64 len', ct.toString('base64').length);
for (let i = 0; i < 500000; i++) {
  const cand = ('wrongkey' + i).padEnd(32, 'X').slice(0,32);
  try {
    const d = crypto.createDecipheriv('aes-256-cbc', Buffer.from(cand,'utf8'), IV);
    const pt = Buffer.concat([d.update(ct), d.final()]);
    const s = pt.toString('utf8');
    const prefix = s.slice(0,14);
    console.log('FOUND key=', JSON.stringify(cand), 'i=', i);
    console.log('  plaintext len', s.length, 'prefix numeric?', /^\d{14}$/.test(prefix));
    console.log('  prefix bytes', JSON.stringify(prefix));
    break;
  } catch (e) { /* padding fail -> keep searching */ }
}
