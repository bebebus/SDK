# SPDX-License-Identifier: MIT
"""检查源码归档可重复生成且内容与 Git 提交一致。"""

import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
with tempfile.TemporaryDirectory(prefix="sdk-archive-test-") as tmp:
    dirs = [Path(tmp) / "first", Path(tmp) / "second"]
    for target in dirs:
        subprocess.run(
            [sys.executable, str(root / "_tooling/reproducible-source-archives.py"),
             "--version", "0.0.0", "--output", str(target)], cwd=root, check=True,
        )
    for component in ("php", "java", "go"):
        name = f"{component}-0.0.0.tar.gz"
        assert (dirs[0] / name).read_bytes() == (dirs[1] / name).read_bytes(), name
        names = subprocess.check_output(
            ["git", "ls-tree", "-r", "--name-only", "HEAD", "--", component + "/"],
            cwd=root, text=True,
        ).splitlines()
        expected = {name[len(component) + 1:]: name for name in names}
        with tarfile.open(dirs[0] / name) as archive:
            actual = {entry.name for entry in archive if entry.isfile()}
            assert actual == set(expected), f"{component} 归档文件清单不一致"
            for filename, git_path in expected.items():
                data = subprocess.check_output(["git", "show", "HEAD:" + git_path], cwd=root)
                assert archive.extractfile(filename).read() == data, git_path
        print(f"{component}：两次归档一致，{len(expected)} 个文件与 Git 内容一致")
