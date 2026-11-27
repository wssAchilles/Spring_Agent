import os
import sys
import traceback
import gurobipy as gp
from gurobipy import GRB
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv

# 加载 .env 环境变量（针对独立运行的情况）
load_dotenv(os.path.join(os.path.dirname(__file__), '../../.env'))

app = FastAPI(title="Gurobi OptiAgent Sidecar")

# 全局单例 Env (保护 WLS 授权 Session)
global_env = None

@app.on_event("startup")
def startup_event():
    global global_env
    try:
        wls_access_id = os.environ.get("WLSACCESSID")
        wls_secret = os.environ.get("WLSSECRET")
        license_id = os.environ.get("LICENSEID")

        if not wls_access_id or not wls_secret or not license_id:
            print("WARNING: Gurobi WLS credentials not fully provided in environment variables.")
            print("Please ensure WLSACCESSID, WLSSECRET, and LICENSEID are set.")
            return

        global_env = gp.Env(empty=True)
        global_env.setParam("WLSACCESSID", wls_access_id)
        global_env.setParam("WLSSECRET", wls_secret)
        global_env.setParam("LICENSEID", int(license_id))
        global_env.setParam("OutputFlag", 0) # 关闭默认标准输出日志，防止污染 API
        global_env.start()
        print("Gurobi WLS Environment initialized successfully.")
    except Exception as e:
        print(f"Failed to initialize Gurobi WLS: {e}")

class LinearProgramSpec(BaseModel):
    # 声明式线性规划模型描述，替代危险的任意 Python 代码执行
    sense: str = "MINIMIZE"
    objective: dict[str, float] = {}
    variables: dict[str, dict[str, float | str]] = {}
    constraints: list[dict] = []

@app.post("/solve")
def solve_model(spec: LinearProgramSpec):
    if not global_env:
        raise HTTPException(status_code=500, detail="Gurobi environment is not initialized (missing WLS credentials).")

    try:
        # 基于结构化声明式参数构建 Gurobi 模型，彻底移除动态代码执行入口以根除沙箱逃逸与 RCE 风险
        model = gp.Model("declarative_lp", env=global_env)
        var_map = {}
        for name, cfg in spec.variables.items():
            lb = float(cfg.get("lb", 0.0))
            ub = float(cfg.get("ub", GRB.INFINITY))
            vtype_str = str(cfg.get("vtype", "CONTINUOUS")).upper()
            vtype = GRB.INTEGER if vtype_str == "INTEGER" else (GRB.BINARY if vtype_str == "BINARY" else GRB.CONTINUOUS)
            var_map[name] = model.addVar(lb=lb, ub=ub, vtype=vtype, name=name)

        obj_expr = gp.quicksum(float(coeff) * var_map[var_name] for var_name, coeff in spec.objective.items() if var_name in var_map)
        sense_flag = GRB.MAXIMIZE if spec.sense.upper() == "MAXIMIZE" else GRB.MINIMIZE
        model.setObjective(obj_expr, sense_flag)

        for idx, c in enumerate(spec.constraints):
            terms = c.get("terms", {})
            op = c.get("op", "<=")
            rhs = float(c.get("rhs", 0.0))
            lhs = gp.quicksum(float(w) * var_map[vn] for vn, w in terms.items() if vn in var_map)
            if op == "<=":
                model.addConstr(lhs <= rhs, name=f"c_{idx}")
            elif op == ">=":
                model.addConstr(lhs >= rhs, name=f"c_{idx}")
            elif op == "==":
                model.addConstr(lhs == rhs, name=f"c_{idx}")
            else:
                raise ValueError(f"Unsupported constraint operator: {op}")

        model.optimize()
        output_dict = {
            "status_code": model.Status,
            "objective_value": model.ObjVal if model.SolCount > 0 else None,
            "solution": {k: v.X for k, v in var_map.items()} if model.SolCount > 0 else {}
        }
        return {"status": "SUCCESS", "data": output_dict}
    except Exception as e:
        error_trace = traceback.format_exc()
        return {"status": "ERROR", "error": str(e), "traceback": error_trace}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8100)
