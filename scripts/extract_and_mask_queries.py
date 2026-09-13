#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Phase 16 线上真实 Query 难例自动挖掘与无感加盐脱敏工具
(Production Real Query Mining & Structure-Preserving Sanitization)

功能：
1. 四维漏斗难例挖掘：零召回 (Zero-Recall)、低置信度 (Cosine < 0.60)、CRAG 歧义与扩展、多轮追问
2. 语法结构保留脱敏：手机号保留前3后4、身份证模11验证并掩码、银行卡Luhn验证、邮箱与IP脱敏
3. 会话级一致性映射与 Leakage 自检防御门禁
4. 支持 --test 自测模式与生产批处理导出
"""

import sys
import os
import json
import re
import argparse
from typing import Dict, List, Any, Optional

# 预编译非贪婪无回溯正则
PHONE_REGEX = re.compile(r'(?<!\d)(?:(?:\+?86[- ]?)?1[3-9]\d{9})(?!\d)')
IDCARD_REGEX = re.compile(r'(?<!\d)([1-9]\d{5}(?:18|19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx])(?!\d)')
BANKCARD_REGEX = re.compile(r'(?<!\d)([1-9]\d{15,18})(?!\d)')
EMAIL_REGEX = re.compile(r'[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+')
IPV4_REGEX = re.compile(r'\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b')

IDCARD_WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
IDCARD_CHECK_CODES = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']


def is_valid_idcard(idcard: str) -> bool:
    if len(idcard) != 18:
        return False
    total = sum(int(idcard[i]) * IDCARD_WEIGHTS[i] for i in range(17) if idcard[i].isdigit())
    mod = total % 11
    expected = IDCARD_CHECK_CODES[mod]
    return expected == idcard[17].upper()


def is_valid_luhn(card: str) -> bool:
    if not card.isdigit() or len(card) < 13 or len(card) > 19:
        return False
    digits = [int(d) for d in card]
    checksum = 0
    alternate = False
    for d in reversed(digits):
        if alternate:
            d *= 2
            if d > 9:
                d = (d % 10) + 1
        checksum += d
        alternate = not alternate
    return checksum % 10 == 0


class QuerySanitizerPython:
    """Python 端与 Java QuerySanitizer 逻辑一致的脱敏实现"""
    def __init__(self):
        self.session_cache: Dict[str, str] = {}

    def sanitize(self, text: str) -> str:
        if not text:
            return text

        # 1. 身份证脱敏 (保留前6后4)
        def replace_idcard(match):
            raw = match.group(1)
            if is_valid_idcard(raw):
                if raw not in self.session_cache:
                    self.session_cache[raw] = raw[:6] + "********" + raw[14:]
                return self.session_cache[raw]
            return raw

        text = IDCARD_REGEX.sub(replace_idcard, text)

        # 2. 银行卡脱敏 (保留前6后4)
        def replace_bankcard(match):
            raw = match.group(1)
            if is_valid_luhn(raw):
                if raw not in self.session_cache:
                    self.session_cache[raw] = raw[:6] + "******" + raw[-4:]
                return self.session_cache[raw]
            return raw

        text = BANKCARD_REGEX.sub(replace_bankcard, text)

        # 3. 手机号脱敏 (保留前3后4)
        def replace_phone(match):
            raw = match.group(0)
            if raw not in self.session_cache:
                digits = re.sub(r'\D', '', raw)
                if len(digits) == 11:
                    masked = digits[:3] + "****" + digits[7:]
                else:
                    masked = "[PHONE]"
                self.session_cache[raw] = masked
            return self.session_cache[raw]

        text = PHONE_REGEX.sub(replace_phone, text)

        # 4. 邮箱脱敏
        def replace_email(match):
            raw = match.group(0)
            if raw not in self.session_cache:
                parts = raw.split('@')
                if len(parts[0]) <= 1:
                    masked = "***@" + parts[1]
                else:
                    masked = parts[0][0] + "***@" + parts[1]
                self.session_cache[raw] = masked
            return self.session_cache[raw]

        text = EMAIL_REGEX.sub(replace_email, text)

        # 5. IPv4 脱敏
        def replace_ip(match):
            raw = match.group(0)
            if raw not in self.session_cache:
                last_dot = raw.rfind('.')
                self.session_cache[raw] = raw[:last_dot + 1] + "***"
            return self.session_cache[raw]

        text = IPV4_REGEX.sub(replace_ip, text)

        # 6. Leakage 自检防御门禁
        if PHONE_REGEX.search(text):
            text = PHONE_REGEX.sub("[PHONE]", text)

        return text


def classify_funnel(item: Dict[str, Any]) -> str:
    result = str(item.get("result", "")).strip()
    score = float(item.get("top_score", 0.0) or 0.0)
    query = str(item.get("query", ""))

    if not result or result in ("[]", "{}") or '"sources":[]' in result:
        return "zero_recall"
    if 0 < score < 0.60:
        return "low_confidence"
    if item.get("crag_ambiguous") or "AMBIGUOUS" in result:
        return "crag_ambiguous"
    if query.startswith(("然后呢", "接着说", "那如果", "之前说的", "继续")):
        return "follow_up"
    return "general"


def run_tests():
    print("=== 开始运行 extract_and_mask_queries.py 自测 ===")
    sanitizer = QuerySanitizerPython()

    # 测试 1: 手机与邮箱
    sample1 = "我的手机号是13800138000，请发预警到admin@qiantong.com"
    out1 = sanitizer.sanitize(sample1)
    assert "138****8000" in out1, f"手机脱敏失败: {out1}"
    assert "a***@qiantong.com" in out1, f"邮箱脱敏失败: {out1}"
    assert "13800138000" not in out1

    # 测试 2: 合法身份证与银行卡
    sample2 = "用户身份证110101199003072383，卡号6222021234567894"
    out2 = sanitizer.sanitize(sample2)
    assert "110101********2383" in out2, f"身份证脱敏失败: {out2}"
    assert "622202******7894" in out2, f"银行卡脱敏失败: {out2}"

    # 测试 3: 四维漏斗分类
    log1 = {"query": "不存在的事物", "result": "[]", "top_score": 0.0}
    assert classify_funnel(log1) == "zero_recall"

    log2 = {"query": "低分内容", "result": "ok", "top_score": 0.45}
    assert classify_funnel(log2) == "low_confidence"

    log3 = {"query": "歧义词", "result": "{\"status\":\"AMBIGUOUS\"}", "top_score": 0.8}
    assert classify_funnel(log3) == "crag_ambiguous"

    log4 = {"query": "接着说刚才的第二点", "result": "ok", "top_score": 0.9}
    assert classify_funnel(log4) == "follow_up"

    print("=== 所有自测试用例 100% 验证通过！===")


def main():
    parser = argparse.ArgumentParser(description="Phase 16 线上真实难例挖掘与脱敏工具")
    parser.add_argument("--test", action="store_true", help="运行内置契约单元自测")
    parser.add_argument("--input", "-i", type=str, help="输入日志文件路径 (JSON 或 JSONL)")
    parser.add_argument("--output", "-o", type=str, help="输出评测基准文件路径 (JSONL)")
    args = parser.parse_args()

    if args.test:
        run_tests()
        return

    if not args.input or not args.output:
        print("提示: 请指定 --input 和 --output，或使用 --test 运行自测")
        return

    sanitizer = QuerySanitizerPython()
    count = 0
    with open(args.input, "r", encoding="utf-8") as fin, open(args.output, "w", encoding="utf-8") as fout:
        for line in fin:
            if not line.strip():
                continue
            item = json.loads(line)
            cat = classify_funnel(item)
            sanitized_q = sanitizer.sanitize(item.get("query", ""))
            mined = {
                "id": f"real-mined-{count+1}",
                "query": sanitized_q,
                "category": cat,
                "expected_kb_id": item.get("knowledge_base_id"),
                "expected_contexts": item.get("expected_contexts", []),
                "split": "real-holdout"
            }
            fout.write(json.dumps(mined, ensure_ascii=False) + "\n")
            count += 1
    print(f"成功处理并导出 {count} 条脱敏难例样本至 {args.output}")


if __name__ == "__main__":
    main()
