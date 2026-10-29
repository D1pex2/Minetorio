#!/bin/sh
# Отдельная распаковка официального headless1.1.110; существующая папка результата запрещена.
set -eu
if [ "$#" -ne 2 ]; then
  echo "Usage: sh run-physics.sh /path/factorio_headless_x64_1.1.110.tar.xz /new/output-directory" >&2
  exit 2
fi
archive=$(CDPATH= cd "$(dirname "$1")" && pwd)/$(basename "$1")
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
expected=485fe6db36e5decd7dd0d70e7c97e61f818100fa3e48d87884b287027c7a646a
actual=$(sha256sum "$archive" | cut -d ' ' -f 1)
[ "$actual" = "$expected" ] || { echo "Wrong official1.1.110 archive SHA-256" >&2; exit 2; }
[ ! -e "$2" ] || { echo "Output directory must not exist" >&2; exit 2; }
mkdir -p "$2"
out=$(CDPATH= cd "$2" && pwd)
tar -xJf "$archive" -C "$out"
mkdir "$out/mods"
cp -R "$script_dir/physics_1.0.0" "$out/mods/"
engine="$out/factorio/bin/x64/factorio"
# config-path.cfg архива задает portable read/write paths, внутри новой распаковки.
"$engine" --version > "$out/version.log"
grep -F 'Version: 1.1.110 ' "$out/version.log" > /dev/null
"$engine" --mod-directory "$out/mods" --create "$out/physics.zip" --map-gen-seed 12345 > "$out/create.log" 2>&1
"$engine" --mod-directory "$out/mods" --benchmark "$out/physics.zip" --benchmark-ticks 1300 --benchmark-runs 1 > "$out/benchmark.log" 2>&1
python3 "$script_dir/extract-train-physics.py" "$out/create.log" "$out/benchmark.log" --output "$out/train-physics-native.json"
echo "Trace: $out/train-physics-native.json"
