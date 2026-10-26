"""One-off generator for src/main/resources/data/minetorio/structure/empty16.nbt.

Stdlib only. Usage: python tools/make_empty_structure.py [output_path]
Writes a gzip-compressed NBT structure: size [16,8,16], empty palette/blocks/entities, DataVersion 3955.
"""
import gzip
import struct
import sys

DEFAULT_OUT = "src/main/resources/data/minetorio/structure/empty16.nbt"


def name(n):
    b = n.encode()
    return struct.pack(">H", len(b)) + b


def build():
    d = b"\x0a" + name("")  # root compound
    d += b"\x09" + name("size") + b"\x03" + struct.pack(">i", 3) + struct.pack(">iii", 16, 8, 16)
    for n in ("palette", "blocks", "entities"):
        d += b"\x09" + name(n) + b"\x00" + struct.pack(">i", 0)  # empty list
    d += b"\x03" + name("DataVersion") + struct.pack(">i", 3955)
    d += b"\x00"  # end compound
    return d


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    with open(out, "wb") as f:
        f.write(gzip.compress(build()))
