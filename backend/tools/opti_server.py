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

class OptimizeRequest(BaseModel):
    code: str

@app.post("/solve")
def solve_model(req: OptimizeRequest):
    if not global_env:
        raise HTTPException(status_code=500, detail="Gurobi environment is not initialized (missing WLS credentials).")
    
    # 构建安全沙盒执行的 globals 和 locals
    # 大模型生成的代码应当将结果写入预注入的 output_dict 中
    output_dict = {}
    
    # 限制高危模块
    safe_builtins = {
        'print': print,
        'range': range,
        'len': len,
        'sum': sum,
        'Exception': Exception,
        'dict': dict,
        'list': list,
        'tuple': tuple,
        'set': set,
        'int': int,
        'float': float,
        'str': str,
        'bool': bool,
    }
    
    exec_globals = {
        '__builtins__': safe_builtins,
        'gp': gp,
        'GRB': GRB,
        'global_env': global_env,
        'output_dict': output_dict
    }
    
    try:
        # 执行大模型生成的代码
        exec(req.code, exec_globals, {})
        
        # 约定：如果模型遇到 Infeasible，必须由生成代码调用 computeIIS 或直接在捕获异常时处理
        # 但我们也可以通过 output_dict 直接拿到模型对象吗？
        # 最好是大模型生成的代码自行提取，因为沙盒外我们没有直接持有 Model 对象（除非约定变量名）
        return {"status": "SUCCESS", "data": output_dict}
        
    except Exception as e:
        error_trace = traceback.format_exc()
        return {"status": "ERROR", "error": str(e), "traceback": error_trace}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8100)
