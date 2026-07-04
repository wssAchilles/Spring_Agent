import psycopg2
import json

conn = psycopg2.connect("dbname='ai_agent' user='achilles' host='127.0.0.1' password='achilles'")
cur = conn.cursor()
cur.execute("""
    SELECT id, content, metadata 
    FROM vector_store 
    WHERE metadata->>'sessionId' IS NOT NULL 
    ORDER BY id DESC 
    LIMIT 5;
""")
rows = cur.fetchall()
print(f"Found {len(rows)} memory records.")
for i, r in enumerate(rows):
    print(f"--- Record {i+1} ---")
    print(f"ID: {r[0]}")
    print(f"Content: {r[1]}")
    print(f"SessionID: {r[2].get('sessionId')}")
    print(f"UserId: {r[2].get('userId')}")
    print(f"Importance: {r[2].get('importance')}")
print("=" * 50)
