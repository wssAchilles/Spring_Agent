import requests
import json
import time
import os

BASE_URL = os.getenv("SOTA_BASE_URL", "http://127.0.0.1:8099")
ADMIN_URL = f"{BASE_URL}/admin-api"

def get_token():
    try:
        response = requests.post(f"{ADMIN_URL}/system/auth/login", json={
            "username": os.getenv("SOTA_USERNAME", "admin"),
            "password": os.getenv("SOTA_PASSWORD")
        })
        return response.json()['data']['token']
    except Exception as e:
        print(f"Login failed: {e}")
        return None

def test_rag_fallback(token):
    print("--- 1. Testing RAG CRAG Web Fallback ---")
    headers = {"Authorization": f"Bearer {token}"}
    
    # We will just try to hit the knowledge base recall test API which is /admin-api/kmc/knowledgeBase/recallTest
    # Since we might not have a KB ID, let's just see if we can create one or if the endpoint works.
    print("Attempting to call recallTest...")
    res = requests.post(f"{ADMIN_URL}/kmc/knowledgeBase/recallTest", headers=headers, json={
        "id": 1,
        "query": "昨晚的美股收盘数据",
        "searchMethod": 1
    })
    print(res.text[:500])

if __name__ == "__main__":
    token = get_token()
    if token:
        test_rag_fallback(token)
