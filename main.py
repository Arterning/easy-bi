"""
数据整理（龙）— 多平台电商销售数据聚合报表工具
GUI: uv run python main.py
CLI: uv run python main.py <输入Excel路径> [输出Excel路径]
"""

import os
import sys
import threading
import traceback
from pathlib import Path

import customtkinter as ctk
from tkinter import filedialog, messagebox

from src.ingest import load_config, parse_all_platforms
from src.transform import run_pipeline
from src.reports import generate_reports
from src.export import write_excel

ctk.set_appearance_mode("light")
ctk.set_default_color_theme("green")


def process(input_path: Path, output_path: Path, log_func):
    """核心处理逻辑，log_func 用于回传日志"""
    config = load_config("config/schemas.yaml")

    log_func(f"[读取] {input_path}")
    unified = parse_all_platforms(input_path, config)
    log_func(f"[解析] 共 {len(unified)} 行")

    if unified.empty:
        log_func("[错误] 未解析到数据")
        return False

    log_func("[合并] DuckDB 清洗...")
    consolidated = run_pipeline(unified)

    log_func("[报表] 生成三张报表...")
    reports = generate_reports(consolidated)
    for name, df in reports.items():
        log_func(f"  {name}: {len(df)} 行")

    output_path.parent.mkdir(parents=True, exist_ok=True)

    csv_path = output_path.parent / "汇总表.csv"
    consolidated.to_csv(csv_path, index=False, encoding="utf-8-sig")
    log_func(f"[输出] CSV: {csv_path.name}")

    write_excel(reports, output_path)
    log_func(f"[输出] Excel: {output_path.name}")
    log_func("[完成]")
    return True


class App(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("多平台电商数据聚合报表工具")
        self.geometry("640x480")
        self._set_icon()

        # ---------- 输入文件 ----------
        self.input_var = ctk.StringVar()
        ctk.CTkLabel(self, text="输入文件（各平台导出Excel）", anchor="w").pack(fill="x", padx=20, pady=(20, 0))
        row1 = ctk.CTkFrame(self, fg_color="transparent")
        row1.pack(fill="x", padx=20, pady=(5, 0))
        ctk.CTkEntry(row1, textvariable=self.input_var).pack(side="left", fill="x", expand=True)
        ctk.CTkButton(row1, text="浏览", width=80, command=self._browse_input).pack(side="right", padx=(10, 0))

        # ---------- 输出文件 ----------
        self.output_var = ctk.StringVar()
        ctk.CTkLabel(self, text="输出文件", anchor="w").pack(fill="x", padx=20, pady=(15, 0))
        row2 = ctk.CTkFrame(self, fg_color="transparent")
        row2.pack(fill="x", padx=20, pady=(5, 0))
        ctk.CTkEntry(row2, textvariable=self.output_var).pack(side="left", fill="x", expand=True)
        ctk.CTkButton(row2, text="浏览", width=80, command=self._browse_output).pack(side="right", padx=(10, 0))

        # ---------- 生成按钮 ----------
        self.run_btn = ctk.CTkButton(self, text="生成报表", height=40, command=self._run)
        self.run_btn.pack(pady=(20, 0))

        # ---------- 日志区 ----------
        self.log_box = ctk.CTkTextbox(self, state="disabled")
        self.log_box.pack(fill="both", expand=True, padx=20, pady=(15, 20))

    def _set_icon(self):
        try:
            if hasattr(sys, "_MEIPASS"):
                p = Path(sys._MEIPASS) / "app.ico"
            else:
                p = Path("app.ico")
            if p.exists():
                self.iconbitmap(str(p))
        except Exception:
            pass

    def _browse_input(self):
        p = filedialog.askopenfilename(filetypes=[("Excel files", "*.xlsx *.xls")])
        if p:
            self.input_var.set(p)
            if not self.output_var.get().strip():
                in_path = Path(p)
                self.output_var.set(str(in_path.parent / "报表输出.xlsx"))

    def _browse_output(self):
        p = filedialog.asksaveasfilename(
            defaultextension=".xlsx",
            filetypes=[("Excel files", "*.xlsx")],
            initialfile="报表输出.xlsx",
        )
        if p:
            self.output_var.set(p)

    def _log(self, msg):
        self.log_box.configure(state="normal")
        self.log_box.insert("end", msg + "\n")
        self.log_box.see("end")
        self.log_box.configure(state="disabled")
        self.update()

    def _run(self):
        in_path = self.input_var.get().strip()
        out_path = self.output_var.get().strip()

        if not in_path:
            messagebox.showerror("错误", "请选择输入文件")
            return
        if not out_path:
            messagebox.showerror("错误", "请选择输出位置")
            return

        self.log_box.configure(state="normal")
        self.log_box.delete("0.0", "end")
        self.log_box.configure(state="disabled")
        self.run_btn.configure(state="disabled", text="处理中...")

        def task():
            try:
                ok = process(Path(in_path), Path(out_path), self._log)
                if ok:
                    self._log("")
                    self._log(f"输出目录: {Path(out_path).resolve().parent}")
                    if messagebox.askyesno("完成", "报表生成完成！\n是否打开输出文件？"):
                        os.startfile(str(Path(out_path).resolve()))
            except Exception:
                self._log(traceback.format_exc())
                messagebox.showerror("错误", "处理出错，详见日志")
            finally:
                self.run_btn.configure(state="normal", text="生成报表")

        threading.Thread(target=task, daemon=True).start()


def main():
    if len(sys.argv) > 1:
        input_path = Path(sys.argv[1])
        if not input_path.exists():
            print(f"错误: 文件不存在 - {input_path}")
            sys.exit(1)
        output_path = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("data/output/报表输出.xlsx")

        def log(msg):
            print(msg)

        ok = process(input_path, output_path, log)
        sys.exit(0 if ok else 1)
    else:
        App().mainloop()


if __name__ == "__main__":
    main()
