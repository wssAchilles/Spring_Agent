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
