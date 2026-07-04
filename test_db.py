import psycopg2
import os

conn = psycopg2.connect(
    dbname=os.getenv("DB_NAME", "ai_agent"),
    user=os.getenv("DB_USER", "achilles"),
    password=os.getenv("DB_PASSWORD"),
    host=os.getenv("DB_HOST", "127.0.0.1"),
    port=os.getenv("DB_PORT", "5432")
)
cur = conn.cursor()
cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")
print([r[0] for r in cur.fetchall() if 'user' in r[0]])
