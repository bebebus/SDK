# SPDX-License-Identifier: MIT
"""验证 coverage.py JSON 报告的语句与分支比例，分母必须非零。"""

import json
import sys
from pathlib import Path

totals = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["totals"]
for label, covered, total, minimum in (
    ("语句", totals["covered_lines"], totals["num_statements"], 90),
    ("分支", totals["covered_branches"], totals["num_branches"], 80),
):
    if not total or covered * 100 < total * minimum:
        sys.exit(f"Python {label}覆盖率未达到 {minimum}%：{covered}/{total}")
    print(f"Python {label}覆盖率：{covered}/{total} = {covered / total:.2%}")
