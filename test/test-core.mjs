// ট্রাফিক রাশ — কোর-লজিক টেস্ট (node test/test-core.mjs)
import pkg from '../js/game.js';
const { LANES, toBn, freeLanes, pickSpawnLane, aabb, speedFor } = pkg;

let pass = 0, fail = 0;
const ok = (c, name) => { if (c) { pass++; console.log('  ✓', name); } else { fail++; console.log('  ✗', name); } };

// বাংলা সংখ্যা
ok(toBn(1234) === '১২৩৪', 'toBn: ১২৩৪');
ok(toBn(0) === '০', 'toBn: ০');
ok(toBn(-7) === '-৭', 'toBn: ঋণাত্মক');

// ফ্রি-লেন
ok(JSON.stringify(freeLanes([false, true, false, true])) === '[0,2]', 'freeLanes: খালি লেন সঠিক');
ok(freeLanes([true, true, true, true]).length === 0, 'freeLanes: সব ব্যস্ত → খালি তালিকা');

// spawn-লেন কখনো ব্যস্ত লেনে যায় না, সব ব্যস্ত হলে -1
let legal = true;
for (let t = 0; t < 500; t++) {
  const occ = [0, 0, 0, 0].map(() => Math.random() < 0.5);
  const l = pickSpawnLane(occ);
  if (l !== -1 && occ[l]) legal = false;
}
ok(legal, 'pickSpawnLane: ৫০০ র‍্যান্ডম ট্রায়ালে কখনো ব্যস্ত লেন বাছেনি');
ok(pickSpawnLane([true, true, true, true]) === -1, 'pickSpawnLane: সব ব্যস্ত → -1 (অসম্ভব স্থিতি নেই)');
let seen = new Set();
for (let t = 0; t < 200; t++) seen.add(pickSpawnLane([false, false, false, false]));
ok(seen.size === LANES, 'pickSpawnLane: ৪ লেনই বাছা হয় (র‍্যান্ডমতা)');

// AABB — margin সহ
ok(aabb(0, 0, 10, 10, 5, 5, 10, 10, 0), 'aabb: ওভারল্যাপ ধরা হয়');
ok(!aabb(0, 0, 10, 10, 20, 0, 10, 10, 0), 'aabb: আলাদা বাক্স না ধরা হয়');
ok(!aabb(4, 0, 10, 10, 12, 0, 10, 10, 0.25), 'aabb: margin-এ প্রায়-স্পর্শ না ধরা হয় (ফেয়ার প্লে)');
ok(aabb(6, 0, 10, 10, 8, 0, 10, 10, 0.25), 'aabb: margin-এ গভীর ওভারল্যাপ ধরা হয়');

// গতি-র‍্যাম্প
ok(speedFor(0) === 240, 'speedFor: শুরু ২৪০');
ok(speedFor(1000) > speedFor(500), 'speedFor: দূরত্বের সাথে বাড়ে');
ok(speedFor(1e9) === 720, 'speedFor: ক্যাপ ৭২০');

console.log(`\nCORE: ${pass} পাস, ${fail} ফেল`);
if (fail > 0) process.exit(1);
