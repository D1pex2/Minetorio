#!/usr/bin/env python3
"""Извлекает выбранные native записи, не переформатируя исходные числовые литералы JSON."""
import argparse
import hashlib
import json
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('logs', type=Path, nargs='+')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
records = []
for log in args.logs:
    for line in log.read_text(encoding='utf-8', errors='replace').splitlines():
        match = re.search(r'PHYSICS (\{.*\})', line)
        if match:
            raw = match.group(1)
            records.append((json.loads(raw), raw))

def selected(predicate):
    return [raw for value, raw in records if predicate(value)]

groups = {
    'setup': selected(lambda r: r.get('kind') == 'setup'),
    'valid_path': selected(lambda r: r.get('kind') == 'valid'),
    'first_powered': selected(lambda r: (r.get('kind') == 'probe_trace' and r.get('tick') in (9, 10, 11)) or (r.get('kind') == 'trace' and r.get('tick') == 12)),
    'steady_cap': selected(lambda r: r.get('kind') == 'trace' and 826 <= r.get('tick', 0) <= 891),
    'brake': selected(lambda r: r.get('kind') == 'trace' and r.get('tick') in (911, 912, 1150, 1151)),
}
if [len(groups[key]) for key in groups] != [1, 1, 4, 19, 4]:
    raise SystemExit('Неполная или отличающаяся трасса: ' + str({key: len(value) for key, value in groups.items()}))
metadata = {
    'engine_version': '1.1.110', 'engine_build': 62357, 'platform': 'linux64 headless',
    'engine_archive_sha256': '485fe6db36e5decd7dd0d70e7c97e61f818100fa3e48d87884b287027c7a646a',
    'logs_sha256': {log.name: hashlib.sha256(log.read_bytes()).hexdigest() for log in args.logs},
    'conditions': 'One automatic coal locomotive, full throttle, force braking bonus 0, natural acceleration; no forced speed or fuel/energy writes. Manual braking starts at tick 911.',
}
parts = [json.dumps(metadata, indent=2)[:-2] + ',']
for index, (name, rows) in enumerate(groups.items()):
    parts.append('  ' + json.dumps(name) + ': [\n    ' + ',\n    '.join(rows) + '\n  ]' + (',' if index < len(groups)-1 else ''))
text = '\n'.join(parts) + '\n}\n'
json.loads(text)
args.output.write_text(text, encoding='utf-8')
print('Сохранены исходные записи:', {key: len(value) for key, value in groups.items()})
