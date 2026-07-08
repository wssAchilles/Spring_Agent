import redis
import json
import time
from datetime import datetime

r = redis.Redis(host='127.0.0.1', port=6379, db=3)

# 插入一个模拟的 session 对话
session_id = "test-session-db-verify-1"
messages = [
    {"type": "USER", "content": "你好，帮我总结一下昨天的会议纪要", "timestamp": "2026-07-03T10:00:00Z"},
    {"type": "ASSISTANT", "content": "好的，昨天会议主要讨论了Q3的AI战略，重点是Agent的自主编排", "timestamp": "2026-07-03T10:00:05Z"}
]

for msg in messages:
    r.rpush(f"qknow:hermes:memory:short-term:messages:{session_id}", json.dumps(msg))

# 强制将最后更新时间设置为过去，以触发过期逻辑（1天前）
old_timestamp = str(int(time.time() - 86400 * 2 * 1000))
r.set(f"qknow:hermes:memory:short-term:last_update:{session_id}", old_timestamp)

print(f"Mock session {session_id} inserted into Redis.")
print("Waiting for SleepTimeMemoryAgent to pick it up (runs every minute)...")
