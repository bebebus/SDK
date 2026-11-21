# SPDX-License-Identifier: MIT
"""从 Git 提交生成可复现的 PHP、Java、Go 源码归档。"""

import argparse
import gzip
import io
import re
import subprocess
import tarfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def build(component, commit, epoch, target):
    source = git("archive", "--format=tar", commit, "--", component + "/")
    with tarfile.open(fileobj=io.BytesIO(source), mode="r:") as original:
        members = sorted(original.getmembers(), key=lambda entry: entry.name)
        with target.open("wb") as output, gzip.GzipFile(
            filename="", mode="wb", fileobj=output, mtime=0, compresslevel=9
        ) as zipped, tarfile.open(fileobj=zipped, mode="w", format=tarfile.PAX_FORMAT) as archive:
            for member in members:
                if member.name.rstrip("/") == component:
                    continue
                prefix = component + "/"
                if not member.name.startswith(prefix):
                    raise ValueError("归档包含意外路径: " + member.name)
                content = original.extractfile(member) if member.isfile() else None
                member.name = member.name[len(prefix):]
                member.uid = member.gid = 0
                member.uname = member.gname = ""
                member.mtime = epoch
                member.pax_headers = {}
                archive.addfile(member, content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ref", default="HEAD", help="Git 提交或标签，默认 HEAD")
    parser.add_argument("--version", required=True, help="归档文件名中的版本号")
    parser.add_argument("--output", required=True, type=Path, help="输出目录")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", args.version):
        parser.error("版本号必须为 X.Y.Z")
    commit = git("rev-parse", "--verify", args.ref + "^{commit}").decode().strip()
    epoch = int(git("show", "-s", "--format=%ct", commit).decode().strip())
    args.output.mkdir(parents=True, exist_ok=True)
    for component in ("php", "java", "go"):
        path = args.output / f"{component}-{args.version}.tar.gz"
        build(component, commit, epoch, path)
        print(path)


if __name__ == "__main__":
    main()
