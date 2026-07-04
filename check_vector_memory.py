import psycopg2
import json
import os

conn = psycopg2.connect(
    dbname=os.getenv("DB_NAME", "ai_agent"),
    user=os.getenv("DB_USER", "achilles"),
    password=os.getenv("DB_PASSWORD"),
    host=os.getenv("DB_HOST", "127.0.0.1")
)
cur = conn.cursor()
cur.execute("SELECT id, content, metadata FROM vector_store WHERE metadata::text LIKE '%\"sessionId\"%' OR metadata::text LIKE '%\"importance\"%';")
rows = cur.fetchall()
print(f"Total rows found: {len(rows)}")
for i, r in enumerate(rows):
    if i < 5:
        print(f"--- Row {i+1} ---")
        print(f"ID: {r[0]}")
        print(f"Content: {r[1][:200]}...")
        print(f"Metadata: {json.dumps(r[2], ensure_ascii=False)}")
print("=" * 50)
