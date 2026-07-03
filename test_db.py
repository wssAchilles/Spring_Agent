import psycopg2

conn = psycopg2.connect(
    dbname="ai_agent",
    user="achilles",
    password="achilles",
    host="127.0.0.1",
    port="5432"
)
cur = conn.cursor()
cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")
print([r[0] for r in cur.fetchall() if 'user' in r[0]])
