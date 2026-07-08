import psycopg2

conn = psycopg2.connect("dbname='ai_agent' user='achilles' host='127.0.0.1' password='achilles'")
cur = conn.cursor()
cur.execute("SELECT table_name, column_name FROM information_schema.columns WHERE data_type IN ('character varying', 'text') AND table_schema = 'public';")
cols = cur.fetchall()

found = False
for table, col in cols:
    try:
        cur.execute(f"SELECT id FROM {table} WHERE {col} LIKE '%87aa%';")
        res = cur.fetchall()
        if res:
            print(f"Found in table: {table}, column: {col}, IDs: {[r[0] for r in res]}")
            found = True
    except Exception as e:
        conn.rollback()

if not found:
    print("Not found in any text column.")
