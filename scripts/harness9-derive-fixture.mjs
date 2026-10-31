import { createHash, timingSafeEqual } from 'node:crypto';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';

const [sourceArg, targetArg] = process.argv.slice(2);
if (!sourceArg || !targetArg) {
  throw new Error('Usage: node scripts/harness9-derive-fixture.mjs <original-run-dir> <test-resource-run-dir>');
}

const source = resolve(sourceArg);
const target = resolve(targetArg);
if (source === target || target.startsWith(source + '/')) {
  throw new Error('Derived output must not overwrite the original run');
}

const RUN_ID = 'c44e7e20-5e33-49c4-bc57-105a8cc3c347';
const ORIGINAL_MANIFEST_SHA256 = '7cc4f4c71d7e675eac4f99d47f9d722c34a6ffa3c159bddd808ed73951a61288';
const RULES_VERSION = 'harness9-allowlist-v1';
const EXPECTED_CASES = ['kb-qa-01', 'tool-route-02', 'param-guard-03', 'ctx-history-04', 'comp-rag-tool-05'];
const EXPECTED_CANDIDATES = ['baseline', 'candidate-a', 'candidate-b'];
const SELECTED_REASON = 'BASELINE_RETAINED_AFTER_REJECTED_CANDIDATES';
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const sameHash = (a, b) => /^[0-9a-f]{64}$/.test(a) && /^[0-9a-f]{64}$/.test(b)
  && timingSafeEqual(Buffer.from(a, 'ascii'), Buffer.from(b, 'ascii'));
const encode = value => Buffer.from(JSON.stringify(value, null, 2) + '\n');
const select = (object, fields) => Object.fromEntries(fields.map(field => [field, object[field]]));
const readJson = async path => JSON.parse(await readFile(path, 'utf8'));
const javaDouble = value => Number.isInteger(value) ? `${value}.0` : String(value);

function voucherFingerprint(v) {
  const unit = '\u001f';
  const components = [
    [v.invariantSystemPrompt.roleDefinition, v.invariantSystemPrompt.corePrinciples,
      v.invariantSystemPrompt.safetyInstructions].join(unit),
    [v.guardrails.maxCycleSteps, v.guardrails.loopThreshold, v.guardrails.maxTokenBudget,
      javaDouble(v.guardrails.maxCostCny)].join(unit),
    [v.memoryContext.shortTermWindowSize, v.memoryContext.longTermRecallTopK,
      v.memoryContext.reflexionDepth].join(unit),
    [v.toolGuidance.autoToolSelectionEnabled, v.toolGuidance.hoareVerificationEnabled,
      v.toolGuidance.twoPhaseApprovalEnabled].join(unit),
    [v.subagents.orchestrationMode, v.subagents.workerRoles.join(','),
      v.subagents.isolatedContextEnabled].join(unit),
    [v.skillBank.activeSkillIds.join(','), javaDouble(v.skillBank.cosineGateThreshold)].join(unit),
    [v.sandboxedExecutionPlan.declaredParameterSlots.join(','), false].join(unit),
    [v.mcpMiddlewarePlugins.dataMaskingEnabled, v.mcpMiddlewarePlugins.sagasCompensationEnabled,
      v.mcpMiddlewarePlugins.auditLoggingEnabled].join(unit),
  ];
  return hash(Buffer.from([v.botId, v.version, ...components].join('\u001e'), 'utf8'));
}

function derivedVoucher(v, candidateId) {
  return {
    voucherId: `derived-${candidateId}`,
    botId: v.botId,
    version: v.version,
    createdAt: v.createdAt,
    invariantSystemPrompt: {
      roleDefinition: `Offline replay ${candidateId}`,
      corePrinciples: 'Derived fixture; original prompt redacted.',
      safetyInstructions: 'No production use.',
    },
    guardrails: select(v.guardrails, ['maxCycleSteps', 'loopThreshold', 'maxTokenBudget', 'maxCostCny']),
    memoryContext: select(v.memoryContext, ['shortTermWindowSize', 'longTermRecallTopK', 'reflexionDepth']),
    toolGuidance: select(v.toolGuidance, ['autoToolSelectionEnabled', 'hoareVerificationEnabled',
      'twoPhaseApprovalEnabled']),
    subagents: { orchestrationMode: v.subagents.orchestrationMode, workerRoles: [],
      isolatedContextEnabled: v.subagents.isolatedContextEnabled },
    skillBank: { activeSkillIds: [], cosineGateThreshold: v.skillBank.cosineGateThreshold },
    sandboxedExecutionPlan: { declaredParameterSlots: [], allowArbitraryScripts: false },
    mcpMiddlewarePlugins: select(v.mcpMiddlewarePlugins, ['dataMaskingEnabled',
      'sagasCompensationEnabled', 'auditLoggingEnabled']),
  };
}

function derivedProbe(p, fingerprints) {
  const e = p.evidence;
  return {
    ...select(p, ['candidateId', 'caseId', 'qualityPass', 'executedAt', 'executionMode',
      'effectiveCapabilityTag', 'cycleDetected', 'guardBlocked', 'ragScoreEnabled',
      'ragAttribution', 'meteredCallCount', 'promptTokens', 'completionTokens',
      'reasoningTokens', 'cacheHitTokens', 'totalTokens', 'actualCostCny', 'costKnown',
      'usageStatus', 'durationMs', 'peakPricingPeriod']),
    injectedRagChunkIds: p.injectedRagChunkIds.map((_, i) => `derived-chunk-${i + 1}`),
    evidence: {
      ...select(e, ['botId', 'voucherVersion', 'timestamp',
        'executionMode', 'capabilityTag', 'executionDurationMs', 'guardBlocked',
        'guardBlockedCount', 'ragDocumentCount', 'ragTopScore', 'ragAttribution',
        'usageStatus', 'estimatedTokens', 'estimatedCostCny']),
      evidenceId: `derived-evidence-${p.candidateId}-${p.caseId}`,
      requestId: `derived-request-${p.candidateId}-${p.caseId}`,
      voucherFingerprint: fingerprints.get(p.candidateId).derived,
      userInputSummary: '[REDACTED USER INPUT]',
      finalOutputSummary: '[REDACTED MODEL OUTPUT]',
      toolExecutions: e.toolExecutions.map((t, i) => ({
        ...select(t, ['success', 'guardBlocked', 'durationMs']),
        toolName: `derived-tool-${i + 1}`,
        inputDigest: null,
        outputDigest: null,
        violationReason: null,
      })),
    },
  };
}

const manifestBytes = await readFile(join(source, 'checksums.sha256'));
if (!sameHash(hash(manifestBytes), ORIGINAL_MANIFEST_SHA256)) {
  throw new Error('Original Stage 9 manifest differs from its frozen hash');
}
const originalHashes = new Map();
for (const line of manifestBytes.toString('utf8').trimEnd().split('\n')) {
  const match = /^([0-9a-f]{64})  (probes\/[A-Za-z0-9._-]+\.json|ppc_summary\.json|selected_candidate\.json)$/.exec(line);
  if (!match || originalHashes.has(match[2])) throw new Error('Invalid original manifest entry');
  if (!sameHash(hash(await readFile(join(source, match[2]))), match[1])) {
    throw new Error(`Original checksum mismatch: ${match[2]}`);
  }
  originalHashes.set(match[2], match[1]);
}
if (originalHashes.size !== 17) throw new Error('Original manifest must contain 17 files');

const summary = await readJson(join(source, 'ppc_summary.json'));
const selected = await readJson(join(source, 'selected_candidate.json'));
if (summary.runId !== RUN_ID || summary.selectedCandidateId !== 'baseline'
    || summary.finalVerdict !== 'REJECTED_AFTER_REAL_PPC'
    || selected.selectedCandidateId !== summary.selectedCandidateId
    || summary.candidates.length !== 3 || summary.probes.length !== 15
    || JSON.stringify(selected.voucher) !== JSON.stringify(summary.candidates[0].voucher)) {
  throw new Error('Original Stage 9 selection contract changed');
}
const fingerprints = new Map();
for (const candidate of summary.candidates) {
  if (!EXPECTED_CANDIDATES.includes(candidate.candidateId)
      || fingerprints.has(candidate.candidateId)
      || !sameHash(voucherFingerprint(candidate.voucher), candidate.voucherFingerprint)) {
    throw new Error(`Original voucher fingerprint mismatch: ${candidate.candidateId}; computed=${voucherFingerprint(candidate.voucher)}; frozen=${candidate.voucherFingerprint}`);
  }
  const voucher = derivedVoucher(candidate.voucher, candidate.candidateId);
  fingerprints.set(candidate.candidateId, { original: candidate.voucherFingerprint,
    derived: voucherFingerprint(voucher), voucher });
}

const outputs = new Map();
const probes = [];
const seen = new Set();
for (const p of summary.probes) {
  const file = `probes/${p.candidateId}-${p.caseId}.json`;
  if (!originalHashes.has(file) || !EXPECTED_CASES.includes(p.caseId)
      || !fingerprints.has(p.candidateId) || seen.has(file)) {
    throw new Error('Original probe matrix changed');
  }
  seen.add(file);
  const diskProbe = await readJson(join(source, file));
  if (JSON.stringify(p) !== JSON.stringify(diskProbe)
      || p.evidence.voucherFingerprint !== fingerprints.get(p.candidateId).original) {
    throw new Error(`Original summary/probe mismatch: ${file}`);
  }
  const derived = derivedProbe(p, fingerprints);
  probes.push(derived);
  outputs.set(file, encode(derived));
}
if (seen.size !== 15) throw new Error('Original probe matrix is incomplete');

const derivedSummary = {
  ...select(summary, ['runId', 'mode', 'budget', 'totalProbesExecuted',
    'totalModelCallsExecuted', 'totalTokensConsumed', 'totalCostCnyConsumed',
    'promptOnlyDowngradeCount', 'finalVerdict', 'selectedCandidateId']),
  candidates: summary.candidates.map(c => ({
    ...select(c, ['candidateId', 'operators', 'promptTokens', 'completionTokens',
      'totalTokens', 'totalCostCny', 'allUsageKnown', 'costKnown']),
    voucher: fingerprints.get(c.candidateId).voucher,
    voucherFingerprint: fingerprints.get(c.candidateId).derived,
  })),
  probes,
  checksumsSha256: Object.fromEntries([...outputs].map(([path, bytes]) => [path, hash(bytes)])),
  positionSwapAudits: summary.positionSwapAudits.map(a => select(a,
    ['candidateId', 'caseId', 'outcome', 'pass1Winner', 'pass2Winner', 'positionSwapConsistent'])),
};
outputs.set('ppc_summary.json', encode(derivedSummary));
outputs.set('selected_candidate.json', encode({
  selectedCandidateId: selected.selectedCandidateId,
  voucher: fingerprints.get(selected.selectedCandidateId).voucher,
  reason: SELECTED_REASON,
}));

for (const [path, bytes] of outputs) {
  const destination = join(target, path);
  await mkdir(dirname(destination), { recursive: true });
  await writeFile(destination, bytes);
}
const paths = [...originalHashes.keys()];
const derivedManifest = Buffer.from(paths.map(path => `${hash(outputs.get(path))}  ${path}`).join('\n') + '\n');
await writeFile(join(target, 'test-checksums.sha256'), derivedManifest);
const mapping = {
  rulesVersion: RULES_VERSION,
  runId: RUN_ID,
  originalManifestSha256: ORIGINAL_MANIFEST_SHA256,
  derivedManifestSha256: hash(derivedManifest),
  files: paths.map(path => ({ originalPath: path, originalSha256: originalHashes.get(path),
    derivedPath: path, derivedSha256: hash(outputs.get(path)) })),
  fingerprints: EXPECTED_CANDIDATES.map(candidateId => ({ candidateId,
    original: fingerprints.get(candidateId).original,
    derived: fingerprints.get(candidateId).derived })),
};
await writeFile(join(target, 'provenance-map.json'), encode(mapping));
process.stdout.write(`Derived manifest SHA-256: ${mapping.derivedManifestSha256}\n`);
