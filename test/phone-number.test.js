import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeInternationalPhone } from '../src/phone-number.js';

test('phone normalization requires an explicit international country code', () => {
  assert.equal(normalizeInternationalPhone('+880 1712-345678'), '+8801712345678');
  assert.equal(normalizeInternationalPhone('  +1 (202) 555-0123  '), '+12025550123');
  assert.equal(normalizeInternationalPhone('+12345678'), '+12345678');
  assert.equal(normalizeInternationalPhone('+123456789012345'), '+123456789012345');
});

test('phone normalization rejects ambiguous, unsafe or invalid formats', () => {
  for (const input of [null, 8801712345678, '', '01712345678', '008801712345678', '+012345678', '+1234567', '+1234567890123456', '+8801712345678 ext 123', '+8801712345678\n7', '+880১৭১২৩৪৫৬৭৮', '+8801712345678#', '+'.repeat(100)]) {
    assert.equal(normalizeInternationalPhone(input), null);
  }
});
