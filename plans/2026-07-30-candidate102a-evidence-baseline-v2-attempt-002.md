# Candidate 10.2A Evidence Baseline V2 Attempt-002 Execution Plan Revision 12

## A. Current State and Failure Mechanism

The predecessor evidence remains `SUPERSEDED_UNRECOVERABLE`. Attempt-001 is also permanently consumed, but for a different reason: compile passed and the Contracts test itself passed `1/0/0/0`; its wrapper then rejected the real Surefire classname because Surefire appends the report-name suffix in parentheses.

Frozen state before this plan:

```text
branch=codex/candidate102a-evidence-baseline-v2
HEAD=main=origin/main=5c84bb044352a3fef684a37f70cc08ac80058c7b
testSourceSha256=ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3
colbertScorerSha256=bf8d340ef6e591a8471e374d03706fbe82bfb58c4d36cc7e58d15c5580569c6e
attempt001BeforeSnapshotSha256=94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e
attempt001ContractsXmlSha256=25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237
attempt001ContractsTxtSha256=4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b
attempt001RuntimeRoot=ABSENT
attempt001FreezeReport=ABSENT
attempt001SelectionReport=ABSENT
selectionQrelAccess=0
holdoutAccess=0
index=EMPTY
```

The actual testcase classname is exactly:

```text
tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test(candidate102a-evidence-baseline-v2-contracts-attempt-001)
```

The unique hypothesis is that migrating the harness to create-only attempt-002 and validating the suffixed Surefire classname closes the wrapper defect without changing any ranking, fixture, qrel, threshold, topK, algorithm, or production behavior.

This plan is stored at `plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md` as requested. It is not a reactor input, so the harness must exclude this exact single path from the worktree snapshot and mechanically prove that the six pre-existing user dirty entries remain unchanged.

The execution threat model is one trusted local Codex process and accidental
concurrent worktree activity. A hostile same-UID process is out of scope.
Shell-owned persistent archives and phase locks use atomic leaf reservation,
private mode-`0700` directories, `umask 077`, the identity checks explicitly
shown in their wrappers, and independent producer/traversal status checks.
Java-owned runtime files retain their existing `NOFOLLOW_LINKS`, lexical-root,
regular-file, source-lock, and readback checks; this plan does not claim an
unimplemented device/inode guarantee for every Java runtime leaf. Pathname
preflight alone is not claimed to defeat an adversarial same-UID rename race.

## B. Research Ledger

```text
N/A
```

This is runtime evidence closure, not algorithm research.

## C. Allowed and Forbidden Boundaries

Allowed writes:

```text
/Users/achilles/.codex/evidence-worktree-snapshots/
  candidate102a-evidence-baseline-v2-attempt-002-before.json

backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/
  RagCandidate102AEvidenceBaselineV2Test.java

backend/tests/evidence/candidate102a-evidence-baseline-v2/
  attempt-001-failed-contracts/**
  attempt-002/**
  .attempt-002.contracts-started/
  .attempt-002.freeze-started/
  .attempt-002.selection-started/

backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2/attempt-002/**
backend/tests/target/surefire-reports/*candidate102a-evidence-baseline-v2*attempt-002*

backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2/
  .attempt-002-selection-authorization.json
  .attempt-002-selection-authorizations-consumed/**

.git/index.lock and .git/index.lock.lock (transient exclusive staging lock)
.git/index (atomic installation of the verified post-commit index)
```

The two authorization paths above are private invocation-local control files.
They are permitted only in the later turn that carries a new explicit
attempt-002 Selection authorization; they are never staged, archived, or
included in the 25-path commit whitelist. Their lifecycle is create-new
mode-`0600` authorization, then a mode-`0400` nonce-bound consumed receipt and
an exactly one-file mode-`0400` Selection lock copy.

Attempt-001 XML/TXT and its external before snapshot are read-only inputs. The six pre-existing user dirty paths must not be deleted, reverted, rewritten, staged, or committed.

Forbidden:

```text
ColbertScorer.java
RagRerankService.java
POM files
fixtures or qrels
thresholds or topK
production configuration
existing tests
old Candidate 10 Freeze/Diagnostic/Recovery/Attribution/Depth/Lifecycle selectors
Holdout paths or raw bytes
Selection replay
push, tag, fresh clone, receipt, A/B, promotion, tuning
```

## D. Options and Decision

| Option | Decision | Reason |
|---|---|---|
| Replay attempt-001 | Reject | Its Contracts report exists; the attempt is consumed. |
| Reuse attempt-001 reports for attempt-002 | Reject | Breaks attempt identity and create-new evidence. |
| Patch only the operator XPath | Reject | The generated source-locked wrapper would remain wrong. |
| Migrate all complete attempt tokens and fix all three generated wrapper predicates | Adopt | Smallest root-cause correction. |
| Store the plan in the repository and count it as a reactor input | Reject | It is documentation, not a compiled reactor input. |
| Store the plan in the repository and exclude its exact path | Adopt | Satisfies durable plan placement without weakening the six-entry source/worktree binding. |
| Preserve attempt-001 failure provenance and run attempt-002 once | Adopt | Retains failure history while keeping the baseline attempt independent. |

## E. Corrective Implementation

### E1. Preserve attempt-001 failure provenance first

Reserve the final archive path with one create-new `mkdir` and populate only
that newly created regular directory. A pre-existing file, directory, or
symlink at the final path fails before any copy; a partial archive is never
removed or reused and stops the gate:

```text
backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts/
```

The archive contains exactly six regular non-symlink files:

```text
SHA256SUMS
failure.json
reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.xml
reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.txt
source/RagCandidate102AEvidenceBaselineV2Test.java
worktree/candidate102a-evidence-baseline-v2-attempt-001-before.json
```

`failure.json` is canonical JSON with a trailing LF and this exact key/value contract:

```json
{
  "attempt": "001",
  "eligibleAsBaseline": false,
  "contractsReport": {
    "errors": 0,
    "failures": 0,
    "skipped": 0,
    "testcaseClassname": "tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test(candidate102a-evidence-baseline-v2-contracts-attempt-001)",
    "testcaseName": "baselineContracts",
    "tests": 1,
    "txtSha256": "4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b",
    "xmlSha256": "25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237"
  },
  "evidenceRole": "FAILED_ATTEMPT_PROVENANCE_ONLY",
  "failureStage": "CONTRACTS_REPORT_POSTFLIGHT",
  "freezeStarted": false,
  "holdoutPathOperationCount": 0,
  "namespace": "candidate102a-evidence-baseline-v2",
  "qrelResourceAccessCount": 0,
  "rootCause": "SUREFIRE_REPORT_SUFFIXED_TESTCASE_CLASSNAME_NOT_ACCEPTED",
  "schemaVersion": "candidate102a-evidence-baseline-v2-failed-attempt-v1",
  "selectionStarted": false,
  "status": "FAILED",
  "testSourceSha256": "ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3",
  "worktreeBeforeSha256": "94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e"
}
```

`SHA256SUMS` covers the other five files, uses `./` POSIX paths sorted under `LC_ALL=C`, and does not cover itself. Postflight requires `regular files=6`, `symlinks=0`, checksum success, old report hashes unchanged, and no credential value other than the known empty `hermes.rag.colbert.embedding-api-key` property in the XML. The archive is private mode `0700`; regular files are created under `umask 077` and remain mode `0600` (Git records them as `100644`). No post-create leaf `chmod` is permitted.

The canonical `failure.json` bytes are the recursively key-sorted compact JSON form of the object above plus exactly one LF:

```text
sizeBytes=1035
sha256=740e7da82e26658ff74d163fddeb910633c355768c2a5d5733e519bae8aa2ae1
```

Before source modification, both each original and its create-new archive copy must be regular non-symlink, have the fixed SHA below, and pass `cmp -s`:

```text
test source=ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3
before snapshot=94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e
Contracts XML=25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237
Contracts TXT=4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b
```

Postflight parses `failure.json` and requires its `testSourceSha256`, `worktreeBeforeSha256`, `contractsReport.xmlSha256`, and `contractsReport.txtSha256` to equal the corresponding archived-copy SHA. It also requires `eligibleAsBaseline=false`, `selectionStarted=false`, `freezeStarted=false`, `qrelResourceAccessCount=0`, and `holdoutPathOperationCount=0`. Report credential validation is deliberately bounded: it requires the sole sensitive-name system property to be the known empty ColBERT API-key property and rejects the fixed PEM/Bearer/AWS/GitHub token patterns. It does not claim detection of arbitrary secrets stored under unrelated property names.

Create and validate the provenance archive with this exact wrapper before changing the test source:

```sh
/bin/zsh -eu <<'ZSH'
set -o pipefail
umask 077
repo='/Users/achilles/Documents/许子祺/Agent'
tests="$repo/backend/tests"
source_test="$tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java"
source_xml="$tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.xml"
source_txt="$tests/target/surefire-reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.txt"
source_snapshot='/Users/achilles/.codex/evidence-worktree-snapshots/candidate102a-evidence-baseline-v2-attempt-001-before.json'
evidence="$tests/evidence"
parent="$evidence/candidate102a-evidence-baseline-v2"
archive="$parent/attempt-001-failed-contracts"
tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-provenance.XXXXXX)
cleanup_provenance() {
  cleanup_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$tmp/tracked"
  /bin/rmdir "$tmp"
  return "$cleanup_status"
}
trap cleanup_provenance EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

ensure_directory() {
  directory=$1
  if test -e "$directory" || test -L "$directory"; then
    test -d "$directory"; test ! -L "$directory"
  else
    /bin/mkdir -m 0755 "$directory"
    test -d "$directory"; test ! -L "$directory"
    test "$(/usr/bin/stat -f '%Lp' "$directory")" = '755'
  fi
}

copy_create() {
  source=$1
  destination=$2
  test -f "$source"; test ! -L "$source"
  test ! -e "$destination"; test ! -L "$destination"
  ensure_directory "${destination:h}"
  (set -C; /bin/cat "$source" > "$destination")
  test -f "$destination"; test ! -L "$destination"
  test "$(/usr/bin/stat -f '%Lp' "$destination")" = '600'
  /usr/bin/cmp -s "$source" "$destination"
}

require_sha() {
  file_path=$1
  expected=$2
  test -f "$file_path"; test ! -L "$file_path"
  digest=$(/usr/bin/shasum -a 256 "$file_path")
  digest=${digest%% *}
  test "$digest" = "$expected"
}

xpath_value() {
  expression=$1
  xml_file=$2
  /usr/bin/xmllint --xpath "$expression" "$xml_file"
}

require_no_credentials() {
  xml=$1
  credential_xpath='count(//*[local-name()="property" and (contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api-key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api_key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"apikey") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"secret") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"password") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"credential"))])'
  allowed_xpath='count(//*[local-name()="property" and @name="hermes.rag.colbert.embedding-api-key" and @value=""])'
  credential_count=$(xpath_value "$credential_xpath" "$xml")
  allowed_count=$(xpath_value "$allowed_xpath" "$xml")
  test "$credential_count" = '1'
  test "$allowed_count" = '1'
  if LC_ALL=C /usr/bin/grep -E -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|Bearer[[:space:]]+[A-Za-z0-9._~+/=-]{8,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}' "$xml" >/dev/null; then
    credential_scan_status=0
  else
    credential_scan_status=$?
  fi
  test "$credential_scan_status" -eq 1
}

test -d "$repo"; test ! -L "$repo"
test -d "$repo/backend"; test ! -L "$repo/backend"
test -d "$tests"; test ! -L "$tests"
require_sha "$source_test" 'ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3'
require_sha "$source_snapshot" '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
require_sha "$source_xml" '25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237'
require_sha "$source_txt" '4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b'
require_no_credentials "$source_xml"
suite='(/*[local-name()="testsuite"] | /*[local-name()="testsuites"]/*[local-name()="testsuite"])'
suite_count=$(xpath_value "count($suite)" "$source_xml")
tests_count=$(xpath_value "string($suite/@tests)" "$source_xml")
failure_count=$(xpath_value "string($suite/@failures)" "$source_xml")
error_count=$(xpath_value "string($suite/@errors)" "$source_xml")
skipped_count=$(xpath_value "string($suite/@skipped)" "$source_xml")
testcase_count=$(xpath_value "count($suite/*[local-name()='testcase' and @name='baselineContracts' and @classname='tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test(candidate102a-evidence-baseline-v2-contracts-attempt-001)'])" "$source_xml")
all_testcases=$(xpath_value 'count(//*[local-name()="testcase"])' "$source_xml")
test "$suite_count" = '1'
test "$tests_count" = '1'
test "$failure_count" = '0'
test "$error_count" = '0'
test "$skipped_count" = '0'
test "$testcase_count" = '1'
test "$all_testcases" = '1'
attempt001="$tests/target/rag-eval/candidate102a-evidence-baseline-v2/attempt-001"
freeze_xml="$tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-001.xml"
freeze_txt="$tests/target/surefire-reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-001.txt"
selection_xml="$tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-001.xml"
selection_txt="$tests/target/surefire-reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-001.txt"
for absent_path in "$attempt001" "$freeze_xml" "$freeze_txt" "$selection_xml" "$selection_txt"; do
  test ! -e "$absent_path"; test ! -L "$absent_path"
done
test ! -e "$archive"; test ! -L "$archive"
/usr/bin/git -C "$repo" diff --cached --quiet --

ensure_directory "$evidence"
ensure_directory "$parent"
if /usr/bin/git -C "$repo" ls-files -z -- \
  'backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts' \
  > "$tmp/tracked"
then
  ls_files_status=0
else
  ls_files_status=$?
fi
test "$ls_files_status" -eq 0
test ! -s "$tmp/tracked"
if /usr/bin/git -C "$repo" check-ignore --no-index -- \
  'backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts/SHA256SUMS' \
  'backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts/failure.json' \
  'backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.xml' \
  'backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.txt' \
  'backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts/source/RagCandidate102AEvidenceBaselineV2Test.java' \
  'backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts/worktree/candidate102a-evidence-baseline-v2-attempt-001-before.json' >/dev/null
then
  exit 1
else
  test "$?" -eq 1
fi

/bin/mkdir -m 0700 "$archive"
test -d "$archive"; test ! -L "$archive"
test "$(/usr/bin/stat -f '%Lp' "$archive")" = '700'
archive_identity=$(/usr/bin/stat -f '%d:%i' "$archive")
copy_create "$source_test" "$archive/source/RagCandidate102AEvidenceBaselineV2Test.java"
copy_create "$source_snapshot" "$archive/worktree/candidate102a-evidence-baseline-v2-attempt-001-before.json"
copy_create "$source_xml" "$archive/reports/${source_xml:t}"
copy_create "$source_txt" "$archive/reports/${source_txt:t}"

failure='{"attempt":"001","contractsReport":{"errors":0,"failures":0,"skipped":0,"testcaseClassname":"tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test(candidate102a-evidence-baseline-v2-contracts-attempt-001)","testcaseName":"baselineContracts","tests":1,"txtSha256":"4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b","xmlSha256":"25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237"},"eligibleAsBaseline":false,"evidenceRole":"FAILED_ATTEMPT_PROVENANCE_ONLY","failureStage":"CONTRACTS_REPORT_POSTFLIGHT","freezeStarted":false,"holdoutPathOperationCount":0,"namespace":"candidate102a-evidence-baseline-v2","qrelResourceAccessCount":0,"rootCause":"SUREFIRE_REPORT_SUFFIXED_TESTCASE_CLASSNAME_NOT_ACCEPTED","schemaVersion":"candidate102a-evidence-baseline-v2-failed-attempt-v1","selectionStarted":false,"status":"FAILED","testSourceSha256":"ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3","worktreeBeforeSha256":"94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e"}'
(set -C; printf '%s\n' "$failure" > "$archive/failure.json")
test "$(/usr/bin/stat -f '%Lp' "$archive/failure.json")" = '600'
require_sha "$archive/failure.json" '740e7da82e26658ff74d163fddeb910633c355768c2a5d5733e519bae8aa2ae1'
test "$(/usr/bin/stat -f '%z' "$archive/failure.json")" = '1035'

require_sha "$archive/source/RagCandidate102AEvidenceBaselineV2Test.java" 'ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3'
require_sha "$archive/worktree/candidate102a-evidence-baseline-v2-attempt-001-before.json" '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
require_sha "$archive/reports/${source_xml:t}" '25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237'
require_sha "$archive/reports/${source_txt:t}" '4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b'
require_no_credentials "$archive/reports/${source_xml:t}"
test "$(/usr/bin/jq -er '.testSourceSha256' "$archive/failure.json")" = 'ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3'
test "$(/usr/bin/jq -er '.worktreeBeforeSha256' "$archive/failure.json")" = '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
test "$(/usr/bin/jq -er '.contractsReport.xmlSha256' "$archive/failure.json")" = '25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237'
test "$(/usr/bin/jq -er '.contractsReport.txtSha256' "$archive/failure.json")" = '4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b'
test "$(/usr/bin/jq -er '.eligibleAsBaseline == false and .selectionStarted == false and .freezeStarted == false and .qrelResourceAccessCount == 0 and .holdoutPathOperationCount == 0' "$archive/failure.json")" = 'true'

(
  cd "$archive"
  set -C
  for checksum_path in \
    './failure.json' \
    "./reports/${source_xml:t}" \
    "./reports/${source_txt:t}" \
    './source/RagCandidate102AEvidenceBaselineV2Test.java' \
    './worktree/candidate102a-evidence-baseline-v2-attempt-001-before.json'
  do
    /usr/bin/shasum -a 256 "$checksum_path"
  done > SHA256SUMS
)
test "$(/usr/bin/stat -f '%Lp' "$archive/SHA256SUMS")" = '600'
tree=$(/usr/bin/mktemp /tmp/candidate102a-attempt001-tree.XXXXXX)
cleanup_attempt001() {
  cleanup_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$tree" "$tree.files" "$tree.symlinks" "$tree.special" "$tree.badmode"
  return "$cleanup_status"
}
trap cleanup_attempt001 EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
/usr/bin/find "$archive" -type f -print > "$tree.files"
/usr/bin/find "$archive" -type l -print > "$tree.symlinks"
/usr/bin/find "$archive" ! -type d ! -type f ! -type l -print > "$tree.special"
/usr/bin/find "$archive" -type f ! -perm 0600 -print > "$tree.badmode"
test "$(/usr/bin/stat -f '%d:%i' "$archive")" = "$archive_identity"
test "$(/usr/bin/awk 'END{print NR+0}' "$archive/SHA256SUMS")" = '5'
test "$(/usr/bin/stat -f '%z' "$archive/SHA256SUMS")" = '751'
require_sha "$archive/SHA256SUMS" '23776a38b6cf76eca90c57ea9a53798d7eb44baab643d1c65a60ce00426adc41'
(cd "$archive"; /usr/bin/shasum -a 256 -c SHA256SUMS)
file_count=$(/usr/bin/awk 'END{print NR+0}' "$tree.files")
test "$file_count" = '6'
test ! -s "$tree.symlinks"; test ! -s "$tree.special"; test ! -s "$tree.badmode"
require_sha "$source_test" 'ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3'
require_sha "$source_snapshot" '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
require_sha "$source_xml" '25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237'
require_sha "$source_txt" '4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b'
/usr/bin/cmp -s "$source_test" "$archive/source/RagCandidate102AEvidenceBaselineV2Test.java"
/usr/bin/cmp -s "$source_snapshot" "$archive/worktree/candidate102a-evidence-baseline-v2-attempt-001-before.json"
/usr/bin/cmp -s "$source_xml" "$archive/reports/${source_xml:t}"
/usr/bin/cmp -s "$source_txt" "$archive/reports/${source_txt:t}"
ZSH
```

For both the original XML and archived XML, `require_no_credentials` captures each XPath result independently and requires exactly one sensitive property and exactly one allowed empty property, both naming `hermes.rag.colbert.embedding-api-key`. Its non-pipelined `grep -E` checks PEM private-key headers, Bearer tokens, `AKIA[0-9A-Z]{16}`, and GitHub token prefixes; only exit status `1` is accepted. A partial final archive is never removed or reused. Any publish or postflight failure leaves the observed bytes in place and stops before E2.

### E2. Migrate the live harness to attempt-002

Only the new test source may change. Apply these mechanical changes:

1. Change `ATTEMPT` from `001` to `002`.
2. Replace exactly 54 active-attempt `attempt-001` tokens with `attempt-002`; never replace bare `001`, preserving numeric fixtures such as `10_160_001L`. Any failed-provenance path added to generated shell bytes must construct the old token from `attempt-` and `001` pieces so no complete old active-attempt token is reintroduced.
3. Fix exactly three testcase XPath predicates from `@classname='$test_class'` to `@classname='$test_class($suffix)'`.
4. Add `requireSameWorktree(before, captureWorktreeSnapshot("BEFORE"))` to Contracts-time persistence validation.
5. Attach the live `AccessCounter` to `AttemptState` before `loadRankingInput`, increment each non-qrel and qrel counter immediately before its V2-owned boundary invocation, set `qrelResourceAccessBeforeRanking` from the live qrel counter at the ranking-freeze boundary, and snapshot the live counter into `state.access` in `finally`. This makes early INVALID markers report attempted access rather than default zeroes.
   Add the tracked fixture generator as a fifth `sourceFiles` binding:

   ```text
   tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate10FixtureGenerator.java
   sha256=8262d8bf5ad65330e0052119bea910cdd4af81e45a450b8238bc499cfd307a6e
   ```

   Its raw bytes remain recoverable from the pinned HEAD, unlike the two dirty
   reactor inputs. Contracts lock the `qrels(Split)` implementation as the
   in-memory generator boundary and require that the V2 source has one direct
   `qrels(Split.SELECTION)` call, zero `Split.HOLDOUT`, zero reflection or
   legacy evidence-loader calls, and no `Path`/`Files`/resource-stream call in
   `loadQrelsAfterRanking`.
6. Add per-case validation for `method`, `mode`, `suffix`, suffixed classname, and marker count. Keep one shared helper used by exactly the three command cases.
7. Derive migration sentinels without embedding a complete old token:

```java
String oldAttemptToken = "attempt-" + "001";
String newAttemptToken = "attempt-" + ATTEMPT;
```

8. Enforce these static postconditions:

```text
ATTEMPT == "002"
live source old complete token count=0
live source new complete token count=54
commandsBytes old complete token count=0
commandsBytes new complete token count=39
ATTEMPT_ROOT, before snapshot source/file and after snapshot file bind attempt-002
preflight_archive_paths definition=1
preflight_archive_paths absent call=1
preflight_archive_paths reserved call=1
Selection absent-call < report preflight < consumption-lock mkdir < Maven
F5 consumed-receipt validations=2 and both precede consumption-lock mkdir
generated Selection requires exact true/002/true capability values and never
  reads the original authorization pathname
generated selection-preflight command=1 and performs no writes
generated Selection requires a mode-0400 consumed receipt whose canonical nonce
  equals the basename under .attempt-002-selection-authorizations-consumed/
F5 Selection drift gate invocations=3: before original authorization read,
  after consumed-receipt readback, and inside generated Selection immediately
  before Selection lock mkdir
generated Selection lock contains exactly authorization.json and is never empty
archive_once begins with reserved-call
generated commandsBytes plain mv count=0; F7 may use one private index-install
rename only after the ref transaction
archive/failure mkdir -p count=0
each command case has exactly one method/mode/suffix/classname/marker binding
generated Selection preflight requires Contracts and Freeze reports.sha256
generated archive validates source and copied Contracts/Freeze/Selection reports
all six successful reports have nested-output marker-line/TXT/credential contract
generated archive phase-lock rmdir count=0
source-locked qrels(...) call count=1
source-locked Split.HOLDOUT token count=0
fixture generator source binding count=1 and SHA=8262d8bf5ad65330e0052119bea910cdd4af81e45a450b8238bc499cfd307a6e
loadQrelsAfterRanking reflection/Path/Files/resource-stream/legacy-loader call count=0
backend/tests/src/test/resources broad snapshot exclusion count=0
live AccessCounter attached before loadRankingInput
non-qrel/qrel increments precede the corresponding boundary invocation
state.access refreshed from the live counter in finally
VALID marker router binds source-lock SHA, 13 checkpoint keys, 200 Futures,
executor idle/terminated, and access counts 3/0/1/0
VALID/INVALID marker routers require exact top-level key sets and fixed
predecessorStatus/causalScope/algorithmConclusion values
executor router binds corePoolSize=4, maxPoolSize=4, queueCapacity=32 and
queueRemainingCapacityBeforeQrelFreeze=queueCapacity=32
report validators compare Selection system-out bytes without shell command
substitution and require the TXT summary as one exact line
```

9. Preserve and check executable mode:

```text
Freeze postflight: runtime commands.sh is regular non-symlink and executable
Selection preflight: runtime commands.sh is regular non-symlink and executable
archive final: create the copy under the private archive and set mode 0700
exactly once before publication checks; then require regular non-symlink and executable
archive final: commands.sh is regular non-symlink and executable
Git index: commands.sh mode=100755, stage=0
```

The explicit final-archive mode operation is:

```sh
/bin/chmod 0700 "$archive/commands.sh"
test -f "$archive/commands.sh"; test ! -L "$archive/commands.sh"; test -x "$archive/commands.sh"
```

10. Add exact plan-source path and SHA constants plus one corresponding `git status` exclusion. Build the active attempt fragment from pieces so the source token count remains exact:

```java
private static final String PLAN_SOURCE =
        "plans/2026-07-30-candidate102a-evidence-baseline-v2-"
                + "attempt-" + ATTEMPT + ".md";
private static final String PLAN_SOURCE_SHA256 =
        FINAL_REVIEWED_PLAN_SHA256;

// captureWorktreeSnapshot command argument
":(exclude)" + PLAN_SOURCE
```

`FINAL_REVIEWED_PLAN_SHA256` is a handoff value, not a literal self-reference in
this Markdown file. After the final exact-SHA review reaches `P0=0/P1=0`, and
before the first Java edit, derive it exactly once with:

```sh
/bin/sh -eu <<'SH'
plan='/Users/achilles/Documents/许子祺/Agent/plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md'
test -f "$plan"; test ! -L "$plan"
plan_digest=$(/usr/bin/shasum -a 256 "$plan")
FINAL_REVIEWED_PLAN_SHA256=${plan_digest%% *}
test "${#FINAL_REVIEWED_PLAN_SHA256}" -eq 64
case "$FINAL_REVIEWED_PLAN_SHA256" in
  *[!0-9a-f]*) exit 1 ;;
esac
printf 'FINAL_REVIEWED_PLAN_SHA256=%s\n' "$FINAL_REVIEWED_PLAN_SHA256"
SH
```

Insert that value only into the Java `PLAN_SOURCE_SHA256` constant; do not edit
this plan afterward. Contracts requires `PLAN_SOURCE` to resolve to the exact
current plan path, requires its regular non-symlink bytes to equal that constant,
and requires the exclusion expression to occur exactly once. The generated
`commands.sh` repeats the same fixed hash check before every irreversible stage
and copies those exact bytes create-new to `attempt-002/execution-plan.md`.
Because `commands.sh` and the test source are source-locked, the plan bytes are
transitively bound without staging the repository `plans/` path. No literal
extra active-attempt token and no broader `plans/**` exclusion is allowed. Any
later plan change invalidates the source constant and requires a new reviewed
plan and attempt.

11. Remove the broad `backend/tests/src/test/resources/**` exclusion from Java and every shell worktree snapshot. The current resource tree is clean; any later fixture or qrel change must therefore appear as worktree drift and stop before qrel loading.

12. Expand `preflight_archive_paths` to the complete final 25-path commit set: test source, six attempt-001 failed-provenance files, and eighteen attempt-002 archive files. It also checks all three phase locks according to the phase state. The failed-provenance prefix is assembled from separate `attempt-` and `001` shell fragments so the active-attempt old-token postcondition remains meaningful.

13. Replace the check-then-plain-`mv` publication in `archive_once`, and add
    a durable Selection consumption barrier without adding a payload file.
    `preflight_archive_paths` takes one exact argument, `absent` or
    `reserved`. Rename the two existing publication-staging bindings in
    place, preserving their two active-attempt token positions and therefore
    the frozen `commandsBytes` count of `39`:

    ```sh
    consumption_lock="$archive_parent/.attempt-002.selection-started"
    consumption_lock_rel='backend/tests/evidence/candidate102a-evidence-baseline-v2/.attempt-002.selection-started'
    ```

    The outer F5 wrapper exclusively owns the original authorization pathname.
    It calls `preflight_archive_paths absent`, completes all report, source,
    JDK, runtime, worktree, and sealed-phase preflights without reading that
    pathname, then consumes the separately authorized nonce into a mode-`0400`
    receipt and removes the original pathname. It repeats the full gate against
    that receipt. The generated `selection` command never opens, stats, or
    consumes the original authorization pathname; it accepts only the exported
    receipt and immediately before Maven executes:

    ```sh
    /bin/mkdir -m 0700 "$consumption_lock"
    test -d "$consumption_lock"; test ! -L "$consumption_lock"
    test "$(/usr/bin/stat -f '%Lp' "$consumption_lock")" = '700'
    ```

    From successful authorization-receipt creation the invocation capability is
    permanently consumed. From successful `mkdir` onward the Selection runtime
    attempt is also permanently consumed.
    `archive_once` starts with `preflight_archive_paths reserved`, which
    requires that exact directory, no symlink, mode `700`, and exactly one
    regular non-symlink mode-`0400` `authorization.json` file whose bytes equal
    the consumed receipt, and
    a still-absent final archive. It then reserves `"$archive"` with
    `/bin/mkdir -m 0700 "$archive"`, requires directory, non-symlink, and
    mode `700`, and copies every payload directly into that create-new final
    directory. It verifies the repository plan against `PLAN_SOURCE_SHA256`
    immediately before copying it create-new as `execution-plan.md`. Remove
    every remaining publication-staging reference from `commands.sh` and use
    no `mv` in generated command/archive code; the separate F7 Git index
    installation may use its one private same-filesystem rename after the ref
    transaction. Reject
    every non-directory/non-regular entry. Generate
    and verify `SHA256SUMS` only inside the reserved final archive.

    `archive_once`, the outer F5 wrapper, and F7 never remove any phase lock.
    All three locks remain permanently as create-only local consumption
    evidence after exact staging and the verified commit. A
    process interruption, Maven/report/marker failure, or partial archive
    retains the applicable lock and permanently stops attempt-002. Once a
    final archive path exists, that create-only directory is an additional
    archive-phase barrier. No
    barrier or archive is deleted, reused, or followed by Selection replay.

    The full-path `git ls-files`/`check-ignore` preflight also covers the
    three phase locks, `execution-plan.md`, and the
    Selection consumption-lock path. `ls-files` accepts only status `0` and empty
    output; `check-ignore --no-index` accepts only status `1`.

14. Keep Contracts and Freeze locks through Selection and final archive
    postflight. After each successful phase postflight, create-new
    `reports.sha256` inside that phase lock from the two exact report snapshots.
    It has exactly two `shasum -a 256` lines and is sealed mode `0400`;
    `reports/` contains only the two mode-`0400` snapshots, and the containing
    directory chain is sealed from `0700` to `0500`. Every later phase verifies the
    prior manifest with `shasum -c` and reruns the complete XML/TXT semantic
    validator before creating its own lock. `archive_once` repeats those checks
    for Contracts and Freeze, validates Selection, copies all six reports with
    `cmp`, and validates all six archived reports before generating the final
    `SHA256SUMS`.

15. Replace every archive/failure `mkdir -p` helper with a component-wise
    creator rooted at an already verified directory. For each relative parent
    component, reject `..`, `/`, NUL, existing symlinks, and non-directories;
    otherwise create one directory and immediately prove directory/non-symlink.
    No copy may follow an intermediate symlink.

16. Use one report validator everywhere. It first requires exactly one
    `testsuite` (whether it is the document root or the sole child of
    `testsuites`) and exactly one `testcase`; every attribute, testcase, and
    `system-out` XPath is scoped to that sole suite. Any producer whose empty or
    malformed output could otherwise satisfy an acceptance predicate runs as a
    standalone checked command. Fixed-value comparisons may use command
    substitution only where empty output necessarily fails the comparison.
    Acceptance-critical source-lock and archive digests are parsed without an
    unchecked pipeline.

17. Snapshot report bytes before validating or hashing them. Contracts and
    Freeze each copy XML/TXT create-new into a private temporary directory,
    validate exact stdout and credentials there, then copy only those validated
    bytes create-new into the active phase lock, generate a two-line manifest,
    and seal the lock. Every later phase validates only the sealed snapshots.
    Selection follows the same private temporary-snapshot rule before copying
    validated bytes into the reserved archive. Mutable live Surefire paths are
    never the checksum preimage.

18. Each selector wrapper is one complete, directly executable shell block.
    Contracts and Freeze are consumed by their create-only phase-lock `mkdir`.
    Selection has two explicit irreversible boundaries: atomic authorization
    receipt creation consumes the invocation capability, and the create-only
    Selection-lock `mkdir` consumes the runtime attempt. A
    pre-existing lock rejects replay before Maven and writes nothing. Any
    nonzero exit after this invocation has created its lock leaves that lock and
    all observed target bytes in place, reports the shell's exact exit status,
    and stops; there is no failure-archive/recovery state machine and no
    operator-transcribed exit code. Before
    Selection, both the F5 wrapper and generated `commands.sh` require the exact
    nonce-bound file capability defined by item 24 plus the three secondary
    `true/002/true` values. All non-consuming drift and downstream-artifact
    preflights run before the first authorization-file read. The complete gate
    then runs again against the consumed receipt immediately before the
    create-only Selection lock. Missing or mismatched values exit before
    authorization consumption; failure after receipt creation consumes that
    capability and permanently stops the invocation.

19. Every Git pathname stream (`status`, `ls-files`, staged paths, and commit
    paths) is NUL-delimited. Archive `find` traversals operate only below a
    fixed-name allowlist that rejects LF and NUL and are captured by a
    standalone successful producer before counting. One private temporary
    directory is created first and an EXIT/HUP/INT/TERM cleanup trap is armed
    immediately; later temporary files are children of that directory.

20. The generated `commands.sh` SHA256 and size are included in
    `source-lock.json`. F5 reads the unique binding, verifies file mode,
    size, and SHA immediately before execution, and `archive_once` repeats the
    same binding against the copied bytes. Worktree exclusions do not weaken
    this execution-edge binding.

21. Add a single canonical-marker router used before staging and again against
    the committed blob. It accepts exactly one of three tuples:

    ```text
    INVALID: status=INVALID, observation=null, selectedBoundary=null,
             decision=null, errorCode is one fixed nonempty error code
    DIVERGED: status=VALID, observation=DIVERGED_FROM_12_4,
              selectedBoundary=null,
              decision=STOP_CANDIDATE102A_SEAM_BASELINE_DIVERGED,
              errorCode=null
    MATCHED: status=VALID,
             observation=MATCHED_12_VISIBLE_4_FRONTIER_MISSING,
             selectedBoundary=COLBERT_FRONTIER_PRESERVATION,
             decision=PROCEED_TO_CANDIDATE102A_SEAM_PLAN_REVIEW,
             errorCode=null
    ```

    The router also requires attempt `002`, the fixed namespace/schema,
    `productionChange=false`, and `algorithmChange=false`. Each VALID route
    binds `sourceLockSha256` to the archived `source-lock.json`, requires the
    exact 13 checkpoint-presence keys, exact access counts `3/0/1/0`, and the
    executor's exact 200 accepted/completed/done Futures with active/queued `0`
    at the qrel-freeze boundary and `terminatedAfterCleanup=true`. INVALID accepts only
    the exact 17-value `FIXED_ERRORS` allowlist. Both VALID routes independently
    require 40 queries, 16 target queries, the exact seven boundary keys,
    nonnegative integer counts, and the unresolved-count identity. MATCHED is
    derived only from `COLBERT90_VISIBLE=12`,
    `COLBERT_FRONTIER_NOT_PRESERVED=4`, the other five counts equal to zero,
    and `unresolvedTargetQueryCount=4`; DIVERGED requires the negation of that
    exact count predicate. Its route string is persisted only in the execution
    log; marker bytes remain immutable.

22. Source-lock the no-protected-raw-output proof: exactly one `System.out`
    call exists and it prints only the canonical marker; `System.err`,
    `printStackTrace`, qrel/dataset serialization, file writes, and logger calls
    are absent from `loadQrelsAfterRanking` and the evaluation path. The report
    validator rejects failure/error elements and unexpected testcase count,
    requires zero marker-prefixed lines for Contracts/Freeze and exactly one
    canonical marker line for Selection, and applies the fixed
    credential-property/private-key/
    bearer-token scan to every sealed and archived XML before persistence or
    staging.
    This, plus the source-locked single qrel call and the archive allowlist,
    is the bounded proof that reports cannot contain Selection qrel or Holdout
    raw bytes; no filesystem-monitor claim is made.

23. No selector/archive recovery claim or F7 terminal-failure file is created.
    The three create-only phase locks are the permanent replay barriers. F7
    builds the commit from an exclusively reserved Git index lock, then uses one
    `update-ref --stdin` transaction to verify symbolic HEAD and the expected old
    branch OID before installing the commit. If F7 fails before the ref moves,
    it releases only its own index lock; if the ref moves but index installation
    fails, it leaves the prepared index lock in place for read-only diagnosis.
    No failure permits Selection replay.

24. Revision 10 closes the Selection authorization boundary with a second,
    invocation-local capability. The three exported boolean/attempt values are
    retained as secondary checks but are never sufficient by themselves. Only
    after a later user message explicitly names and authorizes attempt-002 may
    the main agent create this exact regular non-symlink mode-`0600` file with
    a noclobber write:

    ```text
    backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2/
      .attempt-002-selection-authorization.json
    ```

    Its canonical JSON bytes have exactly these recursively sorted keys plus
    one trailing LF:

    ```text
    allowSelectionOnce=true
    allowSelectionQrelAfterFreezeOnce=true
    attempt="002"
    authorizationNonce="[0-9a-f]{64}"
    namespace="candidate102a-evidence-baseline-v2"
    planSha256=PLAN_SOURCE_SHA256
    sourceLockSha256=SHA256(runtime source-lock.json)
    testSourceSha256=the unique TEST_SOURCE source-lock binding
    ```

    The authorization creator is the exact wrapper in F1b. It first proves the
    path absent/non-symlink, validates both sealed phase trees, source lock,
    plan/test/commands bindings, HEAD/branch, and all downstream artifact
    absences; obtains 32 random bytes from `/dev/urandom` with independently
    checked producer status; writes the canonical object create-new under
    `umask 077`; and readbacks every field/SHA. It runs only in the turn
    containing the new explicit authorization. Compile, Contracts, Freeze,
    inherited environment, the plan file, and `commands.sh` never create this
    file.

    The outer F5 wrapper rejects a missing, symlinked, non-regular, wrong-mode,
    malformed, stale, or mismatched original authorization file and exclusively
    consumes it. Generated `commands.sh selection` rejects a missing,
    symlinked, non-regular, wrong-mode, malformed, stale, or mismatched consumed
    receipt and never references the removed original pathname. Both also
    require the three exported values exactly as before. After every
    non-consuming source/JDK/worktree/phase/downstream-artifact preflight has
    passed, the wrapper creates or verifies this private non-symlink directory:

    ```text
    backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2/
      .attempt-002-selection-authorizations-consumed/
    ```

    It atomically creates the create-new receipt
    `<authorizationNonce>.json` with a same-filesystem hard link, verifies the
    two names have identical device/inode and bytes, removes the original
    authorization pathname, and seals the remaining receipt mode `0400`.
    Existing destination, link/remove failure, byte mismatch, or invalid mode
    stops. From successful link creation onward the invocation authorization is
    permanently consumed even if a later non-consuming Selection preflight
    fails. A later invocation requires a fresh user authorization and a fresh
    nonce/file; inherited environment values and the consumed receipt cannot be
    reused. The receipt basename must equal its canonical JSON nonce. The
    wrapper reruns the complete drift gate and revalidates the consumed receipt
    immediately before the Selection attempt boundary. The Selection boundary
    then performs exactly:

    ```sh
    /bin/mkdir -m 0700 "$selection_lock"
    (set -C; /bin/cat "$consumed_authorization" > "$selection_lock/authorization.json")
    /bin/chmod 0400 "$selection_lock/authorization.json"
    /usr/bin/cmp -s "$consumed_authorization" "$selection_lock/authorization.json"
    ```

    and revalidates the copied bytes before Maven. If `mkdir`, create-new copy,
    mode, or comparison fails, attempt-002 is consumed and stops; the lock is
    never removed and Selection is never replayed. A
    successful Selection lock therefore contains exactly the one sealed
    `authorization.json` file, not an empty directory. F7 verifies that exact
    tree and does not stage it. Any earlier statement that ambient environment
    values alone authorize Selection, or that the successful Selection lock is
    empty, is superseded by this clause.

25. `verify_report` has one schema for every occurrence. It first proves there
    is exactly one selected `testsuite` and exactly one `testcase`, then scopes
    output to that testcase because Surefire 3.0.2 nests `system-out` below
    `testcase`. All three phases require exactly one testcase `system-out`, zero
    testcase `system-err`, zero failure/error nodes, the exact suffixed
    classname, and exactly one full TXT summary line using `grep -Fxc`.
    Contracts and Freeze allow their already-observed non-sensitive framework
    log lines but require zero marker-prefixed lines. Selection redirects the
    unique `system-out` node text to a private file, extracts the sole complete
    marker-prefixed line to a second file, constructs
    `prefix + canonical-marker.json` as a third file, and compares those line
    bytes with `cmp`. It never round-trips Selection output through shell
    command substitution and never applies `string(...)` until suite,
    testcase, and output-node count predicates have succeeded. Extra marker
    lines, stderr nodes, testcases, failure/error nodes, or malformed XML fail
    before persistence, phase sealing, archive checksum generation, or staging.

26. Every marker router, both pre-staging and committed-blob copies, begins by
    requiring one of the following exact ASCII-sorted top-level key arrays;
    extra or missing keys fail:

    ```text
    VALID=[access,algorithmChange,algorithmConclusion,attempt,budgets,
      causalScope,checkpointPresence,contextLineage,decision,
      earliestBoundaryQueryCounts,errorCode,infrastructure,mapping,namespace,
      observation,pathSnapshotSha256,predecessorStatus,productionChange,
      queryCount,schemaVersion,selectedBoundary,sourceLockSha256,status,
      targetQueryCount,unresolvedTargetQueryCount]

    INVALID=[access,algorithmChange,algorithmConclusion,attempt,causalScope,
      decision,errorCode,infrastructure,namespace,observation,
      predecessorStatus,productionChange,queryCount,schemaVersion,
      selectedBoundary,sourceLockSha256,status,targetQueryCount]
    ```

    Both schemas require fixed
    `predecessorStatus=SUPERSEDED_UNRECOVERABLE`, fixed
    `causalScope=post-routing-single-retrieve-once-single-variant-counterfactual-rerank-orchestration-vecsim-disabled-colbert90-admission90-selection-v1`,
    fixed `algorithmConclusion=NOT_REACHED`, attempt `002`, and exact namespace,
    schema, and false production/algorithm booleans. Every VALID route requires
    the exact 13 `checkpointPresence` keys and independently requires all 13
    values to have JSON type `boolean` and value `true`:

    ```jq
    ([.checkpointPresence[] | type == "boolean" and . == true] | all)
    ```

    `valid_counts` additionally requires every seven-boundary value and
    `unresolvedTargetQueryCount` to be an integer in `[0,16]`, requires
    `COLBERT90_VISIBLE + unresolvedTargetQueryCount == 16`, and retains the
    existing unresolved identity. `valid_evidence` requires the executor's exact
    key set and binds `corePoolSize=4`, `maxPoolSize=4`, `queueCapacity=32`, and
    `queueRemainingCapacityBeforeQrelFreeze=queueCapacity=32` in addition to the
    200-Future and idle/termination predicates. These predicates apply equally
    to MATCHED and DIVERGED. INVALID routing requires the exact INVALID key set,
    nonnegative integer `queryCount` and `targetQueryCount` in their actual
    progressively updated state, the exact four-key nonnegative access object,
    the exact three-key `{docker,executor,seed}` infrastructure object, nullable
    or 64-hex `sourceLockSha256`, null observation/selection/decision, and the
    fixed error allowlist. Early failures therefore retain zero initialization;
    later failures retain mechanically observed counters rather than being
    rewritten to zero.

27. Every selector phase rejects all downstream attempt artifacts before its
    own create-only lock. Contracts checks the attempt-002 runtime root, all
    Freeze and Selection XML/TXT report paths, Freeze/Selection locks, and final
    archive absent twice: once in its early preflight and once immediately
    before the Contracts lock/Maven boundary. Freeze checks all Selection
    XML/TXT paths, Selection lock, and final archive absent twice: once in its
    early preflight and once immediately before the Freeze lock/Maven boundary.
    Selection checks its XML/TXT, Selection lock, and final archive absent
    immediately before consuming its authorization and repeats them before its
    own lock. Each check uses `test ! -e` plus `test ! -L`; any file,
    directory, symlink, or special entry fails before that phase lock. Contracts
    additionally requires the attempt-002 runtime root absent at both checks;
    Freeze remains the only phase allowed to create it.

28. A sealed Contracts or Freeze phase lock has one exact tree. The lock root
    contains only the `reports/` directory and regular non-symlink
    `reports.sha256`; `reports/` contains only its exact XML and TXT snapshot.
    Before sealing and on every later verification, a standalone successful
    `find` producer is compared byte-for-byte to the fixed four-entry tree
    allowlist (`reports/`, XML, TXT, and `reports.sha256`); symlink and
    special-entry counts must both be zero. Extra files,
    directories, links, or devices fail before the next phase.

29. Immediately before Selection authorization consumption, F5 performs a
    final drift gate using only the sealed runtime/source lock and fixed plan:

    ```text
    all sourceFiles entries: path/type/SHA exact
    test source, ColbertScorer, fixture generator, and both dirty reactor bytes exact
    plan path/SHA exact
    commands.sh mode=0755, size, and SHA exact
    branch/HEAD and source-lock JDK identity exact:
      javaHome=/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home
      javaVersion=17.0.19
      javaRuntimeVersion=17.0.19+10
      javaVendor=Eclipse Adoptium
      osArch=aarch64
    runtimeContract exact:
      queryCount=40, familyCount=20, corpusCount=1120,
      checkpointCount=13, futureCount=200,
      qrelAfterRankingFreeze=true, qrelAccessCount=1,
      holdoutPathOperationCount=0
    current NUL-safe six-entry worktree snapshot byte-equivalent to before
    Contracts and Freeze sealed trees/reports exact
    qrel counter remains 0 and no Holdout path operation has occurred
    ```

    The complete gate is the generated `commands.sh selection-preflight`
    command. It is read-only and runs before the first authorization-file read,
    again after consumed-receipt readback, and inside `commands.sh selection`
    immediately before the Selection lock `mkdir`. Any
    mismatch stops without consuming Selection. This supersedes any text that
    defers source/worktree drift detection to F7.

These Revision 10 clauses are normative and supersede conflicting Revision 7
wording or shell comments. They do not authorize Selection attempt-002; the
current authorization state remains false.

30. The generated `commands.sh selection-preflight` is a distinct, read-only
    command and its bytes are source-locked. It accepts no positional argument,
    returns nonzero on any failed assertion, and contains none of `mvn`,
    `mkdir`, `ln`, `mv`, `rm`, `chmod`, `git add`, `git commit`, `git push`, or
    writes to a repository/runtime/evidence path. It performs this exact order:

    ```text
    regular non-symlink checks for repo/backend/tests/runtime/source-lock/
      commands.sh/plan/test source and mode commands.sh=0755
    git rev-parse --show-toplevel, --show-object-format, HEAD and branch exact
    source-lock canonical parse, schema, unique sourceFiles bindings, and every
      sourceFiles path/type/SHA readback (including fixture generator, scorer,
      test source, and both dirty reactor bytes)
    plan SHA == PLAN_SOURCE_SHA256 and command path/size/SHA == source-lock
    JDK readback from java -XshowSettings:properties -version, with checked
      producer status, exact java.home/java.version/java.runtime.version/
      java.vendor/os.arch values
    NUL-safe status snapshot with the one plan exclusion and no broad resources
      exclusion; canonical six-entry before snapshot and current tuple/SHA/
      indexMode/indexObjectId byte-equivalent after sorted comparison
    Contracts and Freeze phase locks: exact sealed tree, mode, two-line
      reports.sha256, shasum -c, and the single shared XML/TXT validator
    Selection XML/TXT, Selection lock, final archive, and consumed-receipt
      directory absent before authorization consumption; the seven existing
      publish inputs (test source plus six attempt-001 provenance files) present
      and validated; all eighteen attempt-002 archive leaves plus the final
      archive root absent; no qrel or Holdout path is constructed or inspected
    ```

    During F5, the first invocation is made before opening or `stat`ing the
    authorization file. After hard-link receipt creation, the same command is invoked again
    with `CANDIDATE102A_SELECTION_AUTHORIZATION_RECEIPT` exported; only then it
    additionally validates the mode-0400 receipt, canonical nonce basename,
    same-filesystem/device-inode binding, and the consumed-directory exact
    one-file tree. The `selection` branch invokes the same command a third time
    immediately before its create-only Selection-lock `mkdir`; it then verifies
    the receipt copy and only afterward invokes Maven. Static Contracts checks
    require one `selection-preflight` case, three call sites in this relative
    order, and zero writes in its branch. The separate F1b creator invokes the
    read-only command once before creating the original authorization file; it
    does not invoke it again after creation and is outside this F5 three-call
    count.

31. `preflight_archive_paths` is the sole publishability function. Its complete
    NUL-safe `paths` array has exactly 25 repository-relative entries: the test
    source; the six attempt-001 failed-provenance files; and the eighteen
    attempt-002 archive files (`canonical-marker.json`, executable
    `commands.sh`, `execution-plan.md`, two reactor copies, six reports, three
    Selection non-qrel inputs, `source-lock.json`, two snapshots, and
    `SHA256SUMS`). It also checks the three phase-lock paths and the consumed
    Selection receipt path as separate control paths. For each entry it captures
    `git ls-files` status independently and accepts only `rc=0` with empty
    stdout; it then captures `git check-ignore --no-index` independently and
    accepts only `rc=1`. `rc=0` (ignored), `rc>=2` (command failure), a tracked
    path, or a symlink fails closed. In both calls the test source and six
    attempt-001 provenance files must already exist as regular non-symlinks,
    while all eighteen attempt-002 archive leaves and the final archive root
    must remain absent. The `absent`/`reserved` argument changes only the exact
    expected state of the Selection consumption lock: absent before Selection,
    then a sealed one-file lock at `archive_once`. Selection calls this
    function once before its report preflight; `archive_once` calls it as its
    first executable statement with the reserved-lock state. Static checks
    require definition once, exactly two calls, Selection-call < report
    preflight < Maven, and archive-call < archive `mkdir`.

32. The source-locked command generator uses one complete shell template with
    exactly one `selection-preflight` case and one `selection` case. The latter
    repeats the read-only gate, requires only the consumed receipt and exported
    `true/002/true` values, creates the Selection lock with create-only
    `mkdir -m 0700`, copies the receipt create-new to
    `authorization.json`, seals it `0400`, compares bytes, and then runs the
    full Selection Maven command. No command branch may silently turn a failed
    `xmllint`, `awk`, `grep`, `jq`, `git`, or `shasum` producer into a passing
    comparison; every producer whose output is acceptance-critical has its
    exit status captured and checked before the output is consumed.

33. Every wrapper's temporary-directory cleanup is an `EXIT` trap that saves
    `$?`, disables signal traps, removes only its own children, and returns the
    saved status. HUP/INT/TERM handlers call that same cleanup path exactly once
    with statuses `129/130/143`; they do not continue into Maven, archive,
    staging, or commit code. No signal handler removes a phase lock, consumed
    receipt, runtime attempt, or final archive.

No other behavioral code changes.

### E3. Freeze a new external before snapshot

After all source edits except the snapshot hash constant, execute this bootstrap exactly once. It treats status as NUL records, compares the six records independent of output order, validates every type/SHA/index binding, and then copies the already canonical snapshot bytes using a create-new noclobber write:

```sh
/bin/zsh -eu <<'ZSH'
set -o pipefail
repo='/Users/achilles/Documents/许子祺/Agent'
old='/Users/achilles/.codex/evidence-worktree-snapshots/candidate102a-evidence-baseline-v2-attempt-001-before.json'
new='/Users/achilles/.codex/evidence-worktree-snapshots/candidate102a-evidence-baseline-v2-attempt-002-before.json'

paths=(
  '23030327许子祺/.officecli/config.json'
  '23030327许子祺/1-第2周报.docx'
  'backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java'
  'backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java'
  'frontend/package-lock.json'
  'scripts/start.sh'
)
xys=(' D' ' M' ' M' ' M' ' M' ' M')
types=('MISSING' 'REGULAR' 'REGULAR' 'REGULAR' 'REGULAR' 'REGULAR')
shas=(
  ''
  '3c1a18239854fcbb1cbc54a7b70eb42e6b4afdc633afb6e8b38b13b64fdb03f8'
  '75746407754cdfcf350960bd9587831bb7e2a76df4ba0ef2d46c1a81ea8a2e7f'
  '898d815973fcaedf9dd7bd0a0f73bd6d18431c8ccd4c4b5b385c08a0ed561ffd'
  'fd6650108cdadbcf0e23a9f0471eac4aab987a230f2091b40a7a439e2c5a509a'
  '26436484ad4c1306cfd108611addfd09fe8025d067e93b39be5d01e5f3de0f59'
)
modes=('100644' '100644' '100644' '100644' '100644' '100755')
worktree_modes=('' '644' '644' '644' '644' '755')
oids=(
  '2691755a674e11fd6cda9dd68b6c4424d4a94396'
  '0ab3503ba6aace7c8acf91b5b122b9295320c94a'
  'bc373b9f8e6ab28a1b3eef34ff393e7872bcf818'
  'dafe815f87982c4b472244746d2975aebc9f3734'
  '6468d92396b862beab0ef63955a94c46bf39d684'
  '019704d9041adfa66059a50f6d01c8017b43ee16'
)

test -d "$repo"; test ! -L "$repo"
test -f "$old"; test ! -L "$old"
old_digest=$(/usr/bin/shasum -a 256 "$old"); old_sha=${old_digest%% *}
test "$old_sha" = '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
test ! -e "$new"; test ! -L "$new"
test "$(/usr/bin/git -C "$repo" rev-parse HEAD)" = '5c84bb044352a3fef684a37f70cc08ac80058c7b'
test "$(/usr/bin/git -C "$repo" symbolic-ref --short HEAD)" = 'codex/candidate102a-evidence-baseline-v2'

actual=$(/usr/bin/mktemp /tmp/candidate102a-snapshot-actual.XXXXXX)
expected=$(/usr/bin/mktemp /tmp/candidate102a-snapshot-expected.XXXXXX)
actual_sorted=$(/usr/bin/mktemp /tmp/candidate102a-snapshot-actual-sorted.XXXXXX)
expected_sorted=$(/usr/bin/mktemp /tmp/candidate102a-snapshot-expected-sorted.XXXXXX)
cleanup_snapshot() {
  cleanup_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$actual" "$expected" "$actual_sorted" "$expected_sorted"
  return "$cleanup_status"
}
trap cleanup_snapshot EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

/usr/bin/git -C "$repo" status --porcelain=v1 -z --untracked-files=all --no-renames -- . \
  ':(exclude)backend/tests/target/**' \
  ':(exclude)backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java' \
  ':(exclude)backend/tests/evidence/candidate102a-evidence-baseline-v2/**' \
  ':(exclude)plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md' > "$actual"

for index in {1..6}; do
  printf '%s %s\0' "$xys[$index]" "$paths[$index]"
done > "$expected"
LC_ALL=C /usr/bin/sort -z "$actual" > "$actual_sorted"
LC_ALL=C /usr/bin/sort -z "$expected" > "$expected_sorted"
/usr/bin/cmp -s "$expected_sorted" "$actual_sorted"

for index in {1..6}; do
  file_path="$repo/$paths[$index]"
  if test "$types[$index]" = 'MISSING'; then
    test ! -e "$file_path"; test ! -L "$file_path"
  else
    test -f "$file_path"; test ! -L "$file_path"
    test "$(/usr/bin/stat -f '%Lp' "$file_path")" = "$worktree_modes[$index]"
    file_digest=$(/usr/bin/shasum -a 256 "$file_path"); file_sha=${file_digest%% *}
    test "$file_sha" = "$shas[$index]"
  fi
  printf '%s %s 0\t%s\0' \
    "$modes[$index]" "$oids[$index]" "$paths[$index]" > "$expected"
  /usr/bin/git -C "$repo" ls-files -s -z -- "$paths[$index]" > "$actual"
  /usr/bin/cmp -s "$expected" "$actual"
done

(set -C; /bin/cat "$old" > "$new")
test -f "$new"; test ! -L "$new"
/usr/bin/cmp -s "$old" "$new"
new_digest=$(/usr/bin/shasum -a 256 "$new"); new_sha=${new_digest%% *}
test "$new_sha" = '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
/usr/bin/jq -e '.schemaVersion == "candidate102a-worktree-snapshot-v1" and .phase == "BEFORE" and .headCommit == "5c84bb044352a3fef684a37f70cc08ac80058c7b" and (.entries | length) == 6' "$new" >/dev/null
ZSH
```

The create-new file has deterministic SHA `94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e` because its bytes must compare equal to the old canonical snapshot after the live six-entry proof. Patch the attempt-002 path and retain this verified SHA constant.

Then complete the remaining source binding:

```text
canonical parse/reserialize byte equality
compute final test-source SHA256
```

The attempt-002 snapshot path is:

```text
/Users/achilles/.codex/evidence-worktree-snapshots/candidate102a-evidence-baseline-v2-attempt-002-before.json
```

The snapshot must remain six entries and contain no plan or evidence path. The exact plan exclusion must be present once in source and commands contracts. The attempt-001 snapshot and reports are re-hashed before and after this work and must not change.

### E4. Static review gate

Before any compile or selector, independent reviewers must jointly report `P0=0/P1=0` for:

```text
attempt identity and token counts
snapshot path/SHA/live-state binding
NUL-safe Unicode index binding under core.quotePath=true
attempt-001 provenance archive and checksums
Surefire classname and marker parsing
commands.sh executable modes
durable Selection consumption lock and replay rejection
Freeze-to-Selection independent authorization stop
Selection-once/qrel-after-freeze/Holdout=0
archive create-new behavior
exact 17-path SHA256SUMS coverage in F5/F7
direct-wrapper consumption and replay rejection
25-path staging and commit isolation
```

Any P0/P1 is patched only within this plan's write boundary and re-reviewed. No Maven command runs while P0/P1 remains.

## F. Runtime and Evidence Closure

### F1. Fixed runtime order and stop boundary

Execution is split by the mandatory Selection authorization boundary:

```text
already-approved implementation phase
  compile
  -> Contracts attempt-002
  -> Freeze attempt-002
  -> verify and report the sealed Contracts/Freeze evidence
  -> STOP_AWAITING_ATTEMPT_002_SELECTION_AUTHORIZATION

only after a new explicit user authorization naming attempt-002
  F1b create-new invocation authorization
  -> F5 consume nonce-bound receipt
  -> Selection attempt-002 exactly once
  -> create-new persistent attempt-002 archive
  -> SHA256SUMS verification
  -> exact 25-path staging
  -> one local commit
```

`SELECTION_AUTHORIZATION_FILE=ABSENT` is the default and current state. The earlier
attempt-001 Selection authorization is consumed with attempt-001 and cannot be
reused for attempt-002. F5 fails before authorization consumption unless the
invocation-local canonical file and all three exported values are exact. After
F5 exclusively consumes and removes that original pathname, the generated
`commands.sh selection` branch fails before the Selection lock unless the
exported consumed receipt and all three exported values are exact:

```text
ALLOW_SELECTION_ONCE=true
CANDIDATE102A_SELECTION_ATTEMPT=002
ALLOW_SELECTION_QREL_AFTER_FREEZE_ONCE=true
```

Only a later user message explicitly authorizing Selection for attempt-002 can
cause the main agent to run F1b and set those values for the one F5 invocation.
Compile, Contracts, Freeze, the plan file, and ambient shell state never imply
or create that capability. The three environment values without the canonical
file are insufficient. F7 additionally requires the already-consumed Selection
lock and is unreachable before the successful F5 archive postflight.

Compile failure before any attempt-002 report/runtime path does not consume
attempt-002 and ends this execution round without changing source. Every
selector phase has an atomic create-only consumption barrier, so consumption
does not depend on whether Maven managed to emit a report:

| Boundary | Consumed when | Success replacement | Failure route |
|---|---|---|---|
| Compile | never, provided every attempt-002 runtime/report/lock/archive path remains absent | N/A | stop; no selector started |
| Contracts | `.attempt-002.contracts-started/` `mkdir` succeeds immediately before Maven | create-new XML/TXT snapshots plus two-line `reports.sha256`; files/dirs sealed `0400/0500` and retained | retain lock and observed target bytes; report exact exit status; stop |
| Freeze | `.attempt-002.freeze-started/` `mkdir` succeeds immediately before Maven | prior sealed snapshots revalidated; create-new Freeze snapshots plus manifest; both locks retained | retain locks/runtime and observed target bytes; report exact exit status; stop |
| Selection authorization | same-filesystem hard-link creation of `.attempt-002-selection-authorizations-consumed/<nonce>.json` succeeds after the complete read-only drift gate | original authorization pathname removed; mode-`0400` receipt retained and copied into the later Selection lock | receipt/path state retained; no Selection replay; a new separately authorized attempt is required |
| Selection runtime | `.attempt-002.selection-started/` `mkdir` succeeds immediately before Maven, after receipt revalidation | lock contains exactly one mode-`0400` `authorization.json`; all prior report manifests/semantics revalidated; fully verified final `attempt-002/` archive; all three locks remain permanently as local create-only consumption evidence after the verified local commit | retain lock/authorization copy/partial archive and observed target bytes; report exact exit status; stop |

All three locks are sibling directories under
`backend/tests/evidence/candidate102a-evidence-baseline-v2/`, are created with
mode `0700`, and must be directory/non-symlink. After a successful Contracts
or Freeze postflight it contains only `reports/` with the exact XML/TXT
snapshots plus the regular non-symlink mode-`0400` two-line
`reports.sha256`; files and directories are sealed mode `0400/0500`. A later phase requires
and revalidates every earlier lock and rejects any later lock. The Selection
lock contains exactly one regular non-symlink mode-`0400`
`authorization.json`, byte-identical to the nonce-bound consumed receipt. All
locks are retained after commit; no lock-cleanup phase exists and no consumed
phase is replayed.

### F1a. Consumed-phase failure stop

There is deliberately no attempt-002 failure archive, recovery state machine,
or selector driver. F3, F4, and F5 are the complete invocations. Each wrapper
performs all non-consuming preflight first. Contracts and Freeze then reserve
their own phase lock immediately before Maven. Selection first consumes its
separately authorized receipt, reruns the full gate, and then reserves its phase
lock with one create-only `mkdir` immediately before Maven. A pre-existing lock
makes that wrapper fail before Maven with `REPLAY_REJECTED`; it cannot be
mistaken for a lock created by the current invocation.

If Maven, report snapshotting, semantic validation, runtime validation, or
archive postflight fails after the wrapper has reserved its lock, the wrapper
returns its own nonzero status unchanged. The phase lock and all live
target/runtime bytes remain in place, the main agent reports the exact command
status, and the gate stops permanently. Nothing invokes a recovery archiver,
nothing creates `attempt-002-failed`, and no consumed phase is replayed. A
separate, newly approved provenance plan is required before any later attempt.
This is the minimal persistent failure boundary requested for this gate.

### F1b. Attempt-002 Selection authorization creator

This wrapper is present for decision completeness but is forbidden until a new
user message explicitly authorizes Selection attempt-002. It creates only the
invocation-local capability; it does not create the consumed receipt, Selection
lock, report, archive, or run Maven.

```sh
/bin/sh -eu <<'SH'
umask 077
test "${ALLOW_SELECTION_ONCE-}" = 'true'
test "${CANDIDATE102A_SELECTION_ATTEMPT-}" = '002'
test "${ALLOW_SELECTION_QREL_AFTER_FREEZE_ONCE-}" = 'true'
repo='/Users/achilles/Documents/许子祺/Agent'
attempt_parent="$repo/backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2"
attempt="$attempt_parent/attempt-002"
source_lock="$attempt/source-lock.json"
commands="$attempt/commands.sh"
plan="$repo/plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md"
test_source="$repo/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java"
authorization="$attempt_parent/.attempt-002-selection-authorization.json"
consumed_authorizations="$attempt_parent/.attempt-002-selection-authorizations-consumed"
selection_lock="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2/.attempt-002.selection-started"
archive="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-002"
selection_xml="$repo/backend/tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml"
selection_txt="$repo/backend/tests/target/surefire-reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt"

test -d "$attempt_parent"; test ! -L "$attempt_parent"
test -f "$source_lock"; test ! -L "$source_lock"
test -f "$commands"; test ! -L "$commands"
test "$(/usr/bin/stat -f '%Lp' "$commands")" = '755'
test -f "$plan"; test ! -L "$plan"
test -f "$test_source"; test ! -L "$test_source"
for absent_path in "$authorization" "$consumed_authorizations" \
  "$selection_lock" "$archive" "$selection_xml" "$selection_txt"
do
  test ! -e "$absent_path"; test ! -L "$absent_path"
done

# This source-locked branch is read-only and validates HEAD/branch, plan,
# source/JDK/runtime/worktree, sealed phases, and every downstream absence.
"$commands" selection-preflight

tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-authorization.XXXXXX)
cleanup_authorization() {
  cleanup_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$tmp/random" "$tmp/nonce" "$tmp/authorization.json" "$tmp/canonical" \
    "$tmp/last-byte" "$tmp/last-byte-code" "$tmp/last-byte-value"
  /bin/rmdir "$tmp"
  return "$cleanup_status"
}
trap cleanup_authorization EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

if /usr/bin/od -An -N32 -tx1 /dev/urandom > "$tmp/random"; then
  random_status=0
else
  random_status=$?
fi
test "$random_status" -eq 0
if /usr/bin/tr -d ' \n' < "$tmp/random" > "$tmp/nonce"; then
  nonce_status=0
else
  nonce_status=$?
fi
test "$nonce_status" -eq 0
nonce=$(/bin/cat "$tmp/nonce")
test "${#nonce}" -eq 64
printf '%s\n' "$nonce" | /usr/bin/grep -Eq '^[0-9a-f]{64}$'

plan_digest=$(/usr/bin/shasum -a 256 "$plan")
plan_sha=${plan_digest%% *}
source_lock_digest=$(/usr/bin/shasum -a 256 "$source_lock")
source_lock_sha=${source_lock_digest%% *}
test_backend_rel='tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java'
test "$(/usr/bin/jq -er --arg path "$test_backend_rel" '[.sourceFiles[] | select(.path == $path)] | length' "$source_lock")" = '1'
test_sha=$(/usr/bin/jq -er --arg path "$test_backend_rel" '.sourceFiles[] | select(.path == $path) | .sha256' "$source_lock")
test_digest=$(/usr/bin/shasum -a 256 "$test_source")
actual_test_sha=${test_digest%% *}
test "$test_sha" = "$actual_test_sha"

/usr/bin/jq -cnS \
  --arg nonce "$nonce" \
  --arg plan "$plan_sha" \
  --arg lock "$source_lock_sha" \
  --arg test "$test_sha" \
  '{allowSelectionOnce:true,
    allowSelectionQrelAfterFreezeOnce:true,
    attempt:"002",
    authorizationNonce:$nonce,
    namespace:"candidate102a-evidence-baseline-v2",
    planSha256:$plan,
    sourceLockSha256:$lock,
    testSourceSha256:$test}' > "$tmp/authorization.json"
/usr/bin/tail -c 1 "$tmp/authorization.json" > "$tmp/last-byte"
/usr/bin/od -An -tuC "$tmp/last-byte" > "$tmp/last-byte-code"
/usr/bin/tr -d ' ' < "$tmp/last-byte-code" > "$tmp/last-byte-value"
test "$(/bin/cat "$tmp/last-byte-value")" = '10'
/usr/bin/jq -cS . "$tmp/authorization.json" > "$tmp/canonical"
/usr/bin/cmp -s "$tmp/authorization.json" "$tmp/canonical"

(set -C; /bin/cat "$tmp/authorization.json" > "$authorization")
/bin/chmod 0600 "$authorization"
test -f "$authorization"; test ! -L "$authorization"
test "$(/usr/bin/stat -f '%Lp' "$authorization")" = '600'
/usr/bin/cmp -s "$tmp/authorization.json" "$authorization"
SH
```

### F2. Compile command

```sh
/bin/sh -eu <<'SH'
umask 077
repo='/Users/achilles/Documents/许子祺/Agent'
tests="$repo/backend/tests"
runtime="$tests/target/rag-eval/candidate102a-evidence-baseline-v2/attempt-002"
reports="$tests/target/surefire-reports"
evidence="$tests/evidence/candidate102a-evidence-baseline-v2"
assert_compile_unconsumed() {
  for absent_path in \
    "$runtime" \
    "$reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml" \
    "$reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt" \
    "$reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml" \
    "$reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt" \
    "$reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml" \
    "$reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt" \
    "$evidence/attempt-002" \
    "$evidence/.attempt-002.contracts-started" \
    "$evidence/.attempt-002.freeze-started" \
    "$evidence/.attempt-002.selection-started"
  do
    test ! -e "$absent_path"; test ! -L "$absent_path"
  done
}
assert_compile_unconsumed
if env JAVA_HOME=/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home PATH=/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/rtk mvn -f /Users/achilles/Documents/许子祺/Agent/backend/pom.xml -pl tests -am -DskipTests -Dmaven.resources.skip=true test-compile; then
  compile_status=0
else
  compile_status=$?
fi
assert_compile_unconsumed
exit "$compile_status"
SH
```

### F3. Contracts wrapper

```sh
/bin/sh -eu <<'SH'
umask 077
repo='/Users/achilles/Documents/许子祺/Agent'
reports="$repo/backend/tests/target/surefire-reports"
attempt="$repo/backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2/attempt-002"
evidence="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2"
contracts_lock="$evidence/.attempt-002.contracts-started"
freeze_lock="$evidence/.attempt-002.freeze-started"
selection_lock="$evidence/.attempt-002.selection-started"
success_archive="$evidence/attempt-002"
test_class='tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test'
suffix='candidate102a-evidence-baseline-v2-contracts-attempt-002'
xml="$reports/TEST-$test_class-$suffix.xml"
txt="$reports/$test_class-$suffix.txt"
old_xml="$reports/TEST-$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-001.xml"
old_txt="$reports/$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-001.txt"
freeze_xml="$reports/TEST-$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml"
freeze_txt="$reports/$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt"
selection_xml="$reports/TEST-$test_class-candidate102a-evidence-baseline-v2-selection-attempt-002.xml"
selection_txt="$reports/$test_class-candidate102a-evidence-baseline-v2-selection-attempt-002.txt"
copy_create() {
  source_file=$1; destination=$2
  test -f "$source_file"; test ! -L "$source_file"
  test ! -e "$destination"; test ! -L "$destination"
  (set -C; /bin/cat "$source_file" > "$destination")
  test -f "$destination"; test ! -L "$destination"
  /usr/bin/cmp -s "$source_file" "$destination"
}
xpath_value() { /usr/bin/xmllint --xpath "$1" "$2"; }
require_no_credentials() {
  report_xml=$1
  credential_xpath='count(//*[local-name()="property" and (contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api-key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api_key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"apikey") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"secret") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"password") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"credential"))])'
  allowed_xpath='count(//*[local-name()="property" and @name="hermes.rag.colbert.embedding-api-key" and @value=""])'
  credential_count=$(xpath_value "$credential_xpath" "$report_xml")
  allowed_count=$(xpath_value "$allowed_xpath" "$report_xml")
  test "$credential_count" = '1'; test "$allowed_count" = '1'
  if LC_ALL=C /usr/bin/grep -E -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|Bearer[[:space:]]+[A-Za-z0-9._~+/=-]{8,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}' "$report_xml" >/dev/null; then
    return 1
  else
    scan_status=$?
  fi
  test "$scan_status" -eq 1
}
verify_report() (
  report_root=$1; method=$2; report_suffix=$3; expected_marker_count=$4
  report_xml="$report_root/TEST-$test_class-$report_suffix.xml"
  report_txt="$report_root/$test_class-$report_suffix.txt"
  test -f "$report_xml"; test ! -L "$report_xml"
  test -f "$report_txt"; test ! -L "$report_txt"
  suite_nodes='(/*[local-name()="testsuite"] | /*[local-name()="testsuites"]/*[local-name()="testsuite"])'
  suite_count=$(xpath_value "count($suite_nodes)" "$report_xml")
  test "$suite_count" = '1'
  suite="($suite_nodes)[1]"
  tests_count=$(xpath_value "string($suite/@tests)" "$report_xml")
  failures_count=$(xpath_value "string($suite/@failures)" "$report_xml")
  errors_count=$(xpath_value "string($suite/@errors)" "$report_xml")
  skipped_count=$(xpath_value "string($suite/@skipped)" "$report_xml")
  testcase_count=$(xpath_value "count($suite/*[local-name()='testcase' and @name='$method' and @classname='$test_class($report_suffix)'])" "$report_xml")
  all_testcases=$(xpath_value "count($suite/*[local-name()='testcase'])" "$report_xml")
  failure_nodes=$(xpath_value "count($suite/*[local-name()='testcase']//*[local-name()='failure' or local-name()='error'])" "$report_xml")
  system_out_count=$(xpath_value "count($suite/*[local-name()='testcase']/*[local-name()='system-out'])" "$report_xml")
  system_err_count=$(xpath_value "count($suite/*[local-name()='testcase']/*[local-name()='system-err'])" "$report_xml")
  test "$tests_count" = '1'
  test "$failures_count" = '0'; test "$errors_count" = '0'
  test "$skipped_count" = '0'; test "$testcase_count" = '1'
  test "$all_testcases" = '1'; test "$failure_nodes" = '0'
  test "$system_out_count" = '1'; test "$system_err_count" = '0'
  require_no_credentials "$report_xml"
  test "$(/usr/bin/grep -Fxc 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' "$report_txt")" = '1'
  tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-report.XXXXXX)
  cleanup_report() {
    cleanup_status=$?
    trap - HUP INT TERM EXIT
    /bin/rm -f "$tmp/system-out" "$tmp/marker-lines" "$tmp/expected-marker"
    /bin/rmdir "$tmp"
    return "$cleanup_status"
  }
  trap cleanup_report EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM
  if /usr/bin/xmllint --xpath "string($suite/*[local-name()='testcase']/*[local-name()='system-out'])" "$report_xml" > "$tmp/system-out"; then
    system_out_status=0
  else
    system_out_status=$?
  fi
  test "$system_out_status" -eq 0
  marker_count=$(/usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{n++}END{print n+0}' "$tmp/system-out")
  test "$marker_count" = "$expected_marker_count"
)
assert_contracts_unconsumed() {
  for absent_path in \
    "$attempt" "$contracts_lock" "$freeze_lock" "$selection_lock" \
    "$success_archive" "$xml" "$txt" "$freeze_xml" "$freeze_txt" \
    "$selection_xml" "$selection_txt"
  do
    test ! -e "$absent_path"; test ! -L "$absent_path"
  done
}
verify_phase_tree() (
  phase_lock=$1; report_suffix=$2
  tree_tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-phase-tree.XXXXXX)
  cleanup_phase_tree() {
    cleanup_status=$?
    trap - HUP INT TERM EXIT
    /bin/rm -f "$tree_tmp/actual" "$tree_tmp/expected" "$tree_tmp/symlinks" "$tree_tmp/special"
    /bin/rmdir "$tree_tmp"
    return "$cleanup_status"
  }
  trap cleanup_phase_tree EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM
  if /usr/bin/find "$phase_lock" -mindepth 1 -print > "$tree_tmp/actual"; then
    find_status=0
  else
    find_status=$?
  fi
  test "$find_status" -eq 0
  printf '%s\n' \
    "$phase_lock/reports" \
    "$phase_lock/reports.sha256" \
    "$phase_lock/reports/TEST-$test_class-$report_suffix.xml" \
    "$phase_lock/reports/$test_class-$report_suffix.txt" > "$tree_tmp/expected"
  LC_ALL=C /usr/bin/sort -o "$tree_tmp/expected" "$tree_tmp/expected"
  LC_ALL=C /usr/bin/sort -o "$tree_tmp/actual" "$tree_tmp/actual"
  /usr/bin/cmp -s "$tree_tmp/expected" "$tree_tmp/actual"
  if /usr/bin/find "$phase_lock" -type l -print > "$tree_tmp/symlinks"; then symlink_status=0; else symlink_status=$?; fi
  if /usr/bin/find "$phase_lock" ! -type d ! -type f -print > "$tree_tmp/special"; then special_status=0; else special_status=$?; fi
  test "$symlink_status" -eq 0; test "$special_status" -eq 0
  test ! -s "$tree_tmp/symlinks"; test ! -s "$tree_tmp/special"
)
assert_contracts_unconsumed
test -f "$old_xml"; test ! -L "$old_xml"
test -f "$old_txt"; test ! -L "$old_txt"
old_xml_digest=$(/usr/bin/shasum -a 256 "$old_xml"); old_xml_sha=${old_xml_digest%% *}
old_txt_digest=$(/usr/bin/shasum -a 256 "$old_txt"); old_txt_sha=${old_txt_digest%% *}
test "$old_xml_sha" = '25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237'
test "$old_txt_sha" = '4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b'
test -d "$evidence"; test ! -L "$evidence"
assert_contracts_unconsumed

/bin/mkdir -m 0700 "$contracts_lock"
test -d "$contracts_lock"; test ! -L "$contracts_lock"
test "$(/usr/bin/stat -f '%Lp' "$contracts_lock")" = '700'
contracts_identity=$(/usr/bin/stat -f '%d:%i' "$contracts_lock")

env JAVA_HOME=/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home PATH=/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/rtk mvn -f /Users/achilles/Documents/许子祺/Agent/backend/pom.xml -pl tests -am '-Dtest=tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test#baselineContracts' -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.reportNameSuffix=candidate102a-evidence-baseline-v2-contracts-attempt-002 -Dmaven.resources.skip=true -Drag.eval.candidate10.evidence-baseline-v2=contracts -Drag.eval.candidate10.freeze=false -Drag.eval.candidate10.diagnostic=true -Drag.eval.candidate10.diagnostic-arm=true -Drag.eval.candidate10.environment-qualification=false -Drag.eval.shadow=false -Drag.eval.shadow.compare-stable=false -Drag.eval.identifier.diagnostic=false -Drag.eval.candidate2.diagnostic=false -Drag.eval.candidate3.diagnostic=false -Drag.eval.candidate4.diagnostic=false -Drag.eval.candidate5.diagnostic=false -Drag.eval.candidate6.diagnostic=false -Drag.eval.candidate8.diagnostic=false -Drag.eval.candidate9.diagnostic=false -Drag.eval.candidate9.recovery=false -Drag.eval.promotion=false -Drag.eval.live=false -Dqknow.rag.dynamic-top-k.enabled=false -Dqknow.rag.query-entity.enabled=false -Dqknow.rag.rrf.k=60 -Dqknow.rag.rrf.weak-path-threshold=0 -Dqknow.rag.graph.enabled=false -Dqknow.rag.vector.vecsim-rescore-enabled=false -Dqknow.rag.keyword.identifier-aware=false -Dqknow.rag.rerank.identifier-consistency-enabled=true -Dqknow.rag.local-reranker.enabled=false -Dqknow.rag.onnx-reranker.enabled=false -Dhermes.rag.colbert.enabled=true -Dhermes.rag.colbert.ngram-size=3 -Dhermes.rag.colbert.dimensions=64 -Dhermes.rag.colbert.max-tokens-per-doc=128 -Dhermes.rag.colbert.embedding-platform= -Dhermes.rag.colbert.embedding-base-url= -Dhermes.rag.colbert.embedding-api-key= -Dhermes.rag.colbert.embedding-model= -Dhermes.rag.context.max-bytes=20000 -Dhermes.rag.context.max-tokens=0 -DforkCount=1 -DreuseForks=false '-DargLine=-Dfile.encoding=UTF-8 -Duser.timezone=UTC -Duser.language=en -Duser.country=US -Duser.script= -Duser.variant= -Dqknow.native.lib.dir= -Djava.library.path=/Users/achilles/Documents/许子祺/Agent/backend/tests/target/rag-eval/no-native' test

snapshot=$(/usr/bin/mktemp -d /tmp/candidate102a-contracts-report.XXXXXX)
cleanup_snapshot() {
  cleanup_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$snapshot/${xml##*/}" "$snapshot/${txt##*/}"
  /bin/rmdir "$snapshot"
  return "$cleanup_status"
}
trap cleanup_snapshot EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
copy_create "$xml" "$snapshot/${xml##*/}"
copy_create "$txt" "$snapshot/${txt##*/}"
verify_report "$snapshot" baselineContracts "$suffix" 0
/bin/mkdir -m 0700 "$contracts_lock/reports"
test -d "$contracts_lock/reports"; test ! -L "$contracts_lock/reports"
copy_create "$snapshot/${xml##*/}" "$contracts_lock/reports/${xml##*/}"
copy_create "$snapshot/${txt##*/}" "$contracts_lock/reports/${txt##*/}"
verify_report "$contracts_lock/reports" baselineContracts "$suffix" 0
(cd "$contracts_lock"; set -C; /usr/bin/shasum -a 256 "reports/${xml##*/}" "reports/${txt##*/}" > reports.sha256)
verify_phase_tree "$contracts_lock" "$suffix"
/bin/chmod 0400 "$contracts_lock/reports.sha256" "$contracts_lock/reports/${xml##*/}" "$contracts_lock/reports/${txt##*/}"
/bin/chmod 0500 "$contracts_lock/reports" "$contracts_lock"
test -f "$contracts_lock/reports.sha256"; test ! -L "$contracts_lock/reports.sha256"
test "$(/usr/bin/stat -f '%Lp' "$contracts_lock/reports.sha256")" = '400'
test "$(/usr/bin/stat -f '%Lp' "$contracts_lock")" = '500'
test "$(/usr/bin/awk 'END{print NR+0}' "$contracts_lock/reports.sha256")" = '2'
test "$(/usr/bin/stat -f '%d:%i' "$contracts_lock")" = "$contracts_identity"
(cd "$contracts_lock"; /usr/bin/shasum -a 256 -c reports.sha256)
verify_phase_tree "$contracts_lock" "$suffix"
verify_report "$contracts_lock/reports" baselineContracts "$suffix" 0
SH
```

### F4. Freeze wrapper

```sh
/bin/sh -eu <<'SH'
umask 077
repo='/Users/achilles/Documents/许子祺/Agent'
attempt="$repo/backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2/attempt-002"
reports="$repo/backend/tests/target/surefire-reports"
evidence="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2"
contracts_lock="$evidence/.attempt-002.contracts-started"
freeze_lock="$evidence/.attempt-002.freeze-started"
selection_lock="$evidence/.attempt-002.selection-started"
success_archive="$evidence/attempt-002"
test_class='tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test'
suffix='candidate102a-evidence-baseline-v2-freeze-attempt-002'
xml="$reports/TEST-$test_class-$suffix.xml"
txt="$reports/$test_class-$suffix.txt"
selection_xml="$reports/TEST-$test_class-candidate102a-evidence-baseline-v2-selection-attempt-002.xml"
selection_txt="$reports/$test_class-candidate102a-evidence-baseline-v2-selection-attempt-002.txt"
copy_create() {
  source_file=$1; destination=$2
  test -f "$source_file"; test ! -L "$source_file"
  test ! -e "$destination"; test ! -L "$destination"
  (set -C; /bin/cat "$source_file" > "$destination")
  test -f "$destination"; test ! -L "$destination"
  /usr/bin/cmp -s "$source_file" "$destination"
}
xpath_value() { /usr/bin/xmllint --xpath "$1" "$2"; }
require_no_credentials() {
  report_xml=$1
  credential_xpath='count(//*[local-name()="property" and (contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api-key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api_key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"apikey") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"secret") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"password") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"credential"))])'
  allowed_xpath='count(//*[local-name()="property" and @name="hermes.rag.colbert.embedding-api-key" and @value=""])'
  credential_count=$(xpath_value "$credential_xpath" "$report_xml")
  allowed_count=$(xpath_value "$allowed_xpath" "$report_xml")
  test "$credential_count" = '1'; test "$allowed_count" = '1'
  if LC_ALL=C /usr/bin/grep -E -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|Bearer[[:space:]]+[A-Za-z0-9._~+/=-]{8,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}' "$report_xml" >/dev/null; then
    return 1
  else
    scan_status=$?
  fi
  test "$scan_status" -eq 1
}
verify_report() (
  report_root=$1; method=$2; report_suffix=$3; expected_marker_count=$4
  report_xml="$report_root/TEST-$test_class-$report_suffix.xml"
  report_txt="$report_root/$test_class-$report_suffix.txt"
  test -f "$report_xml"; test ! -L "$report_xml"
  test -f "$report_txt"; test ! -L "$report_txt"
  suite_nodes='(/*[local-name()="testsuite"] | /*[local-name()="testsuites"]/*[local-name()="testsuite"])'
  suite_count=$(xpath_value "count($suite_nodes)" "$report_xml")
  test "$suite_count" = '1'
  suite="($suite_nodes)[1]"
  tests_count=$(xpath_value "string($suite/@tests)" "$report_xml")
  failures_count=$(xpath_value "string($suite/@failures)" "$report_xml")
  errors_count=$(xpath_value "string($suite/@errors)" "$report_xml")
  skipped_count=$(xpath_value "string($suite/@skipped)" "$report_xml")
  testcase_count=$(xpath_value "count($suite/*[local-name()='testcase' and @name='$method' and @classname='$test_class($report_suffix)'])" "$report_xml")
  all_testcases=$(xpath_value "count($suite/*[local-name()='testcase'])" "$report_xml")
  failure_nodes=$(xpath_value "count($suite/*[local-name()='testcase']//*[local-name()='failure' or local-name()='error'])" "$report_xml")
  system_out_count=$(xpath_value "count($suite/*[local-name()='testcase']/*[local-name()='system-out'])" "$report_xml")
  system_err_count=$(xpath_value "count($suite/*[local-name()='testcase']/*[local-name()='system-err'])" "$report_xml")
  test "$tests_count" = '1'
  test "$failures_count" = '0'; test "$errors_count" = '0'
  test "$skipped_count" = '0'; test "$testcase_count" = '1'
  test "$all_testcases" = '1'; test "$failure_nodes" = '0'
  test "$system_out_count" = '1'; test "$system_err_count" = '0'
  require_no_credentials "$report_xml"
  txt_summary_count=$(/usr/bin/grep -Fxc 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' "$report_txt")
  test "$txt_summary_count" = '1'
  tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-report.XXXXXX)
  cleanup_report() {
    cleanup_status=$?
    trap - HUP INT TERM EXIT
    /bin/rm -f "$tmp/system-out" "$tmp/marker-lines" "$tmp/expected-marker"
    /bin/rmdir "$tmp"
    return "$cleanup_status"
  }
  trap cleanup_report EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM
  if /usr/bin/xmllint --xpath "string($suite/*[local-name()='testcase']/*[local-name()='system-out'])" "$report_xml" > "$tmp/system-out"; then
    system_out_status=0
  else
    system_out_status=$?
  fi
  test "$system_out_status" -eq 0
  marker_count=$(/usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{n++}END{print n+0}' "$tmp/system-out")
  test "$marker_count" = "$expected_marker_count"
)
verify_phase_tree() (
  phase_lock=$1; report_suffix=$2
  tree_tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-phase-tree.XXXXXX)
  cleanup_phase_tree() {
    cleanup_status=$?
    trap - HUP INT TERM EXIT
    /bin/rm -f "$tree_tmp/actual" "$tree_tmp/expected" "$tree_tmp/symlinks" "$tree_tmp/special"
    /bin/rmdir "$tree_tmp"
    return "$cleanup_status"
  }
  trap cleanup_phase_tree EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM
  if /usr/bin/find "$phase_lock" -mindepth 1 -print > "$tree_tmp/actual"; then find_status=0; else find_status=$?; fi
  test "$find_status" -eq 0
  printf '%s\n' \
    "$phase_lock/reports" "$phase_lock/reports.sha256" \
    "$phase_lock/reports/TEST-$test_class-$report_suffix.xml" \
    "$phase_lock/reports/$test_class-$report_suffix.txt" > "$tree_tmp/expected"
  LC_ALL=C /usr/bin/sort -o "$tree_tmp/expected" "$tree_tmp/expected"
  LC_ALL=C /usr/bin/sort -o "$tree_tmp/actual" "$tree_tmp/actual"
  /usr/bin/cmp -s "$tree_tmp/expected" "$tree_tmp/actual"
  if /usr/bin/find "$phase_lock" -type l -print > "$tree_tmp/symlinks"; then symlink_status=0; else symlink_status=$?; fi
  if /usr/bin/find "$phase_lock" ! -type d ! -type f -print > "$tree_tmp/special"; then special_status=0; else special_status=$?; fi
  test "$symlink_status" -eq 0; test "$special_status" -eq 0
  test ! -s "$tree_tmp/symlinks"; test ! -s "$tree_tmp/special"
)
verify_phase_lock() {
  phase_lock=$1; method=$2; report_suffix=$3
  report_xml="$phase_lock/reports/TEST-$test_class-$report_suffix.xml"
  report_txt="$phase_lock/reports/$test_class-$report_suffix.txt"
  test -d "$phase_lock"; test ! -L "$phase_lock"
  test "$(/usr/bin/stat -f '%Lp' "$phase_lock")" = '500'
  test -d "$phase_lock/reports"; test ! -L "$phase_lock/reports"
  test "$(/usr/bin/stat -f '%Lp' "$phase_lock/reports")" = '500'
  test -f "$phase_lock/reports.sha256"; test ! -L "$phase_lock/reports.sha256"
  test -f "$report_xml"; test ! -L "$report_xml"
  test -f "$report_txt"; test ! -L "$report_txt"
  test "$(/usr/bin/stat -f '%Lp' "$phase_lock/reports.sha256")" = '400'
  test "$(/usr/bin/awk 'END{print NR+0}' "$phase_lock/reports.sha256")" = '2'
  (cd "$phase_lock"; /usr/bin/shasum -a 256 -c reports.sha256)
  verify_phase_tree "$phase_lock" "$report_suffix"
  verify_report "$phase_lock/reports" "$method" "$report_suffix" 0
}
assert_freeze_unconsumed() {
  for absent_path in \
    "$attempt" "$freeze_lock" "$selection_lock" "$success_archive" \
    "$xml" "$txt" "$selection_xml" "$selection_txt"
  do
    test ! -e "$absent_path"; test ! -L "$absent_path"
  done
}
test -d "$evidence"; test ! -L "$evidence"
assert_freeze_unconsumed
verify_phase_lock "$contracts_lock" baselineContracts candidate102a-evidence-baseline-v2-contracts-attempt-002
assert_freeze_unconsumed

/bin/mkdir -m 0700 "$freeze_lock"
test -d "$freeze_lock"; test ! -L "$freeze_lock"
test "$(/usr/bin/stat -f '%Lp' "$freeze_lock")" = '700'
freeze_identity=$(/usr/bin/stat -f '%d:%i' "$freeze_lock")

env JAVA_HOME=/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home PATH=/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/rtk mvn -f /Users/achilles/Documents/许子祺/Agent/backend/pom.xml -pl tests -am '-Dtest=tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test#freezeCurrentEvidence' -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.reportNameSuffix=candidate102a-evidence-baseline-v2-freeze-attempt-002 -Dmaven.resources.skip=true -Drag.eval.candidate10.evidence-baseline-v2=freeze -Drag.eval.candidate10.freeze=false -Drag.eval.candidate10.diagnostic=true -Drag.eval.candidate10.diagnostic-arm=true -Drag.eval.candidate10.environment-qualification=false -Drag.eval.shadow=false -Drag.eval.shadow.compare-stable=false -Drag.eval.identifier.diagnostic=false -Drag.eval.candidate2.diagnostic=false -Drag.eval.candidate3.diagnostic=false -Drag.eval.candidate4.diagnostic=false -Drag.eval.candidate5.diagnostic=false -Drag.eval.candidate6.diagnostic=false -Drag.eval.candidate8.diagnostic=false -Drag.eval.candidate9.diagnostic=false -Drag.eval.candidate9.recovery=false -Drag.eval.promotion=false -Drag.eval.live=false -Dqknow.rag.dynamic-top-k.enabled=false -Dqknow.rag.query-entity.enabled=false -Dqknow.rag.rrf.k=60 -Dqknow.rag.rrf.weak-path-threshold=0 -Dqknow.rag.graph.enabled=false -Dqknow.rag.vector.vecsim-rescore-enabled=false -Dqknow.rag.keyword.identifier-aware=false -Dqknow.rag.rerank.identifier-consistency-enabled=true -Dqknow.rag.local-reranker.enabled=false -Dqknow.rag.onnx-reranker.enabled=false -Dhermes.rag.colbert.enabled=true -Dhermes.rag.colbert.ngram-size=3 -Dhermes.rag.colbert.dimensions=64 -Dhermes.rag.colbert.max-tokens-per-doc=128 -Dhermes.rag.colbert.embedding-platform= -Dhermes.rag.colbert.embedding-base-url= -Dhermes.rag.colbert.embedding-api-key= -Dhermes.rag.colbert.embedding-model= -Dhermes.rag.context.max-bytes=20000 -Dhermes.rag.context.max-tokens=0 -DforkCount=1 -DreuseForks=false '-DargLine=-Dfile.encoding=UTF-8 -Duser.timezone=UTC -Duser.language=en -Duser.country=US -Duser.script= -Duser.variant= -Dqknow.native.lib.dir= -Djava.library.path=/Users/achilles/Documents/许子祺/Agent/backend/tests/target/rag-eval/no-native' test

snapshot=$(/usr/bin/mktemp -d /tmp/candidate102a-freeze-report.XXXXXX)
cleanup_snapshot() {
  cleanup_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$snapshot/${xml##*/}" "$snapshot/${txt##*/}"
  /bin/rmdir "$snapshot"
  return "$cleanup_status"
}
trap cleanup_snapshot EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
copy_create "$xml" "$snapshot/${xml##*/}"
copy_create "$txt" "$snapshot/${txt##*/}"
verify_report "$snapshot" freezeCurrentEvidence "$suffix" 0
/bin/mkdir -m 0700 "$freeze_lock/reports"
test -d "$freeze_lock/reports"; test ! -L "$freeze_lock/reports"
copy_create "$snapshot/${xml##*/}" "$freeze_lock/reports/${xml##*/}"
copy_create "$snapshot/${txt##*/}" "$freeze_lock/reports/${txt##*/}"
verify_report "$freeze_lock/reports" freezeCurrentEvidence "$suffix" 0
for file_path in "$attempt/source-lock.json" "$attempt/commands.sh" "$attempt/selection/corpus.jsonl" "$attempt/selection/queries.jsonl" "$attempt/selection/pressure.json" "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json"; do test -f "$file_path"; test ! -L "$file_path"; done
test -x "$attempt/commands.sh"
test ! -e "$attempt/canonical-marker.json"; test ! -L "$attempt/canonical-marker.json"
test ! -e "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"; test ! -L "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"
(cd "$freeze_lock"; set -C; /usr/bin/shasum -a 256 "reports/${xml##*/}" "reports/${txt##*/}" > reports.sha256)
verify_phase_tree "$freeze_lock" "$suffix"
/bin/chmod 0400 "$freeze_lock/reports.sha256" "$freeze_lock/reports/${xml##*/}" "$freeze_lock/reports/${txt##*/}"
/bin/chmod 0500 "$freeze_lock/reports" "$freeze_lock"
test -f "$freeze_lock/reports.sha256"; test ! -L "$freeze_lock/reports.sha256"
test "$(/usr/bin/stat -f '%Lp' "$freeze_lock/reports.sha256")" = '400'
test "$(/usr/bin/stat -f '%Lp' "$freeze_lock")" = '500'
test "$(/usr/bin/awk 'END{print NR+0}' "$freeze_lock/reports.sha256")" = '2'
test "$(/usr/bin/stat -f '%d:%i' "$freeze_lock")" = "$freeze_identity"
(cd "$freeze_lock"; /usr/bin/shasum -a 256 -c reports.sha256)
verify_phase_lock "$contracts_lock" baselineContracts candidate102a-evidence-baseline-v2-contracts-attempt-002
verify_phase_lock "$freeze_lock" freezeCurrentEvidence "$suffix"
SH
```

### F5. Selection-once wrapper and archive

The Freeze-created `commands.sh` is canonical, source-locked, regular non-symlink, executable, and already contains the complete Selection Maven invocation, report preflight/postflight, full 25-path publishability preflight, marker comparison, and create-new archive routine. Execute it exactly once:

```sh
/bin/sh -eu <<'SH'
test "${ALLOW_SELECTION_ONCE-}" = 'true'
test "${CANDIDATE102A_SELECTION_ATTEMPT-}" = '002'
test "${ALLOW_SELECTION_QREL_AFTER_FREEZE_ONCE-}" = 'true'
export ALLOW_SELECTION_ONCE CANDIDATE102A_SELECTION_ATTEMPT \
  ALLOW_SELECTION_QREL_AFTER_FREEZE_ONCE
repo='/Users/achilles/Documents/许子祺/Agent'
attempt="$repo/backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2/attempt-002"
attempt_parent="${attempt%/attempt-002}"
commands="$attempt/commands.sh"
archive="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-002"
reports="$repo/backend/tests/target/surefire-reports"
lock="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2/.attempt-002.selection-started"
contracts_lock="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2/.attempt-002.contracts-started"
freeze_lock="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2/.attempt-002.freeze-started"
source_lock="$attempt/source-lock.json"
plan="$repo/plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md"
test_source="$repo/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java"
selection_authorization="$attempt_parent/.attempt-002-selection-authorization.json"
consumed_authorizations="$attempt_parent/.attempt-002-selection-authorizations-consumed"
test_class='tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test'
selection_xml="$repo/backend/tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml"
selection_txt="$repo/backend/tests/target/surefire-reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt"
verify_report() (
  report_root=$1; method=$2; report_suffix=$3; expected_marker_count=$4
  expected_marker_file=${5-}
  report_xml="$report_root/TEST-$test_class-$report_suffix.xml"
  report_txt="$report_root/$test_class-$report_suffix.txt"
  test -f "$report_xml"; test ! -L "$report_xml"
  test -f "$report_txt"; test ! -L "$report_txt"
  suite_nodes='(/*[local-name()="testsuite"] | /*[local-name()="testsuites"]/*[local-name()="testsuite"])'
  suite_count=$(/usr/bin/xmllint --xpath "count($suite_nodes)" "$report_xml")
  test "$suite_count" = '1'
  suite="($suite_nodes)[1]"
  tests_count=$(/usr/bin/xmllint --xpath "string($suite/@tests)" "$report_xml")
  failures_count=$(/usr/bin/xmllint --xpath "string($suite/@failures)" "$report_xml")
  errors_count=$(/usr/bin/xmllint --xpath "string($suite/@errors)" "$report_xml")
  skipped_count=$(/usr/bin/xmllint --xpath "string($suite/@skipped)" "$report_xml")
  testcase_count=$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase' and @name='$method' and @classname='$test_class($report_suffix)'])" "$report_xml")
  all_testcases=$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase'])" "$report_xml")
  failure_nodes=$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase']//*[local-name()='failure' or local-name()='error'])" "$report_xml")
  system_out_count=$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase']/*[local-name()='system-out'])" "$report_xml")
  system_err_count=$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase']/*[local-name()='system-err'])" "$report_xml")
  test "$tests_count" = '1'
  test "$failures_count" = '0'; test "$errors_count" = '0'
  test "$skipped_count" = '0'; test "$testcase_count" = '1'
  test "$all_testcases" = '1'; test "$failure_nodes" = '0'
  test "$system_err_count" = '0'
  credential_xpath='count(//*[local-name()="property" and (contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api-key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api_key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"apikey") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"secret") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"password") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"credential"))])'
  allowed_xpath='count(//*[local-name()="property" and @name="hermes.rag.colbert.embedding-api-key" and @value=""])'
  credential_count=$(/usr/bin/xmllint --xpath "$credential_xpath" "$report_xml")
  allowed_count=$(/usr/bin/xmllint --xpath "$allowed_xpath" "$report_xml")
  test "$credential_count" = '1'; test "$allowed_count" = '1'
  if LC_ALL=C /usr/bin/grep -E -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|Bearer[[:space:]]+[A-Za-z0-9._~+/=-]{8,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}' "$report_xml" >/dev/null; then
    return 1
  else
    scan_status=$?
  fi
  test "$scan_status" -eq 1
  txt_summary_count=$(/usr/bin/grep -Fxc 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' "$report_txt")
  test "$txt_summary_count" = '1'
  tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-report.XXXXXX)
  cleanup_report() {
    cleanup_status=$?
    trap - HUP INT TERM EXIT
    /bin/rm -f "$tmp/system-out" "$tmp/marker-lines" "$tmp/expected-marker"
    /bin/rmdir "$tmp"
    return "$cleanup_status"
  }
  trap cleanup_report EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM
  test "$system_out_count" = '1'
  if /usr/bin/xmllint --xpath "string($suite/*[local-name()='testcase']/*[local-name()='system-out'])" "$report_xml" > "$tmp/system-out"; then
    system_out_status=0
  else
    system_out_status=$?
  fi
  test "$system_out_status" -eq 0
  case "$expected_marker_count" in
    0)
      test -z "$expected_marker_file"
      ;;
    1)
      test -f "$expected_marker_file"; test ! -L "$expected_marker_file"
      marker_lines="$tmp/marker-lines"
      expected_marker="$tmp/expected-marker"
      if /usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{print}' "$tmp/system-out" > "$marker_lines"; then
        marker_extract_status=0
      else
        marker_extract_status=$?
      fi
      test "$marker_extract_status" -eq 0
      test "$(/usr/bin/awk 'END{print NR+0}' "$marker_lines")" = '1'
      { printf '%s ' 'CANDIDATE102A_EVIDENCE_BASELINE_V2'; /bin/cat "$expected_marker_file"; } > "$expected_marker"
      /usr/bin/cmp -s "$marker_lines" "$expected_marker"
      ;;
    *) return 64 ;;
  esac
  marker_count=$(/usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{n++}END{print n+0}' "$tmp/system-out")
  test "$marker_count" = "$expected_marker_count"
)
verify_phase_tree() (
  phase_lock=$1; report_suffix=$2
  tree_tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-phase-tree.XXXXXX)
  cleanup_phase_tree() {
    cleanup_status=$?
    trap - HUP INT TERM EXIT
    /bin/rm -f "$tree_tmp/actual" "$tree_tmp/expected" "$tree_tmp/symlinks" "$tree_tmp/special"
    /bin/rmdir "$tree_tmp"
    return "$cleanup_status"
  }
  trap cleanup_phase_tree EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM
  if /usr/bin/find "$phase_lock" -mindepth 1 -print > "$tree_tmp/actual"; then find_status=0; else find_status=$?; fi
  test "$find_status" -eq 0
  printf '%s\n' \
    "$phase_lock/reports" "$phase_lock/reports.sha256" \
    "$phase_lock/reports/TEST-$test_class-$report_suffix.xml" \
    "$phase_lock/reports/$test_class-$report_suffix.txt" > "$tree_tmp/expected"
  LC_ALL=C /usr/bin/sort -o "$tree_tmp/expected" "$tree_tmp/expected"
  LC_ALL=C /usr/bin/sort -o "$tree_tmp/actual" "$tree_tmp/actual"
  /usr/bin/cmp -s "$tree_tmp/expected" "$tree_tmp/actual"
  if /usr/bin/find "$phase_lock" -type l -print > "$tree_tmp/symlinks"; then symlink_status=0; else symlink_status=$?; fi
  if /usr/bin/find "$phase_lock" ! -type d ! -type f -print > "$tree_tmp/special"; then special_status=0; else special_status=$?; fi
  test "$symlink_status" -eq 0; test "$special_status" -eq 0
  test ! -s "$tree_tmp/symlinks"; test ! -s "$tree_tmp/special"
)
verify_phase_lock() {
  phase_lock=$1; method=$2; report_suffix=$3
  report_xml="$phase_lock/reports/TEST-$test_class-$report_suffix.xml"
  report_txt="$phase_lock/reports/$test_class-$report_suffix.txt"
  test -d "$phase_lock"; test ! -L "$phase_lock"
  test "$(/usr/bin/stat -f '%Lp' "$phase_lock")" = '500'
  test -d "$phase_lock/reports"; test ! -L "$phase_lock/reports"
  test "$(/usr/bin/stat -f '%Lp' "$phase_lock/reports")" = '500'
  test -f "$phase_lock/reports.sha256"; test ! -L "$phase_lock/reports.sha256"
  test -f "$report_xml"; test ! -L "$report_xml"
  test -f "$report_txt"; test ! -L "$report_txt"
  test "$(/usr/bin/stat -f '%Lp' "$phase_lock/reports.sha256")" = '400'
  test "$(/usr/bin/awk 'END{print NR+0}' "$phase_lock/reports.sha256")" = '2'
  (cd "$phase_lock"; /usr/bin/shasum -a 256 -c reports.sha256)
  verify_phase_tree "$phase_lock" "$report_suffix"
  verify_report "$phase_lock/reports" "$method" "$report_suffix" 0
}
verify_commands() {
  test -f "$source_lock"; test ! -L "$source_lock"
  test -f "$commands"; test ! -L "$commands"; test -x "$commands"
  test "$(/usr/bin/stat -f '%Lp' "$commands")" = '755'
  command_path=$(/usr/bin/jq -er '.commands.path' "$source_lock")
  command_size=$(/usr/bin/jq -er '.commands.sizeBytes' "$source_lock")
  command_sha=$(/usr/bin/jq -er '.commands.sha256' "$source_lock")
  test "$command_path" = 'commands.sh'
  actual_size=$(/usr/bin/stat -f '%z' "$commands")
  digest=$(/usr/bin/shasum -a 256 "$commands")
  digest=${digest%% *}
  test "$actual_size" = "$command_size"
  test "$digest" = "$command_sha"
}
preflight_selection_readonly() {
  verify_commands
  "$commands" selection-preflight
  verify_phase_lock "$contracts_lock" baselineContracts \
    candidate102a-evidence-baseline-v2-contracts-attempt-002
  verify_phase_lock "$freeze_lock" freezeCurrentEvidence \
    candidate102a-evidence-baseline-v2-freeze-attempt-002
  for absent_path in "$selection_xml" "$selection_txt" "$archive" "$lock"
  do
    test ! -e "$absent_path"; test ! -L "$absent_path"
  done
}
verify_authorization() {
  authorization_file=$1; expected_mode=$2
  test -f "$authorization_file"; test ! -L "$authorization_file"
  test "$(/usr/bin/stat -f '%Lp' "$authorization_file")" = "$expected_mode"
  test -f "$plan"; test ! -L "$plan"
  test -f "$test_source"; test ! -L "$test_source"
  test -f "$source_lock"; test ! -L "$source_lock"
  auth_tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-auth.XXXXXX)
  trap '/bin/rm -f "$auth_tmp/canonical"; /bin/rmdir "$auth_tmp"' EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM
  if /usr/bin/jq -cS . "$authorization_file" > "$auth_tmp/canonical"; then jq_status=0; else jq_status=$?; fi
  test "$jq_status" -eq 0
  /usr/bin/cmp -s "$authorization_file" "$auth_tmp/canonical"
  plan_sha=$(/usr/bin/shasum -a 256 "$plan"); plan_sha=${plan_sha%% *}
  source_lock_sha=$(/usr/bin/shasum -a 256 "$source_lock"); source_lock_sha=${source_lock_sha%% *}
  locked_test_sha=$(/usr/bin/jq -er \
    '[.sourceFiles[] | select(.path == "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java")] as $v | if ($v|length)==1 then $v[0].sha256 else error("test binding") end' \
    "$source_lock")
  actual_test_sha=$(/usr/bin/shasum -a 256 "$test_source"); actual_test_sha=${actual_test_sha%% *}
  test "$actual_test_sha" = "$locked_test_sha"
  /usr/bin/jq -e --arg plan "$plan_sha" --arg lock "$source_lock_sha" --arg test "$locked_test_sha" '
    (keys == ["allowSelectionOnce", "allowSelectionQrelAfterFreezeOnce",
              "attempt", "authorizationNonce", "namespace", "planSha256",
              "sourceLockSha256", "testSourceSha256"]) and
    .allowSelectionOnce == true and
    .allowSelectionQrelAfterFreezeOnce == true and
    .attempt == "002" and
    .namespace == "candidate102a-evidence-baseline-v2" and
    (.authorizationNonce | type == "string" and test("^[0-9a-f]{64}$")) and
    .planSha256 == $plan and .sourceLockSha256 == $lock and
    .testSourceSha256 == $test' "$authorization_file" >/dev/null
  authorization_nonce=$(/usr/bin/jq -er '.authorizationNonce' "$authorization_file")
  /bin/rm -f "$auth_tmp/canonical"; /bin/rmdir "$auth_tmp"
  trap - EXIT
}
consume_authorization() {
  verify_authorization "$selection_authorization" 600
  test ! -e "$consumed_authorizations"; test ! -L "$consumed_authorizations"
  /bin/mkdir -m 0700 "$consumed_authorizations"
  test -d "$consumed_authorizations"; test ! -L "$consumed_authorizations"
  test "$(/usr/bin/stat -f '%Lp' "$consumed_authorizations")" = '700'
  consumed_authorization="$consumed_authorizations/$authorization_nonce.json"
  test "$(/usr/bin/basename "$consumed_authorization")" = "$authorization_nonce.json"
  test ! -e "$consumed_authorization"; test ! -L "$consumed_authorization"
  /bin/ln "$selection_authorization" "$consumed_authorization"
  test -f "$consumed_authorization"; test ! -L "$consumed_authorization"
  test "$(/usr/bin/stat -f '%d:%i' "$selection_authorization")" = \
    "$(/usr/bin/stat -f '%d:%i' "$consumed_authorization")"
  /usr/bin/cmp -s "$selection_authorization" "$consumed_authorization"
  /bin/rm "$selection_authorization"
  test ! -e "$selection_authorization"; test ! -L "$selection_authorization"
  /bin/chmod 0400 "$consumed_authorization"
  verify_authorization "$consumed_authorization" 400
  CANDIDATE102A_SELECTION_AUTHORIZATION_RECEIPT=$consumed_authorization
  export CANDIDATE102A_SELECTION_AUTHORIZATION_RECEIPT
}
route_marker() {
  marker=$1
  source_lock_sha=$2
  /usr/bin/jq -er --arg sourceLockSha "$source_lock_sha" '
    def valid_top_keys:
      ["access","algorithmChange","algorithmConclusion","attempt","budgets",
       "causalScope","checkpointPresence","contextLineage","decision",
       "earliestBoundaryQueryCounts","errorCode","infrastructure","mapping",
       "namespace","observation","pathSnapshotSha256","predecessorStatus",
       "productionChange","queryCount","schemaVersion","selectedBoundary",
       "sourceLockSha256","status","targetQueryCount",
       "unresolvedTargetQueryCount"];
    def invalid_top_keys:
      ["access","algorithmChange","algorithmConclusion","attempt",
       "causalScope","decision","errorCode","infrastructure","namespace",
       "observation","predecessorStatus","productionChange","queryCount",
       "schemaVersion","selectedBoundary","sourceLockSha256","status",
       "targetQueryCount"];
    def fixed_base:
      .schemaVersion == "candidate102a-evidence-baseline-v2-v1" and
      .namespace == "candidate102a-evidence-baseline-v2" and
      .attempt == "002" and
      .predecessorStatus == "SUPERSEDED_UNRECOVERABLE" and
      .causalScope == "post-routing-single-retrieve-once-single-variant-counterfactual-rerank-orchestration-vecsim-disabled-colbert90-admission90-selection-v1" and
      .algorithmConclusion == "NOT_REACHED" and
      .productionChange == false and .algorithmChange == false;
    def fixed_error:
      . as $error |
      ["CANDIDATE102A_BASELINE_V2_SOURCE_LOCK_INVALID",
       "CANDIDATE102A_BASELINE_V2_COMMAND_INVALID",
       "CANDIDATE102A_BASELINE_V2_FROZEN_INPUT_INVALID",
       "CANDIDATE102A_BASELINE_V2_DATABASE_INVALID",
       "CANDIDATE102A_BASELINE_V2_INFRASTRUCTURE_ADAPTER_INVALID",
       "CANDIDATE102A_BASELINE_V2_EXECUTOR_INVALID",
       "CANDIDATE102A_BASELINE_V2_COUNTERFACTUAL_ORCHESTRATION_INVALID",
       "CANDIDATE102A_BASELINE_V2_FALLBACK_INVALID",
       "CANDIDATE102A_BASELINE_V2_CHECKPOINT_MAPPING_INVALID",
       "CANDIDATE102A_BASELINE_V2_FUSION_CONTRACT_INVALID",
       "CANDIDATE102A_BASELINE_V2_CONTEXT_CHECKPOINT_INVALID",
       "CANDIDATE102A_BASELINE_V2_BUDGET_INVALID",
       "CANDIDATE102A_BASELINE_V2_QREL_ACCESS_INVALID",
       "CANDIDATE102A_BASELINE_V2_SAFETY_INVALID",
       "CANDIDATE102A_BASELINE_V2_RUNTIME_INVALID",
       "CANDIDATE102A_BASELINE_V2_HARNESS_INVALID",
       "CANDIDATE102A_BASELINE_V2_WORKTREE_SNAPSHOT_INVALID"]
      | index($error) != null;
    def valid_counts:
      .queryCount == 40 and .targetQueryCount == 16 and
      (.unresolvedTargetQueryCount |
        type == "number" and floor == . and . >= 0 and . <= 16) and
      (.earliestBoundaryQueryCounts | type == "object") and
      (.earliestBoundaryQueryCounts | keys) ==
        ["COLBERT90_VISIBLE", "COLBERT_FRONTIER_NOT_PRESERVED",
         "FILTER_NOT_PRESERVED", "FUSION_NOT_PRESERVED",
         "NOT_RETRIEVED", "NO_RELEVANT_EXACT", "WEAK_PATH_EXCLUDED"] and
      ([.earliestBoundaryQueryCounts[] |
        type == "number" and floor == . and . >= 0 and . <= 16] | all) and
      (.earliestBoundaryQueryCounts.COLBERT90_VISIBLE
       + .unresolvedTargetQueryCount == 16) and
      .unresolvedTargetQueryCount ==
        (.earliestBoundaryQueryCounts.NO_RELEVANT_EXACT
         + .earliestBoundaryQueryCounts.NOT_RETRIEVED
         + .earliestBoundaryQueryCounts.WEAK_PATH_EXCLUDED
         + .earliestBoundaryQueryCounts.FUSION_NOT_PRESERVED
         + .earliestBoundaryQueryCounts.FILTER_NOT_PRESERVED
         + .earliestBoundaryQueryCounts.COLBERT_FRONTIER_NOT_PRESERVED);
    def matched_counts:
      valid_counts and .unresolvedTargetQueryCount == 4 and
      .earliestBoundaryQueryCounts.COLBERT90_VISIBLE == 12 and
      .earliestBoundaryQueryCounts.COLBERT_FRONTIER_NOT_PRESERVED == 4 and
      .earliestBoundaryQueryCounts.NO_RELEVANT_EXACT == 0 and
      .earliestBoundaryQueryCounts.NOT_RETRIEVED == 0 and
      .earliestBoundaryQueryCounts.WEAK_PATH_EXCLUDED == 0 and
      .earliestBoundaryQueryCounts.FUSION_NOT_PRESERVED == 0 and
      .earliestBoundaryQueryCounts.FILTER_NOT_PRESERVED == 0;
    def valid_evidence:
      .sourceLockSha256 == $sourceLockSha and
      .access == {
        selectionNonQrelResourceAccessCount:3,
        qrelResourceAccessBeforeRanking:0,
        qrelResourceAccessCount:1,
        holdoutPathOperationCount:0
      } and
      (.infrastructure | type == "object") and
      (.infrastructure | keys) == ["docker","executor","seed"] and
      (.infrastructure.executor | type == "object") and
      (.infrastructure.executor | keys) ==
        ["acceptedSubmitCount","activeBeforeQrelFreeze",
         "callableSubmitCount","cancelledFutureCount","completed",
         "completedTaskCount","corePoolSize","doneFutureCount","failed",
         "maxPoolSize","queueCapacity",
         "queueRemainingCapacityBeforeQrelFreeze","queuedBeforeQrelFreeze",
         "rejected","runnableSubmitCount","started","submitAttemptCount",
         "succeeded","taskCount","terminatedAfterCleanup"] and
      .infrastructure.executor.corePoolSize == 4 and
      .infrastructure.executor.maxPoolSize == 4 and
      .infrastructure.executor.queueCapacity == 32 and
      (.checkpointPresence | type == "object") and
      (.checkpointPresence | keys) ==
        ["admissionOutput30", "candidate3Sources", "colbertInput",
         "colbertTop90", "contextRendered", "fused", "graphRaw",
         "keywordRaw", "metadataRaw", "postFilter", "retrieverUnion",
         "vectorRaw", "weakPathEligibleUnion"] and
      ([.checkpointPresence[] |
        type == "boolean" and . == true] | all) and
      .infrastructure.executor.submitAttemptCount == 200 and
      .infrastructure.executor.acceptedSubmitCount == 200 and
      .infrastructure.executor.callableSubmitCount == 200 and
      .infrastructure.executor.runnableSubmitCount == 0 and
      .infrastructure.executor.started == 200 and
      .infrastructure.executor.succeeded == 200 and
      .infrastructure.executor.completed == 200 and
      .infrastructure.executor.failed == 0 and
      .infrastructure.executor.rejected == 0 and
      .infrastructure.executor.doneFutureCount == 200 and
      .infrastructure.executor.cancelledFutureCount == 0 and
      .infrastructure.executor.taskCount == 200 and
      .infrastructure.executor.completedTaskCount == 200 and
      .infrastructure.executor.activeBeforeQrelFreeze == 0 and
      .infrastructure.executor.queuedBeforeQrelFreeze == 0 and
      .infrastructure.executor.queueRemainingCapacityBeforeQrelFreeze == 32 and
      .infrastructure.executor.terminatedAfterCleanup == true;
    fixed_base and
    (
     ((keys == invalid_top_keys) and .status == "INVALID" and
      (.queryCount | type == "number" and floor == . and . >= 0 and . <= 40) and
      (.targetQueryCount | type == "number" and floor == . and . >= 0 and . <= 16) and
      (.access | type == "object") and
      (.access | keys) == ["holdoutPathOperationCount","qrelResourceAccessBeforeRanking","qrelResourceAccessCount","selectionNonQrelResourceAccessCount"] and
      ([.access[] | type == "number" and floor == . and . >= 0] | all) and
      (.infrastructure | type == "object") and
      (.infrastructure | keys) == ["docker","executor","seed"] and
      (.sourceLockSha256 == null or
       (.sourceLockSha256 | type == "string" and test("^[0-9a-f]{64}$"))) and
      .observation == null and
      .selectedBoundary == null and .decision == null and
      (.errorCode | fixed_error)) or
     ((keys == valid_top_keys) and .status == "VALID" and
      .observation == "DIVERGED_FROM_12_4" and
      valid_counts and valid_evidence and (matched_counts | not) and
      .selectedBoundary == null and
      .decision == "STOP_CANDIDATE102A_SEAM_BASELINE_DIVERGED" and
      .errorCode == null) or
     ((keys == valid_top_keys) and .status == "VALID" and
      matched_counts and valid_evidence and
      .observation == "MATCHED_12_VISIBLE_4_FRONTIER_MISSING" and
      .selectedBoundary == "COLBERT_FRONTIER_PRESERVATION" and
      .decision == "PROCEED_TO_CANDIDATE102A_SEAM_PLAN_REVIEW" and
      .errorCode == null)
    )' "$marker" >/dev/null
  /usr/bin/jq -r 'if .status == "INVALID" then "INVALID" elif .observation == "DIVERGED_FROM_12_4" then "DIVERGED" else "MATCHED" end' "$marker"
}
preflight_selection_readonly
commands_identity=$(/usr/bin/stat -f '%d:%i' "$commands")
consume_authorization
verify_authorization "$CANDIDATE102A_SELECTION_AUTHORIZATION_RECEIPT" 400
preflight_selection_readonly
test "$(/usr/bin/stat -f '%d:%i' "$commands")" = "$commands_identity"
test "$ALLOW_SELECTION_ONCE" = 'true'
test "$CANDIDATE102A_SELECTION_ATTEMPT" = '002'
test "$ALLOW_SELECTION_QREL_AFTER_FREEZE_ONCE" = 'true'
verify_authorization "$CANDIDATE102A_SELECTION_AUTHORIZATION_RECEIPT" 400
"$commands" selection
test -d "$archive"; test ! -L "$archive"
test -d "$lock"; test ! -L "$lock"
test "$(/usr/bin/stat -f '%Lp' "$lock")" = '700'
test -f "$lock/authorization.json"; test ! -L "$lock/authorization.json"
test "$(/usr/bin/stat -f '%Lp' "$lock/authorization.json")" = '400'
/usr/bin/cmp -s "$CANDIDATE102A_SELECTION_AUTHORIZATION_RECEIPT" "$lock/authorization.json"
lock_tree=$(/usr/bin/mktemp /tmp/candidate102a-selection-lock.XXXXXX)
cleanup_lock_tree() {
  cleanup_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$lock_tree"
  return "$cleanup_status"
}
trap cleanup_lock_tree EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
if /usr/bin/find "$lock" -mindepth 1 -print > "$lock_tree"; then lock_find_status=0; else lock_find_status=$?; fi
test "$lock_find_status" -eq 0
test "$(/usr/bin/awk 'END{print NR+0}' "$lock_tree")" = '1'
test "$(/bin/cat "$lock_tree")" = "$lock/authorization.json"
/bin/rm -f "$lock_tree"
trap - HUP INT TERM EXIT
verify_phase_lock "$contracts_lock" baselineContracts candidate102a-evidence-baseline-v2-contracts-attempt-002
verify_phase_lock "$freeze_lock" freezeCurrentEvidence candidate102a-evidence-baseline-v2-freeze-attempt-002
test -x "$archive/commands.sh"
test -f "$archive/source-lock.json"; test ! -L "$archive/source-lock.json"
/usr/bin/cmp -s "$commands" "$archive/commands.sh"
archived_command_sha=$(/usr/bin/shasum -a 256 "$archive/commands.sh")
archived_command_sha=${archived_command_sha%% *}
test "$archived_command_sha" = "$command_sha"
tree_tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-archive-tree.XXXXXX)
cleanup_tree() {
  cleanup_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$tree_tmp/files" "$tree_tmp/symlinks" "$tree_tmp/special" \
    "$tree_tmp/manifest.expected" "$tree_tmp/manifest.actual"
  /bin/rmdir "$tree_tmp"
  return "$cleanup_status"
}
trap cleanup_tree EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
/bin/cat > "$tree_tmp/manifest.expected" <<'MANIFEST'
./canonical-marker.json
./commands.sh
./execution-plan.md
./reactor-inputs/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java
./reactor-inputs/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java
./reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml
./reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml
./reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml
./reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt
./reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt
./reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt
./selection/corpus.jsonl
./selection/pressure.json
./selection/queries.jsonl
./source-lock.json
./worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json
./worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json
MANIFEST
LC_ALL=C /usr/bin/sort -o "$tree_tmp/manifest.expected" \
  "$tree_tmp/manifest.expected"
manifest_line_count=$(/usr/bin/awk 'END{print NR+0}' "$archive/SHA256SUMS")
test "$manifest_line_count" = '17'
/usr/bin/awk '
  NF == 2 && length($1) == 64 && $1 !~ /[^0-9a-f]/ \
    && index($2, "./") == 1 { print $2 }
' "$archive/SHA256SUMS" > "$tree_tmp/manifest.actual"
test "$(/usr/bin/awk 'END{print NR+0}' "$tree_tmp/manifest.actual")" = '17'
LC_ALL=C /usr/bin/sort -o "$tree_tmp/manifest.actual" \
  "$tree_tmp/manifest.actual"
/usr/bin/cmp -s "$tree_tmp/manifest.expected" "$tree_tmp/manifest.actual"
/usr/bin/find "$archive" -type f -print > "$tree_tmp/files"
/usr/bin/find "$archive" -type l -print > "$tree_tmp/symlinks"
/usr/bin/find "$archive" ! -type d ! -type f ! -type l -print > "$tree_tmp/special"
test ! -s "$tree_tmp/symlinks"; test ! -s "$tree_tmp/special"
archive_file_count=$(/usr/bin/awk 'END{print NR+0}' "$tree_tmp/files")
test "$archive_file_count" = '18'
(cd "$archive"; /usr/bin/shasum -a 256 -c SHA256SUMS; test -f execution-plan.md; test ! -L execution-plan.md)
/usr/bin/cmp -s "$contracts_lock/reports/TEST-$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml" "$archive/reports/TEST-$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml"
/usr/bin/cmp -s "$contracts_lock/reports/$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt" "$archive/reports/$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt"
/usr/bin/cmp -s "$freeze_lock/reports/TEST-$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml" "$archive/reports/TEST-$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml"
/usr/bin/cmp -s "$freeze_lock/reports/$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt" "$archive/reports/$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt"
verify_report "$archive/reports" baselineContracts candidate102a-evidence-baseline-v2-contracts-attempt-002 0
verify_report "$archive/reports" freezeCurrentEvidence candidate102a-evidence-baseline-v2-freeze-attempt-002 0
verify_report "$archive/reports" establishSelectionBaseline candidate102a-evidence-baseline-v2-selection-attempt-002 1 "$archive/canonical-marker.json"
archived_source_lock_digest=$(/usr/bin/shasum -a 256 "$archive/source-lock.json")
archived_source_lock_sha=${archived_source_lock_digest%% *}
printf '%s\n' "$archived_source_lock_sha" | /usr/bin/grep -Eq '^[0-9a-f]{64}$'
route=$(route_marker "$archive/canonical-marker.json" "$archived_source_lock_sha")
case "$route" in INVALID|DIVERGED|MATCHED) : ;; *) exit 1 ;; esac
test -d "$lock"; test ! -L "$lock"
verify_phase_lock "$contracts_lock" baselineContracts candidate102a-evidence-baseline-v2-contracts-attempt-002
verify_phase_lock "$freeze_lock" freezeCurrentEvidence candidate102a-evidence-baseline-v2-freeze-attempt-002
SH
```

The code path remains:

```text
loadRankingInput once
-> seed
-> 40 queries / 200 Futures
-> full global Budget traversal
-> full global Orchestration traversal
-> executor active=0 and queue=0
-> all 13 ranking checkpoints frozen
-> qrel-before-ranking=0
-> loadQrelsAfterRanking once
-> qrel access=1
-> evaluate
-> cleanup and executor termination
-> after-selection snapshot
-> source/JDK/worktree recheck
-> one marker
```

`holdoutPathOperationCount` remains `0`, and no Selection qrel or Holdout raw bytes are saved.

`selectionNonQrelResourceAccessCount` and `qrelResourceAccessCount` are
increment-before-invocation counters at V2-owned loader boundaries, not
operating-system filesystem monitors. `qrelResourceAccessBeforeRanking` is a
snapshot of the live qrel counter taken at the ranking-freeze boundary.
`holdoutPathOperationCount` is explicitly a bounded code-path counter, not an
operating-system filesystem monitor. The only qrel boundary is the pinned-HEAD,
source-bound in-memory `RagCandidate10FixtureGenerator.qrels(Split)` method; it
does not construct or read a path. The Holdout=0 claim is the conjunction of
its fixed SHA, one direct V2 `qrels(Split.SELECTION)` call, the locked
`Split.HOLDOUT` token count `0`, zero reflection/path/resource-stream/legacy
loader calls in `loadQrelsAfterRanking`, the absence of any Holdout path
construction or broad resource exclusion, and the fixed forbidden-entry
contract. The marker serializes that bounded proof as `0`; no broader OS claim
is made.
The qrel claim is the locked single
`RagCandidate10FixtureGenerator.qrels(...)` call inside
`loadQrelsAfterRanking`, its placement after `rankingFrozen`, and the observed
counter transition `0 -> 1`. No broader monitoring claim is made.

### F6. Attempt-002 archive contract

The successful attempt-002 archive contains exactly 18 regular files: 17 payload files plus `SHA256SUMS`; no symlinks or special entries. Its 17 checksum-covered files are:

```text
canonical-marker.json
commands.sh
execution-plan.md
reactor-inputs/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java
reactor-inputs/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java
reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml
reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml
reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml
reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt
reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt
reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt
selection/corpus.jsonl
selection/pressure.json
selection/queries.jsonl
source-lock.json
worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json
worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json
```

`SHA256SUMS` has exactly 17 unique records. Each record has a 64-character
lowercase hexadecimal digest and a `./`-prefixed path; after stripping no
characters and sorting under `LC_ALL=C`, its path column must equal the above
list with `./` prepended byte-for-byte. File count plus successful
`shasum -a 256 -c` is insufficient without this allowlist comparison; F5 and
F7 both run the same coverage check.

### F7. Exact staging and one local commit

The staged set is exactly 25 paths: the test source, six failed-attempt provenance files, and eighteen attempt-002 archive files. Before staging, the index must be empty; every path must be a regular non-symlink. `attempt-002/commands.sh` must be index mode `100755`, stage `0`; every other path must be mode `100644`, stage `0`. `core.filemode` must be `true`.

The NUL-sorted 25-path whitelist SHA256 must equal:

```text
2798b552f729f0dd4e2186ccb72322f4ca06aac86745135b8906c2855575326a
```

Stage each explicit path with one `index_git add -- "${paths[@]}"` against the
exclusively reserved `.git/index.lock`; never stage a directory or write the
real index before the ref transaction. The wrapper first copies the verified
real index to a unique same-directory seed, hard-links that seed create-only to
`.git/index.lock`, and retains the seed through `git add`. After Git has
atomically replaced the private index, the wrapper hard-links the resulting
index to a unique same-directory owner path, proves device/inode equality, and
only then removes the seed. Cleanup removes the private index only when the
branch is still at the frozen base and the owner path still has the private
index's device/inode; it never deletes an unprovable auxiliary Git lock.
Compare
`index_git diff --cached --name-only -z`, after NUL-safe `LC_ALL=C sort -z`,
byte-for-byte with the expected list. Verify all index modes/stages using
`index_git ls-files -s -z` parsed as NUL records.

F7 has no terminal-failure file or recovery state machine. Its EXIT trap only
removes children of one private temporary directory and, when the branch is
still at the frozen base and device/inode ownership remains provable, releases
the index lock reserved by this invocation. The private directory is created
once and the status-preserving EXIT/HUP/INT/TERM cleanup is armed before any
comparison child or Git-directory lookup. A failed staging or
commit leaves the already verified archive and any create-only phase locks in
place and stops; it never invokes Selection replay.

Execute the staging and commit contract exactly as follows:

```sh
/bin/zsh -eu <<'ZSH'
set -o pipefail
umask 077
repo='/Users/achilles/Documents/许子祺/Agent'
base='5c84bb044352a3fef684a37f70cc08ac80058c7b'
branch='codex/candidate102a-evidence-baseline-v2'
test_rel='backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java'
test_backend_rel='tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java'
colbert_rel='qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java'
embedding_rel='qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java'
rag_checker_rel='qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java'
fixture_generator_rel='tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate10FixtureGenerator.java'
failed='backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-001-failed-contracts'
archive='backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-002'
source_lock="$repo/$archive/source-lock.json"
plan='plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md'
evidence_root="$repo/backend/tests/evidence/candidate102a-evidence-baseline-v2"
reports="$repo/backend/tests/target/surefire-reports"
runtime_attempt="$repo/backend/tests/target/rag-eval/candidate102a-evidence-baseline-v2/attempt-002"
contracts_lock="$evidence_root/.attempt-002.contracts-started"
freeze_lock="$evidence_root/.attempt-002.freeze-started"
selection_lock="$evidence_root/.attempt-002.selection-started"
old_snapshot='/Users/achilles/.codex/evidence-worktree-snapshots/candidate102a-evidence-baseline-v2-attempt-001-before.json'
old_xml="$repo/backend/tests/target/surefire-reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.xml"
old_txt="$repo/backend/tests/target/surefire-reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.txt"

paths=(
  "$test_rel"
  "$failed/SHA256SUMS"
  "$failed/failure.json"
  "$failed/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.xml"
  "$failed/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.txt"
  "$failed/source/RagCandidate102AEvidenceBaselineV2Test.java"
  "$failed/worktree/candidate102a-evidence-baseline-v2-attempt-001-before.json"
  "$archive/SHA256SUMS"
  "$archive/canonical-marker.json"
  "$archive/commands.sh"
  "$archive/execution-plan.md"
  "$archive/reactor-inputs/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java"
  "$archive/reactor-inputs/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java"
  "$archive/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml"
  "$archive/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml"
  "$archive/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml"
  "$archive/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt"
  "$archive/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt"
  "$archive/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt"
  "$archive/selection/corpus.jsonl"
  "$archive/selection/pressure.json"
  "$archive/selection/queries.jsonl"
  "$archive/source-lock.json"
  "$archive/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"
  "$archive/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json"
)

dirty_paths=(
  '23030327许子祺/.officecli/config.json'
  '23030327许子祺/1-第2周报.docx'
  'backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java'
  'backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java'
  'frontend/package-lock.json'
  'scripts/start.sh'
)
dirty_xys=(' D' ' M' ' M' ' M' ' M' ' M')
dirty_types=('MISSING' 'REGULAR' 'REGULAR' 'REGULAR' 'REGULAR' 'REGULAR')
dirty_shas=(
  ''
  '3c1a18239854fcbb1cbc54a7b70eb42e6b4afdc633afb6e8b38b13b64fdb03f8'
  '75746407754cdfcf350960bd9587831bb7e2a76df4ba0ef2d46c1a81ea8a2e7f'
  '898d815973fcaedf9dd7bd0a0f73bd6d18431c8ccd4c4b5b385c08a0ed561ffd'
  'fd6650108cdadbcf0e23a9f0471eac4aab987a230f2091b40a7a439e2c5a509a'
  '26436484ad4c1306cfd108611addfd09fe8025d067e93b39be5d01e5f3de0f59'
)
dirty_index_modes=('100644' '100644' '100644' '100644' '100644' '100755')
dirty_worktree_modes=('' '644' '644' '644' '644' '755')
dirty_oids=(
  '2691755a674e11fd6cda9dd68b6c4424d4a94396'
  '0ab3503ba6aace7c8acf91b5b122b9295320c94a'
  'bc373b9f8e6ab28a1b3eef34ff393e7872bcf818'
  'dafe815f87982c4b472244746d2975aebc9f3734'
  '6468d92396b862beab0ef63955a94c46bf39d684'
  '019704d9041adfa66059a50f6d01c8017b43ee16'
)

tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-commit.XXXXXX)
expected="$tmp/expected"
actual="$tmp/actual"
index_expected="$tmp/index-expected"
index_actual="$tmp/index-actual"
tree_expected="$tmp/tree-expected"
tree_actual="$tmp/tree-actual"
tree_raw="$tmp/tree-raw"
dirty_expected="$tmp/dirty-expected"
dirty_actual="$tmp/dirty-actual"
dirty_expected_sorted="$tmp/dirty-expected-sorted"
dirty_actual_sorted="$tmp/dirty-actual-sorted"
manifest_expected="$tmp/manifest-expected"
manifest_actual="$tmp/manifest-actual"
committed_marker="$tmp/committed-marker"
tracked_paths="$tmp/tracked-paths"
cleanup_bootstrap() {
  bootstrap_status=$?
  trap - HUP INT TERM EXIT
  /bin/rm -f "$expected" "$actual" "$index_expected" "$index_actual" \
    "$tree_expected" "$tree_actual" "$tree_raw" "$committed_marker" \
    "$dirty_expected" "$dirty_actual" "$dirty_expected_sorted" \
    "$dirty_actual_sorted" "$manifest_expected" "$manifest_actual" \
    "$tracked_paths"
  /bin/rmdir "$tmp"
  return "$bootstrap_status"
}
trap cleanup_bootstrap EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
git_dir=$(/usr/bin/git -C "$repo" rev-parse --absolute-git-dir)
index_path="$git_dir/index"
index_lock="$git_dir/index.lock"
index_aux_lock="$git_dir/index.lock.lock"
index_seed="$git_dir/candidate102a-index-seed.$$"
index_owner="$git_dir/candidate102a-index-owner.$$"
index_reserved=0
index_installed=0
route_marker() {
  marker=$1
  source_lock_sha=$2
  /usr/bin/jq -er --arg sourceLockSha "$source_lock_sha" '
    def valid_top_keys:
      ["access","algorithmChange","algorithmConclusion","attempt","budgets",
       "causalScope","checkpointPresence","contextLineage","decision",
       "earliestBoundaryQueryCounts","errorCode","infrastructure","mapping",
       "namespace","observation","pathSnapshotSha256","predecessorStatus",
       "productionChange","queryCount","schemaVersion","selectedBoundary",
       "sourceLockSha256","status","targetQueryCount",
       "unresolvedTargetQueryCount"];
    def invalid_top_keys:
      ["access","algorithmChange","algorithmConclusion","attempt",
       "causalScope","decision","errorCode","infrastructure","namespace",
       "observation","predecessorStatus","productionChange","queryCount",
       "schemaVersion","selectedBoundary","sourceLockSha256","status",
       "targetQueryCount"];
    def fixed_base:
      .schemaVersion == "candidate102a-evidence-baseline-v2-v1" and
      .namespace == "candidate102a-evidence-baseline-v2" and
      .attempt == "002" and
      .predecessorStatus == "SUPERSEDED_UNRECOVERABLE" and
      .causalScope == "post-routing-single-retrieve-once-single-variant-counterfactual-rerank-orchestration-vecsim-disabled-colbert90-admission90-selection-v1" and
      .algorithmConclusion == "NOT_REACHED" and
      .productionChange == false and .algorithmChange == false;
    def fixed_error:
      . as $error |
      ["CANDIDATE102A_BASELINE_V2_SOURCE_LOCK_INVALID",
       "CANDIDATE102A_BASELINE_V2_COMMAND_INVALID",
       "CANDIDATE102A_BASELINE_V2_FROZEN_INPUT_INVALID",
       "CANDIDATE102A_BASELINE_V2_DATABASE_INVALID",
       "CANDIDATE102A_BASELINE_V2_INFRASTRUCTURE_ADAPTER_INVALID",
       "CANDIDATE102A_BASELINE_V2_EXECUTOR_INVALID",
       "CANDIDATE102A_BASELINE_V2_COUNTERFACTUAL_ORCHESTRATION_INVALID",
       "CANDIDATE102A_BASELINE_V2_FALLBACK_INVALID",
       "CANDIDATE102A_BASELINE_V2_CHECKPOINT_MAPPING_INVALID",
       "CANDIDATE102A_BASELINE_V2_FUSION_CONTRACT_INVALID",
       "CANDIDATE102A_BASELINE_V2_CONTEXT_CHECKPOINT_INVALID",
       "CANDIDATE102A_BASELINE_V2_BUDGET_INVALID",
       "CANDIDATE102A_BASELINE_V2_QREL_ACCESS_INVALID",
       "CANDIDATE102A_BASELINE_V2_SAFETY_INVALID",
       "CANDIDATE102A_BASELINE_V2_RUNTIME_INVALID",
       "CANDIDATE102A_BASELINE_V2_HARNESS_INVALID",
       "CANDIDATE102A_BASELINE_V2_WORKTREE_SNAPSHOT_INVALID"]
      | index($error) != null;
    def valid_counts:
      .queryCount == 40 and .targetQueryCount == 16 and
      (.unresolvedTargetQueryCount |
        type == "number" and floor == . and . >= 0 and . <= 16) and
      (.earliestBoundaryQueryCounts | type == "object") and
      (.earliestBoundaryQueryCounts | keys) ==
        ["COLBERT90_VISIBLE", "COLBERT_FRONTIER_NOT_PRESERVED",
         "FILTER_NOT_PRESERVED", "FUSION_NOT_PRESERVED",
         "NOT_RETRIEVED", "NO_RELEVANT_EXACT", "WEAK_PATH_EXCLUDED"] and
      ([.earliestBoundaryQueryCounts[] |
        type == "number" and floor == . and . >= 0 and . <= 16] | all) and
      (.earliestBoundaryQueryCounts.COLBERT90_VISIBLE
       + .unresolvedTargetQueryCount == 16) and
      .unresolvedTargetQueryCount ==
        (.earliestBoundaryQueryCounts.NO_RELEVANT_EXACT
         + .earliestBoundaryQueryCounts.NOT_RETRIEVED
         + .earliestBoundaryQueryCounts.WEAK_PATH_EXCLUDED
         + .earliestBoundaryQueryCounts.FUSION_NOT_PRESERVED
         + .earliestBoundaryQueryCounts.FILTER_NOT_PRESERVED
         + .earliestBoundaryQueryCounts.COLBERT_FRONTIER_NOT_PRESERVED);
    def matched_counts:
      valid_counts and .unresolvedTargetQueryCount == 4 and
      .earliestBoundaryQueryCounts.COLBERT90_VISIBLE == 12 and
      .earliestBoundaryQueryCounts.COLBERT_FRONTIER_NOT_PRESERVED == 4 and
      .earliestBoundaryQueryCounts.NO_RELEVANT_EXACT == 0 and
      .earliestBoundaryQueryCounts.NOT_RETRIEVED == 0 and
      .earliestBoundaryQueryCounts.WEAK_PATH_EXCLUDED == 0 and
      .earliestBoundaryQueryCounts.FUSION_NOT_PRESERVED == 0 and
      .earliestBoundaryQueryCounts.FILTER_NOT_PRESERVED == 0;
    def valid_evidence:
      .sourceLockSha256 == $sourceLockSha and
      .access == {
        selectionNonQrelResourceAccessCount:3,
        qrelResourceAccessBeforeRanking:0,
        qrelResourceAccessCount:1,
        holdoutPathOperationCount:0
      } and
      (.infrastructure | type == "object") and
      (.infrastructure | keys) == ["docker","executor","seed"] and
      (.infrastructure.executor | type == "object") and
      (.infrastructure.executor | keys) ==
        ["acceptedSubmitCount","activeBeforeQrelFreeze",
         "callableSubmitCount","cancelledFutureCount","completed",
         "completedTaskCount","corePoolSize","doneFutureCount","failed",
         "maxPoolSize","queueCapacity",
         "queueRemainingCapacityBeforeQrelFreeze","queuedBeforeQrelFreeze",
         "rejected","runnableSubmitCount","started","submitAttemptCount",
         "succeeded","taskCount","terminatedAfterCleanup"] and
      .infrastructure.executor.corePoolSize == 4 and
      .infrastructure.executor.maxPoolSize == 4 and
      .infrastructure.executor.queueCapacity == 32 and
      (.checkpointPresence | type == "object") and
      (.checkpointPresence | keys) ==
        ["admissionOutput30", "candidate3Sources", "colbertInput",
         "colbertTop90", "contextRendered", "fused", "graphRaw",
         "keywordRaw", "metadataRaw", "postFilter", "retrieverUnion",
         "vectorRaw", "weakPathEligibleUnion"] and
      ([.checkpointPresence[] |
        type == "boolean" and . == true] | all) and
      .infrastructure.executor.submitAttemptCount == 200 and
      .infrastructure.executor.acceptedSubmitCount == 200 and
      .infrastructure.executor.callableSubmitCount == 200 and
      .infrastructure.executor.runnableSubmitCount == 0 and
      .infrastructure.executor.started == 200 and
      .infrastructure.executor.succeeded == 200 and
      .infrastructure.executor.completed == 200 and
      .infrastructure.executor.failed == 0 and
      .infrastructure.executor.rejected == 0 and
      .infrastructure.executor.doneFutureCount == 200 and
      .infrastructure.executor.cancelledFutureCount == 0 and
      .infrastructure.executor.taskCount == 200 and
      .infrastructure.executor.completedTaskCount == 200 and
      .infrastructure.executor.activeBeforeQrelFreeze == 0 and
      .infrastructure.executor.queuedBeforeQrelFreeze == 0 and
      .infrastructure.executor.queueRemainingCapacityBeforeQrelFreeze == 32 and
      .infrastructure.executor.terminatedAfterCleanup == true;
    fixed_base and
    (
     ((keys == invalid_top_keys) and .status == "INVALID" and
      (.queryCount | type == "number" and floor == . and . >= 0 and . <= 40) and
      (.targetQueryCount | type == "number" and floor == . and . >= 0 and . <= 16) and
      (.access | type == "object") and
      (.access | keys) == ["holdoutPathOperationCount","qrelResourceAccessBeforeRanking","qrelResourceAccessCount","selectionNonQrelResourceAccessCount"] and
      ([.access[] | type == "number" and floor == . and . >= 0] | all) and
      (.infrastructure | type == "object") and
      (.infrastructure | keys) == ["docker","executor","seed"] and
      (.sourceLockSha256 == null or
       (.sourceLockSha256 | type == "string" and test("^[0-9a-f]{64}$"))) and
      .observation == null and
      .selectedBoundary == null and .decision == null and
      (.errorCode | fixed_error)) or
     ((keys == valid_top_keys) and .status == "VALID" and .observation == "DIVERGED_FROM_12_4" and
      valid_counts and valid_evidence and (matched_counts | not) and
      .selectedBoundary == null and
      .decision == "STOP_CANDIDATE102A_SEAM_BASELINE_DIVERGED" and
      .errorCode == null) or
     ((keys == valid_top_keys) and .status == "VALID" and
      matched_counts and valid_evidence and
      .observation == "MATCHED_12_VISIBLE_4_FRONTIER_MISSING" and
      .selectedBoundary == "COLBERT_FRONTIER_PRESERVATION" and
      .decision == "PROCEED_TO_CANDIDATE102A_SEAM_PLAN_REVIEW" and
      .errorCode == null)
    )' "$marker" >/dev/null
  /usr/bin/jq -r 'if .status == "INVALID" then "INVALID" elif .observation == "DIVERGED_FROM_12_4" then "DIVERGED" else "MATCHED" end' "$marker"
}
require_no_credentials() {
  report_xml=$1
  test -f "$report_xml"; test ! -L "$report_xml"
  credential_xpath='count(//*[local-name()="property" and (contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api-key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api_key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"apikey") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"secret") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"password") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"credential"))])'
  allowed_xpath='count(//*[local-name()="property" and @name="hermes.rag.colbert.embedding-api-key" and @value=""])'
  credential_count=$(/usr/bin/xmllint --xpath "$credential_xpath" "$report_xml")
  allowed_count=$(/usr/bin/xmllint --xpath "$allowed_xpath" "$report_xml")
  test "$credential_count" = '1'; test "$allowed_count" = '1'
  if LC_ALL=C /usr/bin/grep -E -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|Bearer[[:space:]]+[A-Za-z0-9._~+/=-]{8,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}' "$report_xml" >/dev/null; then
    return 1
  else
    scan_status=$?
  fi
  test "$scan_status" -eq 1
}
index_git() {
  GIT_INDEX_FILE="$index_lock" /usr/bin/git -C "$repo" "$@"
}
cleanup() {
  exit_code=$?
  trap - HUP INT TERM EXIT
  branch_oid=$(/usr/bin/git -C "$repo" rev-parse "refs/heads/$branch" 2>/dev/null || true)
  if test -e "$index_seed" || test -L "$index_seed"; then
    if test -e "$index_lock" || test -L "$index_lock"; then
      seed_identity=$(/usr/bin/stat -f '%d:%i' "$index_seed" 2>/dev/null || true)
      lock_identity=$(/usr/bin/stat -f '%d:%i' "$index_lock" 2>/dev/null || true)
      if test "$index_reserved" -eq 0 && test -n "$seed_identity" && \
         test "$seed_identity" = "$lock_identity"; then
        /bin/rm -f "$index_lock"
      fi
    fi
    /bin/rm -f "$index_seed"
  fi
  if test "$index_reserved" -eq 1 && test "$index_installed" -eq 0; then
    owner_identity=$(/usr/bin/stat -f '%d:%i' "$index_owner" 2>/dev/null || true)
    lock_identity=$(/usr/bin/stat -f '%d:%i' "$index_lock" 2>/dev/null || true)
    if test "$branch_oid" = "$base" && test -n "$owner_identity" && \
       test "$owner_identity" = "$lock_identity"; then
      /bin/rm -f "$index_lock" "$index_owner"
    else
      printf '%s\n' "ERROR: prepared index ownership not provable; retained at $index_lock" >&2
    fi
  fi
  if test "$index_installed" -eq 1 && \
     (test -e "$index_owner" || test -L "$index_owner"); then
    owner_identity=$(/usr/bin/stat -f '%d:%i' "$index_owner" 2>/dev/null || true)
    installed_identity=$(/usr/bin/stat -f '%d:%i' "$index_path" 2>/dev/null || true)
    if test -n "$owner_identity" && test "$owner_identity" = "$installed_identity"; then
      /bin/rm -f "$index_owner"
    else
      printf '%s\n' "ERROR: installed index ownership not provable; owner link retained at $index_owner" >&2
    fi
  fi
  if test -e "$index_aux_lock" || test -L "$index_aux_lock"; then
    printf '%s\n' "ERROR: auxiliary Git lock retained because ownership is not provable: $index_aux_lock" >&2
  fi
  /bin/rm -f "$expected" "$actual" "$index_expected" "$index_actual" "$tree_expected" "$tree_actual" \
    "$tree_raw" "$committed_marker" \
    "$dirty_expected" "$dirty_actual" "$dirty_expected_sorted" \
    "$dirty_actual_sorted" "$manifest_expected" "$manifest_actual" \
    "$tracked_paths"
  /bin/rmdir "$tmp"
  return "$exit_code"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

verify_user_worktree() {
  /usr/bin/git -C "$repo" status --porcelain=v1 -z \
    --untracked-files=all --no-renames -- . \
    ':(exclude)backend/tests/target/**' \
    ':(exclude)backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java' \
    ':(exclude)backend/tests/evidence/candidate102a-evidence-baseline-v2/**' \
    ':(exclude)plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md' \
    > "$dirty_actual"
  for index in {1..6}; do
    printf '%s %s\0' "$dirty_xys[$index]" "$dirty_paths[$index]"
  done > "$dirty_expected"
  LC_ALL=C /usr/bin/sort -z "$dirty_actual" > "$dirty_actual_sorted"
  LC_ALL=C /usr/bin/sort -z "$dirty_expected" > "$dirty_expected_sorted"
  /usr/bin/cmp -s "$dirty_expected_sorted" "$dirty_actual_sorted"
  for index in {1..6}; do
    file_path="$repo/$dirty_paths[$index]"
    if test "$dirty_types[$index]" = 'MISSING'; then
      test ! -e "$file_path"; test ! -L "$file_path"
    else
      test -f "$file_path"; test ! -L "$file_path"
      test "$(/usr/bin/stat -f '%Lp' "$file_path")" = "$dirty_worktree_modes[$index]"
      test "$(/usr/bin/shasum -a 256 "$file_path" | /usr/bin/awk '{print $1}')" = "$dirty_shas[$index]"
    fi
    printf '%s %s 0\t%s\0' \
      "$dirty_index_modes[$index]" "$dirty_oids[$index]" \
      "$dirty_paths[$index]" > "$dirty_expected"
    /usr/bin/git -C "$repo" ls-files -s -z -- \
      "$dirty_paths[$index]" > "$dirty_actual"
    /usr/bin/cmp -s "$dirty_expected" "$dirty_actual"
  done
}

verify_source_bindings() {
  test -f "$repo/$plan"; test ! -L "$repo/$plan"
  test -f "$repo/$archive/execution-plan.md"; test ! -L "$repo/$archive/execution-plan.md"
  /usr/bin/cmp -s "$repo/$plan" "$repo/$archive/execution-plan.md"
  for source_file in \
    "$runtime_attempt/commands.sh" \
    "$runtime_attempt/source-lock.json" \
    "$runtime_attempt/canonical-marker.json" \
    "$runtime_attempt/selection/corpus.jsonl" \
    "$runtime_attempt/selection/pressure.json" \
    "$runtime_attempt/selection/queries.jsonl" \
    "$runtime_attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json" \
    "$runtime_attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json" \
    "$contracts_lock/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml" \
    "$contracts_lock/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt" \
    "$freeze_lock/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml" \
    "$freeze_lock/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt" \
    "$reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml" \
    "$reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt"
  do
    test -f "$source_file"; test ! -L "$source_file"
  done
  /usr/bin/cmp -s "$runtime_attempt/commands.sh" "$repo/$archive/commands.sh"
  /usr/bin/cmp -s "$runtime_attempt/source-lock.json" "$repo/$archive/source-lock.json"
  /usr/bin/cmp -s "$runtime_attempt/canonical-marker.json" "$repo/$archive/canonical-marker.json"
  /usr/bin/cmp -s "$runtime_attempt/selection/corpus.jsonl" "$repo/$archive/selection/corpus.jsonl"
  /usr/bin/cmp -s "$runtime_attempt/selection/pressure.json" "$repo/$archive/selection/pressure.json"
  /usr/bin/cmp -s "$runtime_attempt/selection/queries.jsonl" "$repo/$archive/selection/queries.jsonl"
  /usr/bin/cmp -s "$runtime_attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json" "$repo/$archive/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json"
  /usr/bin/cmp -s "$runtime_attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json" "$repo/$archive/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"
  /usr/bin/cmp -s "$contracts_lock/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml" "$repo/$archive/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml"
  /usr/bin/cmp -s "$contracts_lock/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt" "$repo/$archive/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt"
  /usr/bin/cmp -s "$freeze_lock/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml" "$repo/$archive/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml"
  /usr/bin/cmp -s "$freeze_lock/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt" "$repo/$archive/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt"
  /usr/bin/cmp -s "$reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml" "$repo/$archive/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml"
  /usr/bin/cmp -s "$reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt" "$repo/$archive/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt"
  /usr/bin/cmp -s "$repo/backend/$embedding_rel" "$repo/$archive/reactor-inputs/$embedding_rel"
  /usr/bin/cmp -s "$repo/backend/$rag_checker_rel" "$repo/$archive/reactor-inputs/$rag_checker_rel"
  test -f "$old_snapshot"; test ! -L "$old_snapshot"
  test -f "$old_xml"; test ! -L "$old_xml"
  test -f "$old_txt"; test ! -L "$old_txt"
  test "$(/usr/bin/shasum -a 256 "$old_snapshot" | /usr/bin/awk '{print $1}')" = '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
  test "$(/usr/bin/shasum -a 256 "$old_xml" | /usr/bin/awk '{print $1}')" = '25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237'
  test "$(/usr/bin/shasum -a 256 "$old_txt" | /usr/bin/awk '{print $1}')" = '4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b'
  test "$(/usr/bin/shasum -a 256 "$repo/$failed/SHA256SUMS" | /usr/bin/awk '{print $1}')" = '23776a38b6cf76eca90c57ea9a53798d7eb44baab643d1c65a60ce00426adc41'
  test "$(/usr/bin/shasum -a 256 "$repo/$failed/failure.json" | /usr/bin/awk '{print $1}')" = '740e7da82e26658ff74d163fddeb910633c355768c2a5d5733e519bae8aa2ae1'
  test "$(/usr/bin/shasum -a 256 "$repo/$failed/source/RagCandidate102AEvidenceBaselineV2Test.java" | /usr/bin/awk '{print $1}')" = 'ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3'
  test "$(/usr/bin/shasum -a 256 "$repo/$failed/worktree/candidate102a-evidence-baseline-v2-attempt-001-before.json" | /usr/bin/awk '{print $1}')" = '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
  test "$(/usr/bin/shasum -a 256 "$repo/$failed/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.xml" | /usr/bin/awk '{print $1}')" = '25586c5853e9f320c03bc0a0ec135d9741f197bd0c8bced9755fa0ce9912b237'
  test "$(/usr/bin/shasum -a 256 "$repo/$failed/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-001.txt" | /usr/bin/awk '{print $1}')" = '4a4bb2c12c0cb8b42545bbff8a0b2308dce80c44dab286feb93502d3e4c4617b'
  actual_test_sha=$(/usr/bin/shasum -a 256 "$repo/$test_rel" | /usr/bin/awk '{print $1}')
  binding_count=$(/usr/bin/jq -er --arg path "$test_backend_rel" '[.sourceFiles[] | select(.path == $path)] | length' "$source_lock")
  locked_test_sha=$(/usr/bin/jq -er --arg path "$test_backend_rel" '.sourceFiles[] | select(.path == $path) | .sha256' "$source_lock")
  test "$binding_count" = '1'
  test "$actual_test_sha" = "$locked_test_sha"
  test "$actual_test_sha" != 'ae840d58b73bf2984cc1581dc2a6caef60409e1d261e1e24b38b63bd5dbaafc3'
  for binding in \
    "$colbert_rel:bf8d340ef6e591a8471e374d03706fbe82bfb58c4d36cc7e58d15c5580569c6e" \
    "$embedding_rel:75746407754cdfcf350960bd9587831bb7e2a76df4ba0ef2d46c1a81ea8a2e7f" \
    "$rag_checker_rel:898d815973fcaedf9dd7bd0a0f73bd6d18431c8ccd4c4b5b385c08a0ed561ffd" \
    "$fixture_generator_rel:8262d8bf5ad65330e0052119bea910cdd4af81e45a450b8238bc499cfd307a6e"
  do
    relative=${binding%%:*}
    expected_sha=${binding##*:}
    test "$(/usr/bin/jq -er --arg path "$relative" '[.sourceFiles[] | select(.path == $path)] | length' "$source_lock")" = '1'
    test "$(/usr/bin/jq -er --arg path "$relative" '.sourceFiles[] | select(.path == $path) | .sha256' "$source_lock")" = "$expected_sha"
    test "$(/usr/bin/shasum -a 256 "$repo/backend/$relative" | /usr/bin/awk '{print $1}')" = "$expected_sha"
  done
}

verify_index_entries() {
  : > "$tree_expected"
  for file_path in "${paths[@]}"; do
    index_oid=$(index_git rev-parse ":$file_path")
    worktree_oid=$(/usr/bin/git -C "$repo" hash-object -- "$repo/$file_path")
    test "$index_oid" = "$worktree_oid"
    if test "$file_path" = "$archive/commands.sh"; then
      mode='100755'
    else
      mode='100644'
    fi
    printf '%s %s 0\t%s\0' "$mode" "$index_oid" "$file_path" > "$index_expected"
    index_git ls-files -s -z -- "$file_path" > "$index_actual"
    /usr/bin/cmp -s "$index_expected" "$index_actual"
    printf '%s %s\t%s\0' "$mode" "$index_oid" "$file_path" >> "$tree_expected"
  done
  LC_ALL=C /usr/bin/sort -z -o "$tree_expected" "$tree_expected"
}

verify_fixed_tree() {
  directory=$1; expected_files=$2
  /usr/bin/find "$directory" -type l -print > "$actual"
  /usr/bin/find "$directory" ! -type d ! -type f ! -type l -print > "$index_actual"
  /usr/bin/find "$directory" -type f -print > "$dirty_actual"
  test ! -s "$actual"; test ! -s "$index_actual"
  regular_count=$(/usr/bin/awk 'END{print NR+0}' "$dirty_actual")
  test "$regular_count" = "$expected_files"
}

verify_archive_manifest() {
  directory=$1
  /bin/cat > "$manifest_expected" <<'MANIFEST'
./canonical-marker.json
./commands.sh
./execution-plan.md
./reactor-inputs/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java
./reactor-inputs/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java
./reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml
./reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml
./reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml
./reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt
./reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt
./reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt
./selection/corpus.jsonl
./selection/pressure.json
./selection/queries.jsonl
./source-lock.json
./worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json
./worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json
MANIFEST
  LC_ALL=C /usr/bin/sort -o "$manifest_expected" "$manifest_expected"
  test "$(/usr/bin/awk 'END{print NR+0}' "$directory/SHA256SUMS")" = '17'
  /usr/bin/awk '
    NF == 2 && length($1) == 64 && $1 !~ /[^0-9a-f]/ \
      && index($2, "./") == 1 { print $2 }
  ' "$directory/SHA256SUMS" > "$manifest_actual"
  test "$(/usr/bin/awk 'END{print NR+0}' "$manifest_actual")" = '17'
  LC_ALL=C /usr/bin/sort -o "$manifest_actual" "$manifest_actual"
  /usr/bin/cmp -s "$manifest_expected" "$manifest_actual"
  (cd "$directory"; /usr/bin/shasum -a 256 -c SHA256SUMS)
}
verify_publishability() {
  if /usr/bin/git -C "$repo" ls-files -z -- "${paths[@]}" > "$tracked_paths"; then
    tracked_status=0
  else
    tracked_status=$?
  fi
  test "$tracked_status" -eq 0
  test ! -s "$tracked_paths"
  if /usr/bin/git -C "$repo" check-ignore -q --no-index -- "${paths[@]}"; then
    ignore_status=0
  else
    ignore_status=$?
  fi
  test "$ignore_status" -eq 1
}

test "${#paths[@]}" -eq 25
test "$(/usr/bin/git -C "$repo" rev-parse HEAD)" = "$base"
test "$(/usr/bin/git -C "$repo" symbolic-ref --quiet --short HEAD)" = "$branch"
test "$(/usr/bin/git -C "$repo" config --bool core.filemode)" = 'true'
test -f "$repo/$plan"; test ! -L "$repo/$plan"
test -f "$repo/$test_rel"; test ! -L "$repo/$test_rel"
test -f "$source_lock"; test ! -L "$source_lock"
test -d "$repo/$failed"; test ! -L "$repo/$failed"
test -d "$repo/$archive"; test ! -L "$repo/$archive"
test -d "$git_dir"; test ! -L "$git_dir"
test -f "$index_path"; test ! -L "$index_path"
test ! -e "$index_lock"; test ! -L "$index_lock"
test ! -e "$index_aux_lock"; test ! -L "$index_aux_lock"
for sealed_spec in \
  "$contracts_lock|candidate102a-evidence-baseline-v2-contracts-attempt-002" \
  "$freeze_lock|candidate102a-evidence-baseline-v2-freeze-attempt-002"
do
  sealed_lock=${sealed_spec%%|*}
  sealed_suffix=${sealed_spec#*|}
  test -d "$sealed_lock"; test ! -L "$sealed_lock"
  test "$(/usr/bin/stat -f '%Lp' "$sealed_lock")" = '500'
  test -d "$sealed_lock/reports"; test ! -L "$sealed_lock/reports"
  test "$(/usr/bin/stat -f '%Lp' "$sealed_lock/reports")" = '500'
  test -f "$sealed_lock/reports.sha256"; test ! -L "$sealed_lock/reports.sha256"
  test "$(/usr/bin/stat -f '%Lp' "$sealed_lock/reports.sha256")" = '400'
  (cd "$sealed_lock"; /usr/bin/shasum -a 256 -c reports.sha256)
  if /usr/bin/find "$sealed_lock" -mindepth 1 -print > "$actual"; then sealed_tree_status=0; else sealed_tree_status=$?; fi
  test "$sealed_tree_status" -eq 0
  printf '%s\n' \
    "$sealed_lock/reports" \
    "$sealed_lock/reports.sha256" \
    "$sealed_lock/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-$sealed_suffix.xml" \
    "$sealed_lock/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-$sealed_suffix.txt" \
    | LC_ALL=C /usr/bin/sort > "$manifest_expected"
  LC_ALL=C /usr/bin/sort -o "$actual" "$actual"
  /usr/bin/cmp -s "$manifest_expected" "$actual"
done
test -d "$selection_lock"; test ! -L "$selection_lock"
test "$(/usr/bin/stat -f '%Lp' "$selection_lock")" = '700'
test -f "$selection_lock/authorization.json"; test ! -L "$selection_lock/authorization.json"
test "$(/usr/bin/stat -f '%Lp' "$selection_lock/authorization.json")" = '400'
if /usr/bin/find "$selection_lock" -mindepth 1 -print > "$actual"; then selection_tree_status=0; else selection_tree_status=$?; fi
test "$selection_tree_status" -eq 0
test "$(/usr/bin/awk 'END{print NR+0}' "$actual")" = '1'
test "$(/bin/cat "$actual")" = "$selection_lock/authorization.json"
selection_plan_sha=$(/usr/bin/shasum -a 256 "$repo/$plan"); selection_plan_sha=${selection_plan_sha%% *}
selection_source_lock_sha=$(/usr/bin/shasum -a 256 "$source_lock"); selection_source_lock_sha=${selection_source_lock_sha%% *}
selection_test_sha=$(/usr/bin/shasum -a 256 "$repo/$test_rel"); selection_test_sha=${selection_test_sha%% *}
/usr/bin/jq -e --arg plan "$selection_plan_sha" --arg lock "$selection_source_lock_sha" --arg test "$selection_test_sha" '
  (keys == ["allowSelectionOnce", "allowSelectionQrelAfterFreezeOnce",
            "attempt", "authorizationNonce", "namespace", "planSha256",
            "sourceLockSha256", "testSourceSha256"]) and
  .allowSelectionOnce == true and .allowSelectionQrelAfterFreezeOnce == true and
  .attempt == "002" and .namespace == "candidate102a-evidence-baseline-v2" and
  (.authorizationNonce | type == "string" and test("^[0-9a-f]{64}$")) and
  .planSha256 == $plan and .sourceLockSha256 == $lock and
  .testSourceSha256 == $test' "$selection_lock/authorization.json" >/dev/null
verify_fixed_tree "$repo/$failed" 6
(cd "$repo/$failed"; /usr/bin/shasum -a 256 -c SHA256SUMS)
verify_fixed_tree "$repo/$archive" 18
verify_archive_manifest "$repo/$archive"
(for report_xml in "$repo/$archive/reports/"TEST-*.xml; do
  require_no_credentials "$report_xml"
done)
(cd "$repo/$archive"; test -x commands.sh; test -f execution-plan.md; test ! -L execution-plan.md)
archive_manifest_digest=$(/usr/bin/shasum -a 256 "$repo/$archive/SHA256SUMS")
archive_manifest_sha=${archive_manifest_digest%% *}
printf '%s\n' "$archive_manifest_sha" | /usr/bin/grep -Eq '^[0-9a-f]{64}$'

for file_path in "${paths[@]}"; do
  test -f "$repo/$file_path"; test ! -L "$repo/$file_path"
done

verify_user_worktree
verify_source_bindings
verify_publishability
locked_source_digest=$(/usr/bin/shasum -a 256 "$repo/$archive/source-lock.json")
locked_source_sha=${locked_source_digest%% *}
printf '%s\n' "$locked_source_sha" | /usr/bin/grep -Eq '^[0-9a-f]{64}$'
result_route=$(route_marker "$repo/$archive/canonical-marker.json" "$locked_source_sha")

/usr/bin/git -C "$repo" diff --cached --quiet --
printf '%s\0' "${paths[@]}" | LC_ALL=C /usr/bin/sort -z > "$expected"
test "$(/usr/bin/shasum -a 256 "$expected" | /usr/bin/awk '{print $1}')" = '2798b552f729f0dd4e2186ccb72322f4ca06aac86745135b8906c2855575326a'

index_digest=$(/usr/bin/shasum -a 256 "$index_path")
index_sha=${index_digest%% *}
test ! -e "$index_seed"; test ! -L "$index_seed"
test ! -e "$index_owner"; test ! -L "$index_owner"
(set -C; /bin/cat "$index_path" > "$index_seed")
test -f "$index_seed"; test ! -L "$index_seed"
test "$(/usr/bin/stat -f '%Lp' "$index_seed")" = '600'
/bin/ln "$index_seed" "$index_lock"
test -f "$index_lock"; test ! -L "$index_lock"
test "$(/usr/bin/stat -f '%d:%i' "$index_seed")" = "$(/usr/bin/stat -f '%d:%i' "$index_lock")"
test "$(/usr/bin/stat -f '%Lp' "$index_lock")" = '600'
/usr/bin/cmp -s "$index_path" "$index_lock"
test "$(/usr/bin/shasum -a 256 "$index_path" | /usr/bin/awk '{print $1}')" = "$index_sha"

index_git add -- "${paths[@]}"
test ! -e "$index_aux_lock"; test ! -L "$index_aux_lock"
/bin/ln "$index_lock" "$index_owner"
test -f "$index_owner"; test ! -L "$index_owner"
test "$(/usr/bin/stat -f '%d:%i' "$index_owner")" = "$(/usr/bin/stat -f '%d:%i' "$index_lock")"
index_reserved=1
/bin/rm -f "$index_seed"
test ! -e "$index_seed"; test ! -L "$index_seed"
index_git diff --cached --name-only -z > "$actual"
LC_ALL=C /usr/bin/sort -z -o "$actual" "$actual"
/usr/bin/cmp -s "$expected" "$actual"

verify_index_entries
index_git diff --cached --check
index_git diff --cached --stat
index_git diff --cached --summary
index_git diff --cached --no-ext-diff -- "$test_rel"
index_git diff --cached --name-only --diff-filter=U -z > "$actual"
test ! -s "$actual"

verify_user_worktree
verify_source_bindings
verify_index_entries
verify_fixed_tree "$repo/$archive" 18
verify_archive_manifest "$repo/$archive"
verify_publishability
/usr/bin/git -C "$repo" rev-parse HEAD | /usr/bin/grep -Fx "$base" >/dev/null
test "$(/usr/bin/git -C "$repo" symbolic-ref --quiet --short HEAD)" = "$branch"
test "$(/usr/bin/shasum -a 256 "$index_path" | /usr/bin/awk '{print $1}')" = "$index_sha"
index_git diff --cached --check
index_git diff --cached --name-only -z > "$actual"
LC_ALL=C /usr/bin/sort -z -o "$actual" "$actual"
/usr/bin/cmp -s "$expected" "$actual"

tree=$(index_git write-tree)
commit=$(/usr/bin/printf '%s\n\n%s\n%s\n' \
  'test(rag): 建立 Candidate 10.2A 证据基线' \
  '- 保留 attempt-001 Contracts 后置校验失败证据' \
  '- 固化 attempt-002 单次 Selection 生命周期与校验和' | \
  /usr/bin/git -C "$repo" -c commit.gpgsign=false commit-tree \
    "$tree" -p "$base" -F -)
printf '%s\n' "$commit" | /usr/bin/grep -Eq '^[0-9a-f]{40}$'
branch_ref="refs/heads/$branch"
{
  printf 'start\n'
  printf 'option no-deref\n'
  printf 'symref-verify HEAD %s\n' "$branch_ref"
  printf 'update %s %s %s\n' "$branch_ref" "$commit" "$base"
  printf 'prepare\ncommit\n'
} | /usr/bin/git -C "$repo" update-ref --stdin
/bin/mv "$index_lock" "$index_path"
test "$(/usr/bin/stat -f '%d:%i' "$index_owner")" = "$(/usr/bin/stat -f '%d:%i' "$index_path")"
index_installed=1
/bin/rm -f "$index_owner"
test ! -e "$index_owner"; test ! -L "$index_owner"
test ! -e "$index_lock"; test ! -L "$index_lock"
test ! -e "$index_aux_lock"; test ! -L "$index_aux_lock"

test "$(/usr/bin/git -C "$repo" symbolic-ref --quiet HEAD)" = "$branch_ref"
test "$(/usr/bin/git -C "$repo" rev-parse HEAD)" = "$commit"
test "$commit" != "$base"
test "$(/usr/bin/git -C "$repo" show -s --format=%P "$commit")" = "$base"
test "$(/usr/bin/git -C "$repo" rev-list --count "$base..$commit")" = '1'
/usr/bin/git -C "$repo" diff --cached --quiet --
/usr/bin/git -C "$repo" diff-tree --no-commit-id --name-only -r -z "$commit" > "$actual"
LC_ALL=C /usr/bin/sort -z -o "$actual" "$actual"
/usr/bin/cmp -s "$expected" "$actual"
/usr/bin/git -C "$repo" ls-tree -rz "$commit" -- "${paths[@]}" > "$tree_raw"
: > "$tree_actual"
while IFS= read -r -d '' entry; do
  metadata=${entry%%$'\t'*}
  tree_path=${entry#*$'\t'}
  tree_mode=${metadata%% *}
  metadata=${metadata#* }
  tree_oid=${metadata##* }
  printf '%s %s\t%s\0' "$tree_mode" "$tree_oid" "$tree_path" >> "$tree_actual"
done < "$tree_raw"
LC_ALL=C /usr/bin/sort -z -o "$tree_actual" "$tree_actual"
/usr/bin/cmp -s "$tree_expected" "$tree_actual"
verify_user_worktree
verify_source_bindings
verify_fixed_tree "$repo/$failed" 6
(cd "$repo/$failed"; /usr/bin/shasum -a 256 -c SHA256SUMS)
verify_fixed_tree "$repo/$archive" 18
verify_archive_manifest "$repo/$archive"
(for report_xml in "$repo/$archive/reports/"TEST-*.xml; do
  require_no_credentials "$report_xml"
done)
(cd "$repo/$archive"; test -x commands.sh; test -f execution-plan.md; test ! -L execution-plan.md)
/usr/bin/cmp -s "$repo/$plan" "$repo/$archive/execution-plan.md"
for sealed_spec in \
  "$contracts_lock|candidate102a-evidence-baseline-v2-contracts-attempt-002" \
  "$freeze_lock|candidate102a-evidence-baseline-v2-freeze-attempt-002"
do
  sealed_lock=${sealed_spec%%|*}
  sealed_suffix=${sealed_spec#*|}
  test -d "$sealed_lock"; test ! -L "$sealed_lock"
  test "$(/usr/bin/stat -f '%Lp' "$sealed_lock")" = '500'
  test -d "$sealed_lock/reports"; test ! -L "$sealed_lock/reports"
  test "$(/usr/bin/stat -f '%Lp' "$sealed_lock/reports")" = '500'
  test -f "$sealed_lock/reports.sha256"; test ! -L "$sealed_lock/reports.sha256"
  test "$(/usr/bin/stat -f '%Lp' "$sealed_lock/reports.sha256")" = '400'
  (cd "$sealed_lock"; /usr/bin/shasum -a 256 -c reports.sha256)
  for report_leaf in \
    "TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-$sealed_suffix.xml" \
    "tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-$sealed_suffix.txt"
  do
    test -f "$sealed_lock/reports/$report_leaf"; test ! -L "$sealed_lock/reports/$report_leaf"
    /usr/bin/cmp -s "$sealed_lock/reports/$report_leaf" "$repo/$archive/reports/$report_leaf"
  done
done
postcommit_route=$(route_marker "$repo/$archive/canonical-marker.json" "$locked_source_sha")
test "$postcommit_route" = "$result_route"
/usr/bin/git -C "$repo" show "$commit:$archive/canonical-marker.json" > "$committed_marker"
committed_route=$(route_marker "$committed_marker" "$locked_source_sha")
test "$committed_route" = "$result_route"
for retained_lock in "$selection_lock" "$freeze_lock" "$contracts_lock"; do
  test -d "$retained_lock"; test ! -L "$retained_lock"
done
test -f "$selection_lock/authorization.json"; test ! -L "$selection_lock/authorization.json"
test "$(/usr/bin/stat -f '%Lp' "$selection_lock/authorization.json")" = '400'
if /usr/bin/find "$selection_lock" -mindepth 1 -print > "$actual"; then selection_tree_status=0; else selection_tree_status=$?; fi
test "$selection_tree_status" -eq 0
test "$(/usr/bin/awk 'END{print NR+0}' "$actual")" = '1'
test "$(/bin/cat "$actual")" = "$selection_lock/authorization.json"
ZSH
```

Run this wrapper in a PTY or noninteractive shell. It stages and validates the
exact whitelist, prints the staged stat, mode summary, and full test-source
diff, then performs the same NUL-list, mode, diff-check, source-binding, and
dirty-worktree assertions without a human/TTY handshake. Any HUP, INT, or TERM
exits nonzero before commit. The wrapper
copies the verified real index to a unique same-directory seed, hard-links the
seed create-only to `.git/index.lock`, rotates ownership proof to a hard-linked
owner path after `git add`, and runs every staging/index command with
`GIT_INDEX_FILE=.git/index.lock`. The existing lock blocks concurrent writers
until the ref and prepared index are installed. The wrapper repeats branch/HEAD,
the six-dirty tuple, source-lock, worktree/index, NUL whitelist, and
`diff --check` validations immediately before `write-tree`/`commit-tree`,
including a fresh fixed-tree/manifest/checksum/publishability pass.
The single `update-ref --stdin` transaction atomically verifies symbolic HEAD
and the expected old branch OID while installing the commit; the prepared index
then atomically replaces the real index. Afterward it compares the committed tree's normalized
mode/OID/path NUL records to the verified precommit index records and repeats
the six-dirty tuple, source bindings, failed-provenance checksum, complete
attempt-002 manifest/checksum, plan copy, report credential scan, and sealed
Contracts/Freeze report checksum and archive-byte comparisons.

The repository plan path is intentionally not staged because the approved final commit boundary is limited to the new test source and evidence trees. Its exact reviewed bytes are SHA-bound by the test source and generated commands, copied as `attempt-002/execution-plan.md`, checksum-covered, compared byte-for-byte immediately before staging and commit, and therefore included in the commit only through the approved evidence tree.

Postflight:

```text
new commit != base
new commit has exactly one parent equal to 5c84bb044352a3fef684a37f70cc08ac80058c7b
base..commit count=1
commit path NUL-list equals the same 25-path whitelist
commit tree mode/OID/path NUL-list equals the verified staged index
index empty
three phase locks retained and revalidated after commit verification
six user dirty paths remain byte/status/index-identical
no push
```

Before staging and again before commit, the test source must be regular non-symlink and its SHA must equal the unique `sourceFiles` entry in attempt-002 `source-lock.json`; ColbertScorer and both dirty reactor hashes must also match the lock.

## G. Result Routing, Risks, and Authorization Boundary

Routing is evidence-driven and immutable:

```text
INVALID
  -> if archive postflight validates the actual marker/reports and every fixed
     source/worktree/command binding: checksum -> local commit -> STOP
  -> otherwise retain the phase lock, observed runtime/report bytes, and any
     partial create-only archive -> STOP_WITHOUT_SELECTION_REPLAY

VALID but not visible=12/frontierMissing=4
  -> archive actual result
  -> checksum
  -> local commit
  -> STOP_CANDIDATE102A_SEAM_BASELINE_DIVERGED

VALID and visible=12/frontierMissing=4
  -> archive actual result
  -> checksum
  -> local commit
  -> only a read-only Candidate 10.2A Single-Pass Scored-Ranking Seam plan may follow
```

Compile failure with every attempt path absent is unconsumed and ends this
execution round. Contracts, Freeze, or Selection preflight failure before its
lock exists writes nothing and stops. Any Contracts, Freeze, Selection,
report-postflight, or archive-postflight failure after its lock is created
preserves all remaining phase locks, live target bytes, and any partial final
archive, then stops without a recovery state machine. Once F7 starts, the
completed archive is already fixed; any staging, commit-object,
ref-transaction, index-install, or committed-tree failure stops without
Selection replay. A completed Selection INVALID whose archive postflight
remains valid, or a 12/4 divergence, is not an infrastructure failure: it
follows the normal checksum and single-commit route above. An INVALID caused by
a persistent source-lock, command, or worktree defect may be unarchivable under
the same fail-closed postflight and then follows the retained-lock stop route.
No result permits modifying scorer, identifier preservation, fixture,
qrel, topK, threshold, Holdout, production, A/B, promotion, or tuning.

```text
PLAN_STATUS=READY_FOR_IMPLEMENTATION
PREDECESSOR_STATUS=SUPERSEDED_UNRECOVERABLE
ATTEMPT_001_STATUS=FAILED_CONTRACTS_POSTFLIGHT_CONSUMED
NEW_ATTEMPT=002
SELECTION_AUTHORIZATION_FILE=ABSENT
SELECTION_REPLAY=false
HOLDOUT_PATH_OPERATION_COUNT=0
ALGORITHM_CHANGE=false
PRODUCTION_CHANGE=false
PUSH_AUTHORIZATION=false
```
