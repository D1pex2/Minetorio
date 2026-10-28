"""Generator for the empty GameTest structures in src/main/resources/data/minetorio/structure/.

Stdlib only. Usage: python tools/make_empty_structure.py [output_path] [size]
Writes a gzip-compressed NBT structure: size [size,8,size] (default 16, i.e. empty16.nbt), empty
palette/blocks/entities, DataVersion 3955. empty32.nbt (the substation and big pole tests) is size 32.
"""
import gzip
import struct
import sys

DEFAULT_OUT = "src/main/resources/data/minetorio/structure/empty16.nbt"


def name(n):
    b = n.encode()
    return struct.pack(">H", len(b)) + b


def build(size=16):
    d = b"\x0a" + name("")  # root compound
    d += b"\x09" + name("size") + b"\x03" + struct.pack(">i", 3) + struct.pack(">iii", size, 8, size)
    for n in ("palette", "blocks", "entities"):
        d += b"\x09" + name(n) + b"\x00" + struct.pack(">i", 0)  # empty list
    d += b"\x03" + name("DataVersion") + struct.pack(">i", 3955)
    d += b"\x00"  # end compound
    return d


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 16
    with open(out, "wb") as f:
        f.write(gzip.compress(build(size)))
