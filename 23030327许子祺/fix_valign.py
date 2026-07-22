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

def set_valign_top(file, cells):
    cmds = []
    for cell in cells:
        cmds.append({
            "command": "set",
            "path": cell,
            "props": {
                "valign": "top"
            }
        })
    run_batch(file, cmds)

def main():
    weekly_files = ["1-第2周报.docx", "2-第4周报.docx", "3-第6周报.docx", "4-第8周报.docx"]
    for f in weekly_files:
        set_valign_top(f, ["/body/tbl[1]/tr[2]/tc[2]", "/body/tbl[1]/tr[3]/tc[2]"])

    task_files = ["5-实训任务书-项目1.docx", "7-实训任务书-项目2.docx"]
    for f in task_files:
        set_valign_top(f, ["/body/tbl[1]/tr[2]/tc[1]", "/body/tbl[1]/tr[4]/tc[1]", "/body/tbl[1]/tr[6]/tc[1]"])

    report_files = ["6-实训报告-项目1.docx", "8-实训报告-项目2.docx"]
    report_cells = ["/body/tbl[1]/tr[2]/tc[2]"] + [f"/body/tbl[1]/tr[{i}]/tc[1]" for i in range(5, 13)]
    for f in report_files:
        set_valign_top(f, report_cells)

    set_valign_top("10-答辩记录.docx", ["/body/tbl[1]/tr[1]/tc[2]"])

if __name__ == "__main__":
    main()
