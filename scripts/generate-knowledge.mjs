import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
const pkg = JSON.parse(readFileSync('package.json', 'utf8'));
const content = readFileSync('docs/saira-knowledge.md', 'utf8').trim();
if (!content || content.length > 12000) throw new Error('Saira knowledge must be between 1 and 12000 characters.');
const knowledge = { version: pkg.version, sha256: createHash('sha256').update(content).digest('hex'), content };
writeFileSync('functions/knowledge.generated.json', JSON.stringify(knowledge, null, 2) + '\n');
