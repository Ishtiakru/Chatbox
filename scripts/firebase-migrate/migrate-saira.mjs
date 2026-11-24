#!/usr/bin/env node
/**
 * Chatbox — Saira AI data migration between Firebase projects.
 *
 * Copies Firestore data (by default: the whole `users` collection, which
 * contains users/{uid}/sairaSessions/{sessionId}/exchanges — all Saira AI
 * chat history) from a SOURCE project to a DESTINATION project, preserving
 * document IDs, subcollections and timestamps.
 *
 * Usage:
 *   npm install
 *   node migrate-saira.mjs --source-key ./source.json --dest-key ./dest.json
 *   node migrate-saira.mjs --source-key ./source.json --dest-key ./dest.json --execute
 *
 * By default this is a DRY RUN (counts only, writes nothing).
 * Add --execute to actually copy the data.
 *
 * Options:
 *   --source-key <path>   Service-account JSON of the SOURCE project
 *   --dest-key <path>     Service-account JSON of the DESTINATION project
 *   --source-db <id>      Firestore database ID in source (default: see below)
 *   --dest-db <id>        Firestore database ID in destination (default: same)
 *   --collections <list>  Comma-separated top-level collections (default: users)
 *   --execute             Actually write to destination (default: dry run)
 *   --list-auth           Also print Auth users (uid/email) of both projects
 *
 * NEVER commit the service-account JSON files. They are git-ignored here.
 */

import { initializeApp, cert } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { readFileSync, existsSync } from 'node:fs';

// Chatbox uses a NAMED Firestore database (see
// app/src/main/res/values/firebase_applet_config.xml). The destination
// project must have a database with this exact ID, otherwise the app
// (which hardcodes this ID) will not find the migrated data.
const DEFAULT_DB = 'ai-studio-android-chatbox-f5f16960-82e9-4756-8ee4-5c3fe55bdd70';

function parseArgs(argv) {
  const args = {};
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--execute' || a === '--list-auth') {
      args[a.slice(2)] = true;
    } else if (a.startsWith('--')) {
      args[a.slice(2)] = argv[++i];
    }
  }
  return args;
}

const args = parseArgs(process.argv);

function loadApp(name, keyPath) {
  if (!keyPath || !existsSync(keyPath)) {
    console.error(`\nERROR: service-account file not found: ${keyPath || '(missing arg)'}`);
    console.error('Get it from Firebase Console -> Project settings -> Service accounts');
    console.error('-> Generate new private key. Pass it via --source-key / --dest-key.');
    process.exit(1);
  }
  const serviceAccount = JSON.parse(readFileSync(keyPath, 'utf8'));
  return initializeApp({ credential: cert(serviceAccount) }, name);
}

function getDb(app, dbId) {
  return dbId && dbId !== '(default)' ? getFirestore(app, dbId) : getFirestore(app);
}

let totalDocs = 0;
let totalWrites = 0;

async function copyCollection(sourceCol, destCol, execute, path) {
  const snap = await sourceCol.get();
  if (snap.empty) {
    console.log(`  (empty) ${path}`);
    return;
  }
  console.log(`  ${path} — ${snap.size} doc(s)`);

  let batch = null;
  let batchCount = 0;
  const flush = async () => {
    if (batch && batchCount > 0) {
      if (execute) {
        await batch.commit();
        totalWrites += batchCount;
      }
      batch = null;
      batchCount = 0;
    }
  };

  for (const doc of snap.docs) {
    totalDocs++;
    if (execute) {
      if (!batch) batch = destCol.firestore.batch();
      batch.set(destCol.doc(doc.id), doc.data());
      batchCount++;
      if (batchCount >= 400) await flush();
    }
    // Recurse into subcollections (e.g. sairaSessions, exchanges)
    const subcols = await doc.ref.listCollections();
    for (const sub of subcols) {
      await copyCollection(sub, destCol.doc(doc.id).collection(sub.id), execute, `${path}/${doc.id}/${sub.id}`);
    }
  }
  await flush();
}

async function listAuthUsers(app, label) {
  try {
    const auth = getAuth(app);
    const result = await auth.listUsers(1000);
    console.log(`\nAuth users in ${label}: ${result.users.length}`);
    for (const u of result.users) {
      console.log(`  - uid=${u.uid} email=${u.email || '(no email)'} providers=${u.providerData.map(p => p.providerId).join(',')}`);
    }
    if (result.users.length === 0) {
      console.log('  (none — WARNING: Saira sessions under users/{uid} will be orphaned');
      console.log('   if these UIDs do not exist as Auth users in the destination project)');
    }
  } catch (e) {
    console.log(`\nCould not list Auth users for ${label}: ${e.message}`);
  }
}

async function main() {
  const sourceDbId = args['source-db'] || DEFAULT_DB;
  const destDbId = args['dest-db'] || sourceDbId;
  const collections = (args.collections || 'users').split(',').map(s => s.trim()).filter(Boolean);
  const execute = !!args.execute;

  console.log('Chatbox Saira data migration');
  console.log('============================');
  console.log(`Mode:        ${execute ? 'EXECUTE (will write!)' : 'DRY RUN (no writes)'}`);
  console.log(`Collections: ${collections.join(', ')}`);
  console.log(`Source DB:   ${sourceDbId}`);
  console.log(`Dest DB:     ${destDbId}`);
  if (!execute) {
    console.log('\nRe-run with --execute to actually copy the data.');
  }

  const sourceApp = loadApp('source', args['source-key']);
  const destApp = loadApp('dest', args['dest-key']);

  console.log(`\nSource project: ${sourceApp.options.projectId}`);
  console.log(`Dest project:   ${destApp.options.projectId}`);

  if (args['list-auth']) {
    await listAuthUsers(sourceApp, 'SOURCE');
    await listAuthUsers(destApp, 'DESTINATION');
  }

  const sourceDb = getDb(sourceApp, sourceDbId);
  const destDb = getDb(destApp, destDbId);

  // Sanity check: destination database must exist.
  try {
    await destDb.listCollections();
  } catch (e) {
    console.error(`\nERROR: cannot access destination database "${destDbId}": ${e.message}`);
    console.error('Create it first in Firebase Console -> Firestore Database ->');
    console.error(`Create database with ID: ${destDbId}`);
    process.exit(1);
  }

  for (const col of collections) {
    console.log(`\nCopying top-level collection: ${col}`);
    await copyCollection(sourceDb.collection(col), destDb.collection(col), execute, col);
  }

  console.log('\n============================');
  console.log(`Docs scanned: ${totalDocs}`);
  console.log(execute ? `Docs written: ${totalWrites}` : 'Docs written: 0 (dry run)');
  console.log('\nDone. Verify in Firebase Console (destination project) -> Firestore Database');
  console.log(`-> database "${destDbId}" -> users -> {uid} -> sairaSessions.`);
}

main().catch((e) => {
  console.error('\nMigration failed:', e);
  process.exit(1);
});
