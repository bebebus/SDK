# SPDX-License-Identifier: MIT
"""检查仓库中受版本控制的程序源码是否标明项目许可证。"""

from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
suffixes = {'.js', '.mjs', '.cjs', '.ts', '.java', '.go', '.php', '.py', '.sh'}
paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
missing = []
for name in paths:
    path = Path(name)
    if name and path.suffix in suffixes:
        lines = (root / path).read_text(encoding='utf-8').splitlines()[:6]
        if not any(line.strip() in ('# SPDX-License-Identifier: MIT', '// SPDX-License-Identifier: MIT') for line in lines):
            missing.append(name)
if missing:
    print('以下程序源码缺少 MIT SPDX 标识：', *missing, sep='\n', file=sys.stderr)
    sys.exit(1)
print('程序源码 SPDX 标识检查通过')
