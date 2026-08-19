#!/usr/bin/env node
/**
 * Renders `patient.jdl` into `.jhipster/*.json` for both this service and the patient dashboard.
 *
 *   node tools/render-entity-configs.mjs [path-to-dashboard-repo]
 *
 * The dashboard defaults to `../web` (the hc-patient workspace layout). Pass a path if yours
 * differs; if the directory is missing, only this repo is written.
 *
 * Why a script rather than `jhipster jdl`: running the generator rewrites entity code as a side
 * effect, and the two repos disagree on conventions that must NOT be normalised —
 * `clientRootFolder`/`microserviceName` differ per repo, and `entityTableName` names a live
 * MongoDB collection. Everything of that kind is read back from whatever the repo already says;
 * only fields, relationships, pagination, service and documentation come from the JDL.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const API = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WEB = path.resolve(process.argv[2] ?? path.join(API, '..', 'web'));

// The JDL importer ships with the dashboard's generator-jhipster; this repo has no node_modules
// for it. Fall back to a local install if one appears.
const importerFrom = async () => {
  for (const base of [WEB, API]) {
    const candidate = path.join(base, 'node_modules', 'generator-jhipster', 'dist', 'jdl', 'index.js');
    if (fs.existsSync(candidate)) return import(candidate);
  }
  throw new Error('generator-jhipster not found — run `npm install` in the dashboard repo first');
};

const { createImporterFromContent } = await importerFrom();

const entities = createImporterFromContent(fs.readFileSync(path.join(API, 'patient.jdl'), 'utf8'), {
  skipFileGeneration: true,
  skipUserManagement: true,
})
  .import()
  .exportedEntities;

/** Changelog dates for the entities this refactor introduces. Fixed, so reruns are idempotent. */
const NEW_CHANGELOG = {
  Professional: '20260803090000',
  Visitation: '20260803090100',
  Emergency: '20260803090200',
  ActivityLog: '20260803090300',
  CarePlanItem: '20260803090400',
  Allergy: '20260803090500',
  DutyRoster: '20260811150000',
  Shift: '20260811150100',
  CareDelegation: '20260819100000',
};

const readExisting = (repo, name) => {
  const p = path.join(repo, '.jhipster', `${name}.json`);
  return fs.existsSync(p) ? JSON.parse(fs.readFileSync(p, 'utf8')) : null;
};

const dateOf = cfg => cfg?.annotations?.changelogDate ?? cfg?.changelogDate;

/** JDL keeps hard newlines in doc comments as a literal backslash-n, which reads badly once the
 *  generator lifts it into javadoc. Flatten to one line. */
const flatten = doc => doc?.replace(/\\n/g, ' ').replace(/\s+/g, ' ').trim();

/** Alphabetical, so a rerun does not churn the diff against the hand-written files. */
const sortKeys = obj => Object.fromEntries(Object.entries(obj).sort(([a], [b]) => a.localeCompare(b)));

function write(repo, other, entity, defaults) {
  const existing = readExisting(repo, entity.name);
  // ClinicalCase and Recommendation exist only in api today; web must adopt api's date, not mint one.
  const sibling = readExisting(other, entity.name);
  const changelogDate = dateOf(existing) ?? dateOf(sibling) ?? NEW_CHANGELOG[entity.name];
  if (!changelogDate) throw new Error(`no changelogDate for ${entity.name}`);

  const out = {
    annotations: { changelogDate },
    ...(existing && 'changelogDate' in existing ? { changelogDate } : {}),
    clientRootFolder: existing?.clientRootFolder ?? defaults.clientRootFolder,
    databaseType: 'mongodb',
    ...(entity.documentation ? { documentation: flatten(entity.documentation) } : {}),
    dto: entity.dto ?? 'no',
    // Names a live MongoDB collection. Renaming it here would silently orphan every document in it.
    entityTableName: existing?.entityTableName ?? sibling?.entityTableName ?? entity.entityTableName,
    fields: entity.fields.map(f =>
      f.fieldTypeDocumentation ? { ...f, fieldTypeDocumentation: flatten(f.fieldTypeDocumentation) } : f,
    ),
    microserviceName: existing?.microserviceName ?? defaults.microserviceName,
    name: entity.name,
    pagination: entity.pagination ?? 'no',
    readOnly: false,
    relationships: (entity.relationships ?? []).map(sortKeys),
    // web claimed `true` for most entities, which is how its generated services ended up calling
    // `_search` URLs that 404 — neither backend runs a search engine.
    searchEngine: 'no',
    service: entity.service ?? 'no',
  };
  fs.writeFileSync(path.join(repo, '.jhipster', `${entity.name}.json`), `${JSON.stringify(out, null, 2)}\n`);
}

const targets = [{ repo: API, other: WEB, clientRootFolder: 'hcPatientService', microserviceName: 'hcPatientService' }];
if (fs.existsSync(path.join(WEB, '.jhipster'))) {
  targets.push({ repo: WEB, other: API, clientRootFolder: 'patientMS', microserviceName: 'patientMS' });
} else {
  console.warn(`dashboard repo not found at ${WEB} — writing ${path.basename(API)} only`);
}

for (const target of targets) {
  for (const entity of entities) write(target.repo, target.other, entity, target);
  console.log(`${entities.length} entity configs → ${target.repo}/.jhipster`);
}
