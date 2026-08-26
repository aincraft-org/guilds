#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, shutil, sys, zipfile
from pathlib import Path
TRIPLE = "x86_64-unknown-linux-gnu"

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8192), b""):
            digest.update(chunk)
    return digest.hexdigest()

def patch(jar_path: Path, sidecar_path: Path) -> None:
    digest = sha256(sidecar_path)
    size = sidecar_path.stat().st_size
    with zipfile.ZipFile(jar_path, "r") as jar:
        manifest = json.loads(jar.read("squaremap-backends.json"))
    target = manifest["targets"][TRIPLE]
    target["sha256"] = digest
    target["length"] = str(size)
    payload = (json.dumps(manifest, indent=4) + "\n").encode()
    temp = jar_path.with_suffix(".jar.tmp")
    with zipfile.ZipFile(jar_path, "r") as src, zipfile.ZipFile(temp, "w") as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            if item.filename == "squaremap-backends.json":
                data = payload
            dst.writestr(item, data)
    shutil.move(temp, jar_path)
    print(f"patched {jar_path} for {sidecar_path} sha256={digest} size={size}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} <squaremap.jar> <squaremap-server>")
    patch(Path(sys.argv[1]), Path(sys.argv[2]))
