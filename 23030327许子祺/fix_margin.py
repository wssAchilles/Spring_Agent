import json
import subprocess
import os

def run_cmd(cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout, res.stderr

def run_batch(file, commands):
    if not commands:
        return
    print(f"Applying to {file}...")
    json_path = f"{file}.batch.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(commands, f, ensure_ascii=False)

    res, err = run_cmd(["officecli", "batch", file, "--input", json_path, "--force"])
    if "Error" in err or "Exception" in err:
        print(f"Error on {file}: {err}")
    else:
        print(f"Success on {file}")

    if os.path.exists(json_path):
        os.remove(json_path)

def adjust_margin(file):
    cmds = [
        {
            "command": "set",
            "path": "/body/sectPr[1]",
            "props": {
                "marginBottom": "1.5cm"
            }
        }
    ]
    run_batch(file, cmds)

def main():
    files = [
        "5-实训任务书-项目1.docx",
        "6-实训报告-项目1.docx",
        "7-实训任务书-项目2.docx",
        "8-实训报告-项目2.docx"
    ]
    for f in files:
        adjust_margin(f)

if __name__ == "__main__":
    main()
