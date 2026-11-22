// Generate a separate review-only candidate. Never replace or deploy existing rules.
import { readFileSync, writeFileSync } from 'node:fs';
export function sparkSourceRules(spark) {
  const marker = '    // Public display settings can only be changed by the trusted backend.';
  if (spark.split(marker).length !== 2 || spark.includes('match /sparkPrivate/sourceHandover')) throw new Error('Unexpected Spark candidate; review before generating source rules.');
  const rule = `    // Review-only source handover; trusted server provisioning only.
    match /sparkPrivate/sourceHandover {
      allow get: if isSparkAdmin();
      allow list, create, update, delete: if false;
    }

`;
  return spark.replace(marker, rule + marker);
}
if (process.argv[1] && import.meta.url === new URL(process.argv[1], `file://${process.cwd()}/`).href) {
  for (const [input, output] of [['firestore.spark-admin.rules','firestore.spark-source-experimental.rules'], ['firestore.combined-experimental.rules','firestore.combined-source-experimental.rules']]) {
    writeFileSync(output,sparkSourceRules(readFileSync(input,'utf8')));
  }
  console.log('Wrote isolated source handover candidate; no deployment performed.');
}
