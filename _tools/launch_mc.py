"""启动 Minecraft 客户端（后台），日志写入项目根目录 _run.log。"""
import json
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
cmd = json.load(open(os.path.join(ROOT, "_launch_cmd.json"), encoding="utf-8"))
log = open(os.path.join(ROOT, "_run.log"), "wb")
p = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT,
                     cwd=r"D:\PCL 2\.minecraft\versions\1.20.1（Fabric）")
with open(os.path.join(ROOT, "_run.pid"), "w") as f:
    f.write(str(p.pid))
print("started pid", p.pid)
