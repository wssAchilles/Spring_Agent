import psycopg2
import json

conn = psycopg2.connect("dbname='ai_agent' user='achilles' host='127.0.0.1' password='achilles'")
cur = conn.cursor()
cur.execute("SELECT id, content, metadata FROM vector_store WHERE metadata::text LIKE '%scope%' OR metadata::text LIKE '%session%' OR metadata::text LIKE '%memory%' LIMIT 10;")
rows = cur.fetchall()
for r in rows:
    print(f"ID: {r[0]}")
    print(f"Content: {r[1][:100]}...")
    print(f"Metadata: {json.dumps(r[2], ensure_ascii=False)}")
    print("-" * 50)
