from neo4j import GraphDatabase

uri = "bolt://localhost:7687"
user = "neo4j"
password = "password"

driver = GraphDatabase.driver(uri, auth=(user, "123456"))
with driver.session() as session:
    result = session.run("MATCH (n:SemanticCache) DETACH DELETE n")
    print("SemanticCache deleted.")
driver.close()
