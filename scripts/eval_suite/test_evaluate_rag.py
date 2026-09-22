from pathlib import Path

from evaluate_rag import evaluate, load_jsonl, load_qrels


ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "backend/tests/src/test/resources/rag-eval/candidate10-selection"


def test_fixture_contract_and_metrics(tmp_path: Path) -> None:
    queries = load_jsonl(FIXTURE / "queries.jsonl")
    corpus = load_jsonl(FIXTURE / "corpus.jsonl")
    qrels = load_qrels(FIXTURE / "qrels.tsv")
    assert len(queries) == 40
    assert len(corpus) == 1120
    assert len(qrels) == 30
    jsonl, summary = evaluate(FIXTURE, tmp_path, ["token_overlap"], 10)
    assert sum(1 for _ in jsonl.open(encoding="utf-8")) == 40
    lines = summary.read_text(encoding="utf-8").splitlines()
    assert lines[1].split(",") == ["token_overlap", "40", "0.450000", "0.450000", "0.650000", "0.495259", "0.650000"]
