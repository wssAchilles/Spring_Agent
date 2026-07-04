#!/bin/bash
# 清除泄露的密钥
find . -type f -name "*.java" -o -name "*.yml" -o -name "*.md" | xargs sed -i '' 's/REDACTED_DEEPSEEK_KEY_1/REDACTED/g'