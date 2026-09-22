#!/usr/bin/env python3
"""Reproducible representative MTTR and five-year financial scenario.

All numeric values are declared scenario inputs from the approved ESWA
protocol.  The script reports both a conservative direct-benefit case and a
risk-inclusive case, and explicitly checks whether the requested headline
ROI, NPV, and payback can hold simultaneously.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Inputs:
    baseline_mttr_min: float = 84.0
    knowledge_hub_mttr_min: float = 22.0
    annual_events: int = 12
    staff_count: int = 3
    staff_rate_usd_per_hour: float = 120.0
    downtime_cost_usd_per_hour: float = 140_000.0
    additional_incident_hours: float = 1.0
    attribution_weight: float = 0.20
    hallucination_baseline: float = 0.316
    hallucination_knowledge_hub: float = 0.114
    capex_usd: float = 250_000.0
    annual_opex_usd: float = 35_000.0
    discount_rate: float = 0.08
    requested_roi_pct: float = 312.6
    requested_npv_usd: float = 1_014_350.0
    requested_payback_months: float = 4.6


SOURCES = [
    {
        "name": "Gartner downtime-cost benchmark (protocol source)",
        "url": "https://blogs.gartner.com/andrew-lerner/2014/07/16/the-cost-of-downtime/",
        "verification": "NOT_VERIFIED",
        "note": "The original blog URL is retired/unavailable in this run; secondary indexes attribute a $140,000--$540,000/hour range to the post. Authors must confirm the primary citation before submission.",
    },
    {
        "name": "Ponemon Institute, 2016 Cost of Data Center Outages",
        "url": "https://www.ponemon.org/research/ponemon-library/security/2016-cost-of-data-center-outages.html",
        "verification": "VERIFIED_PUBLIC",
        "note": "Public study page; its reported average outage cost is not substituted for the scenario's declared $140,000/hour input.",
    },
    {
        "name": "Vertiv/Ponemon 2016 primary report PDF",
        "url": "https://www.vertiv.com/globalassets/documents/reports/2016-cost-of-data-center-outages-11-11_51190_1.pdf",
        "verification": "VERIFIED_PUBLIC",
        "note": "Primary report states the sample and outage-cost methodology; it is used for provenance and not as a customer measurement.",
    },
    {
        "name": "IEEE Computer, Software Systems With Antifragility to Downtime",
        "url": "https://doi.org/10.1109/MC.2018.2888772",
        "verification": "VERIFIED_METADATA",
        "note": "Supports the relevance of downtime resilience; it does not validate the numeric $140,000/hour input.",
    },
    {
        "name": "Google SRE book, Service Level Objectives",
        "url": "https://sre.google/sre-book/service-level-objectives/",
        "verification": "VERIFIED_PUBLIC",
        "note": "Supports SLO/error-budget framing for the scenario; it is not used as a monetary benchmark.",
    },
]


def npv(cash_flows: list[float], rate: float) -> float:
    return sum(value / ((1.0 + rate) ** year) for year, value in enumerate(cash_flows))


def scenario(name: str, annual_benefit: float, inputs: Inputs) -> dict[str, Any]:
    annual_net = annual_benefit - inputs.annual_opex_usd
    cash_flows = [-inputs.capex_usd] + [annual_net] * 5
    discounted = [cash_flows[0]] + [annual_net / ((1 + inputs.discount_rate) ** year) for year in range(1, 6)]
    total_net = sum(cash_flows[1:])
    return {
        "name": name,
        "annualBenefitUsd": annual_benefit,
        "annualNetCashFlowUsd": annual_net,
        "cashFlowsUsd": cash_flows,
        "discountedCashFlowsUsd": discounted,
        "npv5Usd": npv(cash_flows, inputs.discount_rate),
        "roi5Pct": ((total_net - inputs.capex_usd) / inputs.capex_usd) * 100.0,
        "discountedReturnOnCapexPct": (npv(cash_flows, inputs.discount_rate) / inputs.capex_usd) * 100.0,
        "paybackMonths": inputs.capex_usd / annual_net * 12.0 if annual_net > 0 else math.inf,
    }


def compute_direct_benefit(inputs: Inputs, downtime_factor: float = 1.0, attribution_factor: float = 1.0, staff_rate: float | None = None) -> float:
    delta_hours = (inputs.baseline_mttr_min - inputs.knowledge_hub_mttr_min) / 60.0
    rate = inputs.staff_rate_usd_per_hour if staff_rate is None else staff_rate
    downtime = inputs.annual_events * delta_hours * inputs.downtime_cost_usd_per_hour * downtime_factor * inputs.attribution_weight * attribution_factor
    labor = inputs.annual_events * delta_hours * inputs.staff_count * rate
    return downtime + labor


def compute(inputs: Inputs) -> dict[str, Any]:
    delta_min = inputs.baseline_mttr_min - inputs.knowledge_hub_mttr_min
    delta_hours = delta_min / 60.0
    direct_downtime = inputs.annual_events * delta_hours * inputs.downtime_cost_usd_per_hour * inputs.attribution_weight
    labor = inputs.annual_events * delta_hours * inputs.staff_count * inputs.staff_rate_usd_per_hour
    incident_loss = inputs.downtime_cost_usd_per_hour * inputs.additional_incident_hours
    hallucination = (inputs.hallucination_baseline - inputs.hallucination_knowledge_hub) * inputs.annual_events * incident_loss
    direct_benefit = direct_downtime + labor
    full_benefit = direct_benefit + hallucination
    conservative = scenario("conservative_direct_benefit", direct_benefit, inputs)
    risk_inclusive = scenario("risk_inclusive_benefit", full_benefit, inputs)
    sensitivity_specs = [
        ("base direct benefit", 1.0, 1.0, inputs.staff_rate_usd_per_hour),
        ("downtime cost -50%", 0.5, 1.0, inputs.staff_rate_usd_per_hour),
        ("attribution weight -50%", 1.0, 0.5, inputs.staff_rate_usd_per_hour),
        ("staff rate = $60/h", 1.0, 1.0, 60.0),
        ("downtime/attribution -50%, staff $60/h", 0.5, 0.5, 60.0),
    ]
    sensitivity = []
    for label, downtime_factor, attribution_factor, staff_rate in sensitivity_specs:
        benefit = compute_direct_benefit(inputs, downtime_factor, attribution_factor, staff_rate)
        row = scenario(label, benefit, inputs)
        row.update({"downtimeFactor": downtime_factor, "attributionFactor": attribution_factor, "staffRateUsdPerHour": staff_rate})
        sensitivity.append(row)
    annuity_factor = sum(1.0 / ((1 + inputs.discount_rate) ** year) for year in range(1, 6))
    implied_net_from_npv = (inputs.requested_npv_usd + inputs.capex_usd) / annuity_factor
    implied_net_from_roi = inputs.capex_usd * (1 + inputs.requested_roi_pct / 100.0) / 5.0
    implied_net_from_payback = inputs.capex_usd / (inputs.requested_payback_months / 12.0)
    stages = {
        "baseline": {"fault_localization_min": 55.0, "remediation_plan_min": 15.0, "approval_execution_min": 14.0},
        "knowledge_hub": {"fault_localization_min": 3.0, "remediation_plan_min": 4.0, "approval_execution_min": 15.0},
    }
    return {
        "schemaVersion": "eswa.roi_scenario.v1",
        "status": "VALID_SCENARIO_WITH_HEADLINE_INCONSISTENCY",
        "inputs": asdict(inputs),
        "derived": {
            "deltaMttrMin": delta_min,
            "mttrReductionPct": delta_min / inputs.baseline_mttr_min * 100.0,
            "annualDirectDowntimeAvoidedUsd": direct_downtime,
            "annualLaborSavingsUsd": labor,
            "annualHallucinationRiskAvoidedUsd": hallucination,
            "annualDirectBenefitUsd": direct_benefit,
            "annualRiskInclusiveBenefitUsd": full_benefit,
            "incidentCostPerMinuteUsd": inputs.downtime_cost_usd_per_hour / 60.0,
            "additionalIncidentLossUsd": incident_loss,
        },
        "stages": stages,
        "scenarios": [conservative, risk_inclusive],
        "sensitivity": sensitivity,
        "requestedHeadlineConsistency": {
            "annuityFactor": annuity_factor,
            "impliedAnnualNetFromNpvUsd": implied_net_from_npv,
            "impliedAnnualNetFromRoiUsd": implied_net_from_roi,
            "impliedAnnualNetFromPaybackUsd": implied_net_from_payback,
            "npvAndDirectBenefitDifferenceUsd": conservative["npv5Usd"] - inputs.requested_npv_usd,
            "roiAndDirectBenefitDifferencePct": conservative["roi5Pct"] - inputs.requested_roi_pct,
            "paybackAndRiskInclusiveDifferenceMonths": risk_inclusive["paybackMonths"] - inputs.requested_payback_months,
            "interpretation": "The supplied NPV aligns with the direct-benefit case, while the supplied 4.6-month payback aligns with the risk-inclusive case. The supplied 312.6% ROI cannot be obtained from the same cash-flow definition without an additional unreported assumption.",
        },
        "sourceBasis": SOURCES,
        "limitations": [
            "This is a representative parameterized scenario, not a measured customer incident.",
            "The $140,000/hour value and hallucination rates require author-confirmed public citations or project logs before submission.",
            "No ROI, MTTR, or outage-loss claim is inferred from the retrieval or 100-question generation experiments.",
            "The risk term assumes an additional one-hour incident caused by an erroneous action. It must be disjoint from the recovery-time benefit to avoid double counting.",
        ],
    }


def latex_escape(value: str) -> str:
    replacements = {"\\": r"\textbackslash{}", "_": r"\_", "%": r"\%",
                    "$": r"\$", "&": r"\&", "#": r"\#", "{": r"\{", "}": r"\}"}
    return "".join(replacements.get(char, char) for char in value)


def render_table(result: dict[str, Any]) -> str:
    lines = [
        "% Auto-generated by scripts/eval_suite/roi_scenario_simulation.py.",
        "% Scenario inputs and arithmetic are stored in backend/tests/evidence/roi_scenario_simulation.json.",
        r"\begin{table}[htbp]", r"\centering", r"\footnotesize",
        r"\caption{Representative five-year cash-flow scenarios; values are parameterized inputs, not measured customer outcomes.}",
        r"\label{tab:roi-scenario}", r"\resizebox{\linewidth}{!}{%", r"\begin{tabular}{lrrrrrr}", r"\toprule",
        r"Scenario / year & 0 & 1 & 2 & 3 & 4 & 5 \\", r"\midrule",
    ]
    for row in result["scenarios"]:
        label = {"conservative_direct_benefit": "Direct benefit",
                 "risk_inclusive_benefit": "Including assumed risk benefit"}[row["name"]]
        values = [label] + [f"{value:,.0f}" for value in row["cashFlowsUsd"]]
        lines.append(" & ".join(values) + r" \\")
    lines += [r"\bottomrule", r"\end{tabular}}", r"\end{table}", "", r"\begin{table}[htbp]", r"\centering", r"\footnotesize", r"\caption{Derived financial indicators and the consistency check for the requested headline values.}", r"\label{tab:roi-indicators}", r"\begin{tabular}{lrrr}", r"\toprule", r"Scenario & NPV$_5$ (USD) & ROI$_5$ (\%) & Payback (months) \\", r"\midrule"]
    for row in result["scenarios"]:
        label = {"conservative_direct_benefit": "Direct benefit",
                 "risk_inclusive_benefit": "Including assumed risk benefit"}[row["name"]]
        lines.append(f"{label} & {row['npv5Usd']:,.0f} & {row['roi5Pct']:.1f} & {row['paybackMonths']:.2f} " + r"\\")
    lines += [r"\bottomrule", r"\end{tabular}", r"\begin{flushleft}\scriptsize", r"The conservative case includes attributed downtime and labor savings. The risk-inclusive case adds the declared hallucination-risk term. The requested 312.6\% ROI, \$1,014,350 NPV, and 4.6-month payback do not arise from one consistent cash-flow row; see the consistency fields in the evidence JSON.", r"\end{flushleft}", r"\end{table}", "", r"\begin{table}[htbp]", r"\centering", r"\scriptsize", r"\caption{Direct-benefit sensitivity to downtime attribution and labor-rate assumptions.}", r"\label{tab:roi-sensitivity}"]
    lines.append(r"\begin{tabularx}{\linewidth}{@{}Xrrrr@{}}")
    lines.append(r"\toprule")
    lines.append(r"Assumption & Benefit (USD/yr) & NPV (USD) & ROI (\%) & Months \\")
    lines.append(r"\midrule")
    for row in result["sensitivity"]:
        label = latex_escape(row["name"])
        lines.append(f"{label} & {row['annualBenefitUsd']:,.0f} & {row['npv5Usd']:,.0f} & {row['roi5Pct']:.1f} & {row['paybackMonths']:.2f} " + r"\\")
    lines += [r"\bottomrule", r"\end{tabularx}", r"\end{table}", ""]
    return "\n".join(lines)


def render_figure(result: dict[str, Any], output: Path) -> None:
    plt.rcParams.update({"font.family": ["Arial", "Helvetica", "DejaVu Sans", "sans-serif"], "font.size": 12, "axes.spines.top": False, "axes.spines.right": False, "axes.linewidth": 1.5, "legend.frameon": False, "svg.fonttype": "none", "pdf.fonttype": 42, "savefig.facecolor": "white"})
    labels = ["Baseline", "Knowledge Hub"]
    stage_keys = ["fault_localization_min", "remediation_plan_min", "approval_execution_min"]
    stage_labels = ["Fault localization", "Plan and validation", "Approval and execution"]
    colors = ["#0F4D92", "#42949E", "#8BCF8B"]
    fig, ax = plt.subplots(figsize=(7.4, 3.8))
    left = [0.0, 0.0]
    for key, label, color in zip(stage_keys, stage_labels, colors):
        values = [result["stages"]["baseline"][key], result["stages"]["knowledge_hub"][key]]
        ax.barh(labels, values, left=left, label=label, color=color, edgecolor="white", linewidth=0.8)
        for index, value in enumerate(values):
            if value >= 3:
                ax.text(left[index] + value / 2, index, f"{value:.0f}", ha="center", va="center", fontsize=10, color="white" if color == "#0F4D92" else "#173617")
        left = [left[index] + values[index] for index in range(2)]
    totals = [sum(result["stages"]["baseline"].values()), sum(result["stages"]["knowledge_hub"].values())]
    for index, total in enumerate(totals):
        ax.text(total + 1, index, f"Total {total:.0f} min", va="center", fontsize=10, weight="bold")
    ax.set_xlabel("Minutes per representative incident")
    ax.set_xlim(0, max(totals) * 1.24)
    ax.legend(loc="upper center", bbox_to_anchor=(0.5, 1.23), ncol=3, fontsize=9)
    ax.grid(axis="x", color="#D9D9D9", linewidth=0.6, alpha=0.7)
    ax.set_axisbelow(True)
    fig.tight_layout(pad=1.2)
    output.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output.with_suffix(".pdf"), dpi=300, bbox_inches="tight", pad_inches=0.08)
    fig.savefig(output.with_suffix(".svg"), dpi=300, bbox_inches="tight", pad_inches=0.08)
    plt.close(fig)


def main() -> None:
    if sys.prefix == sys.base_prefix:
        raise RuntimeError("Run with the project .venv Python")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-json", type=Path, default=ROOT / "backend/tests/evidence/roi_scenario_simulation.json")
    parser.add_argument("--output-tex", type=Path, default=ROOT / "article/generated_roi_table.tex")
    parser.add_argument("--figure-stem", type=Path, default=ROOT / "article/figures/mttr_breakdown")
    args = parser.parse_args()
    result = compute(Inputs())
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.output_tex.parent.mkdir(parents=True, exist_ok=True)
    args.output_tex.write_text(render_table(result), encoding="utf-8")
    render_figure(result, args.figure_stem)
    print(json.dumps({"status": result["status"], "directBenefit": result["derived"]["annualDirectBenefitUsd"], "riskInclusiveBenefit": result["derived"]["annualRiskInclusiveBenefitUsd"], "scenarios": [{"name": x["name"], "npv5": x["npv5Usd"], "roi5Pct": x["roi5Pct"], "paybackMonths": x["paybackMonths"]} for x in result["scenarios"]]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
