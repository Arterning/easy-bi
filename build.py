"""
打包脚本
用法: uv run python build.py
输出: dist/数据整理工具.exe
"""

import os
import sys
import subprocess

APP_NAME = "数据整理工具"
ENTRY = "main.py"


def main():
    icon = "app.ico"
    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--windowed",
        "--name", APP_NAME,
        f"--icon={icon}",
        "--add-data", f"config/schemas.yaml{os.pathsep}config",
        "--add-data", f"src{os.pathsep}src",
        "--add-data", f"app.ico{os.pathsep}.",
        "--clean",
        "--noconfirm",
        ENTRY,
    ]
    print("运行:", " ".join(cmd))
    subprocess.check_call(cmd)
    print(f"\n完成! 打包文件: dist/{APP_NAME}.exe")


if __name__ == "__main__":
    main()
