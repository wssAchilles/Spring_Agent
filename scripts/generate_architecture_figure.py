#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate high-fidelity, publication-grade vector system architecture diagram for Knowledge Hub.
Complies with Elsevier ESWA Guide for Authors (100% English, vector PDF, no raster artifacts, elegant styling).
Optimized:
1. Extended vertical connector arrows (Client-to-Middle and Middle-to-Infra) for comfortable visual breathing room.
2. Adjusted vertical spacing so subtitles never touch or collide with the top borders of inner child cards.
"""

import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

def create_system_architecture(output_pdf_path, output_png_path=None):
    fig = plt.figure(figsize=(15, 9.8), dpi=300)
    ax = fig.add_subplot(111)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis("off")

    # 调色板 (Navy, Slate, Teal, Accent, Card Backgrounds)
    c_bg_main = "#F8FAFC"
    c_border_main = "#CBD5E1"
    c_control_bg = "#EFF6FF"
    c_control_border = "#3B82F6"
    c_hermes_bg = "#F0FDF4"
    c_hermes_border = "#10B981"
    c_infra_bg = "#F5F3FF"
    c_infra_border = "#8B5CF6"
    c_card_bg = "#FFFFFF"
    c_text_title = "#0F172A"
    c_text_body = "#334155"
    c_text_sub = "#64748B"
    c_accent_blue = "#1D4ED8"
    c_accent_green = "#047857"
    c_accent_purple = "#6D28D9"

    # 全局背景容器
    bg = FancyBboxPatch((0.5, 0.5), 99, 99, boxstyle="round,pad=0.2,rounding_size=1.5",
                        facecolor=c_bg_main, edgecolor=c_border_main, linewidth=1.5, zorder=0)
    ax.add_patch(bg)

    # ---------------------------------------------------------
    # 1. 顶层：Client Presentation Layer
    # ---------------------------------------------------------
    # 顶边 97.5, 底边 88.0, 高度 9.5
    client_box = FancyBboxPatch((3, 88.0), 94, 9.5, boxstyle="round,pad=0.15,rounding_size=1.0",
                                facecolor="#FFFFFF", edgecolor="#94A3B8", linewidth=1.2, zorder=1)
    ax.add_patch(client_box)
    ax.text(5, 95.0, "CLIENT PRESENTATION & WORKFLOW CANVAS LAYER (Vue 3 / Vite)", 
            fontsize=10.5, fontweight="bold", color=c_accent_blue)

    # Client 子卡片 (高度 5.5, y=88.8, 顶边 94.3)
    cards_client = [
        ("Web UI & SSE Stream", "Real-time Markdown Streaming\nTypewriter & Event-Source", (5, 88.8), 21),
        ("Visual DAG Designer", "Node Palette & Execution Graphs\nBreadth-First Layer Inspection", (28, 88.8), 22),
        ("HITL Review Console", "Interactive Approval Dialogs\nState Rollback & CAS Resume", (52, 88.8), 21),
        ("Knowledge & Bot Admin", "Document Parsing & Chunk Config\nVector & Graph Sync Monitoring", (75, 88.8), 20)
    ]
    for title, desc, (x, y), w in cards_client:
        box = FancyBboxPatch((x, y), w, 5.5, boxstyle="round,pad=0.1,rounding_size=0.6",
                             facecolor="#F1F5F9", edgecolor="#CBD5E1", linewidth=0.8, zorder=2)
        ax.add_patch(box)
        ax.text(x + 1.0, y + 3.6, title, fontsize=9.0, fontweight="bold", color=c_text_title)
        ax.text(x + 1.0, y + 1.1, desc, fontsize=7.2, color=c_text_sub, linespacing=1.25)

    # ---------------------------------------------------------
    # 2. 中层左侧：Business Control Plane (Spring Boot 3)
    # ---------------------------------------------------------
    # 顶边 81.5, 底边 33.5, 高度 48.0
    ctrl_box = FancyBboxPatch((3, 33.5), 44.5, 48.0, boxstyle="round,pad=0.15,rounding_size=1.2",
                              facecolor=c_control_bg, edgecolor=c_control_border, linewidth=1.8, zorder=1)
    ax.add_patch(ctrl_box)
    ax.text(5, 79.1, "BUSINESS CONTROL PLANE (qknow-admin: Spring Boot 3)", 
            fontsize=10.5, fontweight="bold", color=c_accent_blue)
    ax.text(5, 77.2, "Multi-Tenant Security, Knowledge Ingestion & REST Endpoints", 
            fontsize=7.8, color=c_text_sub)

    # 5 个子卡片：每个卡片高 5.8，y 间距 7.2。顶层卡片 y=69.5 -> 顶边 75.3 (与副标题 77.2 相差近 2.0，完全杜绝碰触)
    ctrl_cards = [
        ("Authentication & RBAC", "Spring Security + JWT Bearer\nWorkspace Tenancy Isolation", 69.5),
        ("Structure-Aware Chunker (FSM)", "AST Parser: Code Fences & Table Headers\nParent-Child Hierarchical Indexing", 62.3),
        ("Vector & Graph Pipeline", "Qwen Embed Batching (1536-dim)\nNeo4j Entity-Relation Triplets Sync", 55.1),
        ("REST API & Gateway Proxy", "HTTP/2 WebFlux SSE Streaming\nClient Abort Signal Detection", 47.9),
        ("GrpcReactorBridge", "Reactive doOnCancel Interceptor\nPropagates Abort Signals (<15ms)", 40.7)
    ]
    for title, desc, y in ctrl_cards:
        box = FancyBboxPatch((5, y), 40.5, 5.8, boxstyle="round,pad=0.1,rounding_size=0.6",
                             facecolor=c_card_bg, edgecolor="#BFDBFE", linewidth=1.0, zorder=2)
        ax.add_patch(box)
        ax.text(6.5, y + 3.8, title, fontsize=8.8, fontweight="bold", color=c_text_title)
        ax.text(6.5, y + 1.1, desc, fontsize=7.2, color=c_text_sub, linespacing=1.25)

    # ---------------------------------------------------------
    # 3. 中间通信通道：gRPC Channel (Bidirectional Stream)
    # ---------------------------------------------------------
    grpc_box = FancyBboxPatch((48.5, 48.5), 3.0, 18.0, boxstyle="round,pad=0.1,rounding_size=0.5",
                              facecolor="#FEF3C7", edgecolor="#F59E0B", linewidth=1.5, zorder=3)
    ax.add_patch(grpc_box)
    ax.text(50.0, 57.5, "gRPC\nHTTP/2\nStream", fontsize=8.5, fontweight="bold", 
            color="#B45309", ha="center", va="center")

    # 双向横向箭头
    ax.annotate("", xy=(52.2, 62.5), xytext=(46.8, 62.5),
                arrowprops=dict(arrowstyle="<->", color="#D97706", lw=2, mutation_scale=14), zorder=4)
    ax.annotate("", xy=(52.2, 52.5), xytext=(46.8, 52.5),
                arrowprops=dict(arrowstyle="<->", color="#D97706", lw=2, mutation_scale=14), zorder=4)

    # ---------------------------------------------------------
    # 4. 中层右侧：Hermes Cognitive Plane (Hermes Kernel)
    # ---------------------------------------------------------
    # 顶边 81.5, 底边 33.5, 高度 48.0
    hermes_box = FancyBboxPatch((52.5, 33.5), 44.5, 48.0, boxstyle="round,pad=0.15,rounding_size=1.2",
                                facecolor=c_hermes_bg, edgecolor=c_hermes_border, linewidth=1.8, zorder=1)
    ax.add_patch(hermes_box)
    ax.text(54.5, 79.1, "COGNITIVE EXECUTION ENGINE (qknow-hermes Kernel)", 
            fontsize=10.5, fontweight="bold", color=c_accent_green)
    ax.text(54.5, 77.2, "ReAct Reasoning, Non-Blocking Workflows & Self-Healing", 
            fontsize=7.8, color=c_text_sub)

    hermes_cards = [
        ("Hermes ReAct Engine", "Prompt Sandwich Defense + Guardrail\nReActCycleGuard (SHA-256 Fingerprint)", 69.5),
        ("AI Judge & Reflection Loop", "Tri-Dimensional Critique: Faith / Rel / Comp\nDual-Threshold Self-Consistency (0.60)", 62.3),
        ("Quad-Path Hybrid Retrieval", "Dense HNSW + Tantivy BM25 + Filters\nJNI AVX-512 SIMD Rescoring + 2-Hop PPR", 55.1),
        ("Non-Blocking DAG Engine", "Kahn Topological Sort & BFS Concurrency\nDelimited Continuation for Zero Thread Lock", 47.9),
        ("Immutable HAMT State Tree", "Branch Factor B=32 Path Copying\nIncremental Snapshots O(log32 N)", 40.7)
    ]
    for title, desc, y in hermes_cards:
        box = FancyBboxPatch((54.5, y), 40.5, 5.8, boxstyle="round,pad=0.1,rounding_size=0.6",
                             facecolor=c_card_bg, edgecolor="#A7F3D0", linewidth=1.0, zorder=2)
        ax.add_patch(box)
        ax.text(56.0, y + 3.8, title, fontsize=8.8, fontweight="bold", color=c_text_title)
        ax.text(56.0, y + 1.1, desc, fontsize=7.2, color=c_text_sub, linespacing=1.25)

    # ---------------------------------------------------------
    # 5. 底层：Infrastructure & External Foundation Layer
    # ---------------------------------------------------------
    # 顶边 27.0, 底边 3.5, 高度 23.5
    infra_box = FancyBboxPatch((3, 3.5), 94, 23.5, boxstyle="round,pad=0.15,rounding_size=1.2",
                               facecolor=c_infra_bg, edgecolor=c_infra_border, linewidth=1.5, zorder=1)
    ax.add_patch(infra_box)
    ax.text(5, 24.3, "INFRASTRUCTURE, DATA PERSISTENCE & MODEL PROVIDER LAYER", 
            fontsize=10.5, fontweight="bold", color=c_accent_purple)

    infra_cards = [
        ("PostgreSQL 16.2", "pgvector (Cosine HNSW)\npg_trgm Trigram GIN\nCAS Workflow States", (5, 5.2), 21),
        ("Neo4j 5.26 Graph DB", "Entity-Relation Graph\n2-Hop Induced Subgraphs\nSub-300ms PPR Traversal", (28, 5.2), 22),
        ("Redis 7.2 Cluster", "DB 0: Session Cache\nDB 3: Token Audit & Rate\nRedisson Distributed Lock", (52, 5.2), 21),
        ("Model & MCP Services", "DeepSeek-V2 / Reasoner\nQwen text-embedding-v3\nEnterprise MCP Sandbox", (75, 5.2), 20)
    ]
    for title, desc, (x, y), w in infra_cards:
        box = FancyBboxPatch((x, y), w, 16.5, boxstyle="round,pad=0.1,rounding_size=0.6",
                             facecolor=c_card_bg, edgecolor="#DDD6FE", linewidth=1.0, zorder=2)
        ax.add_patch(box)
        ax.text(x + 1.2, y + 13.0, title, fontsize=9.2, fontweight="bold", color=c_text_title)
        ax.text(x + 1.2, y + 3.2, desc, fontsize=7.6, color=c_text_sub, linespacing=1.35)

    # ---------------------------------------------------------
    # 6. 连线与数据流垂直长箭头 (Extended Vertical Arrows)
    # ---------------------------------------------------------
    # Client 底边 88.0 -> Control Plane 顶边 81.5 (垂直间距 6.5)
    # 箭头长达 5.8，大幅提升视觉舒展度
    ax.annotate("", xy=(25, 81.8), xytext=(25, 87.6),
                arrowprops=dict(arrowstyle="->", color=c_accent_blue, lw=2.0, mutation_scale=14), zorder=3)
    ax.text(25.6, 84.7, "HTTPS / REST", fontsize=8.0, color=c_accent_blue, fontweight="bold", va="center")

    # Client to Hermes (SSE Stream)
    ax.annotate("", xy=(75, 87.6), xytext=(75, 81.8),
                arrowprops=dict(arrowstyle="->", color=c_accent_green, lw=2.0, mutation_scale=14), zorder=3)
    ax.text(75.6, 84.7, "SSE Event Stream", fontsize=8.0, color=c_accent_green, fontweight="bold", va="center")

    # Control Plane 底边 33.5 -> DB 顶边 27.0 (垂直间距 6.5)
    # 箭头长达 5.8
    ax.annotate("", xy=(15.5, 27.3), xytext=(15.5, 33.2),
                arrowprops=dict(arrowstyle="<->", color=c_accent_purple, lw=1.8, mutation_scale=14), zorder=3)
    # Hermes to Data / Model
    ax.annotate("", xy=(85.0, 27.3), xytext=(85.0, 33.2),
                arrowprops=dict(arrowstyle="<->", color=c_accent_purple, lw=1.8, mutation_scale=14), zorder=3)

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_pdf_path), exist_ok=True)
    fig.savefig(output_pdf_path, format="pdf", bbox_inches="tight", dpi=300)
    if output_png_path:
        os.makedirs(os.path.dirname(output_png_path), exist_ok=True)
        fig.savefig(output_png_path, format="png", bbox_inches="tight", dpi=300)
    plt.close(fig)
    print(f"Architecture diagram exported successfully to:\n- {output_pdf_path}\n- {output_png_path}")

if __name__ == "__main__":
    out_pdf_fig = "/Users/achilles/Documents/许子祺/Agent/article/figures/system_architecture.pdf"
    out_pdf_root = "/Users/achilles/Documents/许子祺/Agent/article/system_architecture.pdf"
    out_png = "/Users/achilles/.gemini/antigravity/brain/5df56cc3-ce12-40c4-b191-0fb5383c4b77/scratch/system_architecture_preview.png"
    create_system_architecture(out_pdf_fig, out_png)
    import shutil
    shutil.copyfile(out_pdf_fig, out_pdf_root)
    print(f"Synced architecture PDF to root directory: {out_pdf_root}")
