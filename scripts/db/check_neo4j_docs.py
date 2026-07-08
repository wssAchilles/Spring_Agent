from neo4j import GraphDatabase

uri = "bolt://localhost:7687"
user = "neo4j"
password = "neo4jpass123"

try:
    driver = GraphDatabase.driver(uri, auth=(user, password))
    with driver.session() as session:
        # Check what labels exist
        res = session.run("CALL db.labels()")
        labels = [r[0] for r in res]
        print("Labels in Neo4j:", labels)
        
        # Count nodes
        res = session.run("MATCH (n) RETURN count(n) as c")
        print("Total nodes:", res.single()['c'])

        # Get a sample node properties
        res = session.run("MATCH (n:Entity) RETURN keys(n) LIMIT 1")
        if res.peek():
            print("Entity properties:", res.single()[0])

        res = session.run("MATCH (n:Entity) RETURN n.documentName, count(n) as c GROUP BY n.documentName")
        if res.peek():
            for record in res:
                print(f"Doc: {record['n.documentName']} -> {record['c']} entities")
except Exception as e:
    print(f"Error: {e}")
