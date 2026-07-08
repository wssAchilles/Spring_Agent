from neo4j import GraphDatabase
import os

uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
user = os.getenv("NEO4J_USER", "neo4j")
password = os.getenv("NEO4J_PASSWORD")

driver = GraphDatabase.driver(uri, auth=(user, password))
with driver.session() as session:
    result = session.run("MATCH (n:SemanticCache) DETACH DELETE n")
    print("SemanticCache deleted.")
driver.close()
