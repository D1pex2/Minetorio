#!/bin/sh
set -eu
[ ! -e /opt/factorio-oracle/rename.zip ]
[ ! -e /opt/factorio-oracle/rename-mods ]
mkdir /opt/factorio-oracle/rename-mods
cp -r /mnt/payload/rename_1.0.0 /opt/factorio-oracle/rename-mods/
sha256sum /mnt/payload/rename_1.0.0/control.lua /mnt/payload/rename_1.0.0/data.lua /mnt/payload/rename_1.0.0/info.json /mnt/payload/rename.sh
sha256sum /opt/factorio-oracle/rename-mods/rename_1.0.0/control.lua /opt/factorio-oracle/rename-mods/rename_1.0.0/data.lua /opt/factorio-oracle/rename-mods/rename_1.0.0/info.json
cd /opt/factorio-oracle/factorio
echo RENAME_CREATE_BEGIN
timeout 240 ./bin/x64/factorio --mod-directory /opt/factorio-oracle/rename-mods --create /opt/factorio-oracle/rename.zip --map-gen-seed 12345
echo RENAME_BENCH_BEGIN
timeout 240 ./bin/x64/factorio --mod-directory /opt/factorio-oracle/rename-mods --benchmark /opt/factorio-oracle/rename.zip --benchmark-ticks 60 --benchmark-runs 1
echo RENAME_DONE
