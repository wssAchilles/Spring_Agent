import os
import requests
import subprocess
import glob

# Get token
login_url = "http://localhost:8099/login"
resp = requests.post(login_url, json={"username": "admin", "password": "admin123"})
token = resp.json().get("token")
headers = {"Authorization": f"Bearer {token}"}

pdf_dir = "/Users/achilles/Documents/许子祺/大厂工作日志/盛趣游戏"
pdfs = glob.glob(f"{pdf_dir}/**/*.pdf", recursive=True)

def get_db_record(prefix):
    cmd = [
        "psql", "-h", "127.0.0.1", "-p", "5432", "-U", "achilles", "-d", "ai_agent",
        "-t", "-A", "-c", 
        f"SELECT id, name, category_id, knowledge_base_id, workspace_id, category_name FROM kmc_document WHERE lower(name) LIKE '{prefix}%' LIMIT 1;"
    ]
    env = os.environ.copy()
    env["PGPASSWORD"] = "achilles"
    result = subprocess.run(cmd, env=env, capture_output=True, text=True)
    out = result.stdout.strip()
    if not out:
        return None
    parts = out.split("|")
    # if category_id or other fields are empty string, set them to None or empty string
    return {
        "id": int(parts[0]),
        "name": parts[1],
        "categoryId": parts[2] if parts[2] else None,
        "knowledgeBaseId": parts[3] if parts[3] else None,
        "workspaceId": parts[4] if parts[4] else None,
        "categoryName": parts[5] if len(parts) > 5 else None
    }

for pdf_path in sorted(pdfs):
    filename = os.path.basename(pdf_path)
    name_no_ext = os.path.splitext(filename)[0] # day01, day13_14
    
    # map name_no_ext to db name prefix
    db_prefix = name_no_ext.replace("_", "-").lower() # day01, day13-14
    
    row = get_db_record(db_prefix)
    if not row:
        print(f"Skipping {filename}, no matching DB record.")
        continue
        
    doc_id = row["id"]
    doc_name = row["name"]
    
    print(f"Processing {filename} -> ID: {doc_id} ({doc_name})")
    
    # Upload
    with open(pdf_path, "rb") as f:
        files = {"file": f}
        up_resp = requests.post("http://localhost:8099/common/upload", headers=headers, files=files)
        up_data = up_resp.json()
        
    if up_data.get("code") != 200:
        print(f"Upload failed for {filename}: {up_data}")
        continue
        
    file_path = up_data["fileName"].replace("/profile", "")
    
    # PUT update
    update_payload = {
        "id": doc_id,
        "name": doc_name,
        "path": file_path,
        "categoryId": row["categoryId"],
        "knowledgeBaseId": row["knowledgeBaseId"],
        "workspaceId": row["workspaceId"],
        "categoryName": row["categoryName"],
        "mode": "RecursiveSplitter",
        "docForm": "TEXT",
        "chunkOverlap": 20,
        "maxTokens": 500,
        "separator": "\\n",
        "chatModel": "gpt-4o",
        "chatModelProvider": "openai",
        "syncStatus": 2
    }
    
    put_url = "http://localhost:8099/kmcDocument/document"
    put_resp = requests.put(put_url, headers=headers, json=update_payload)
    print(f"Update response for {filename}: {put_resp.json()}")

print("Done.")
