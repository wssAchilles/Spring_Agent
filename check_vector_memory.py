import psycopg2
import json

conn = psycopg2.connect("dbname='ai_agent' user='achilles' host='127.0.0.1' password='achilles'")
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
