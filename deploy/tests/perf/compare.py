"""比对两次基准（改造前 / 改造后）：性能指标对照，以及每个接口返回 JSON 的逐项差异。
用法：python3 compare.py <改造前目录> <改造后目录>
"""
import json, sys
from pathlib import Path

before, after = Path(sys.argv[1]), Path(sys.argv[2])
rb = {r['name']: r for r in json.loads((before / 'results.json').read_text())}
ra = {r['name']: r for r in json.loads((after / 'results.json').read_text())}


def diff(a, b, path=''):
    if type(a) is not type(b):
        yield f'{path}: {a!r} -> {b!r}'
    elif isinstance(a, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a or k not in b:
                yield f'{path}.{k}: {"缺失" if k not in a else repr(a[k])[:80]} -> {"缺失" if k not in b else repr(b[k])[:80]}'
            else:
                yield from diff(a[k], b[k], f'{path}.{k}')
    elif isinstance(a, list):
        if len(a) != len(b):
            yield f'{path}: 列表长度 {len(a)} -> {len(b)}'
        for i, (x, y) in enumerate(zip(a, b)):
            yield from diff(x, y, f'{path}[{i}]')
    elif a != b:
        yield f'{path}: {a!r} -> {b!r}'


print(f"{'接口':60s} {'耗时ms':>14s} {'SQL条数':>12s} {'扫描行数':>18s}")
for name in rb:
    b, a = rb[name], ra.get(name)
    if not a:
        print(f'{name[:60]:60s} 改造后缺失')
        continue
    print(f"{name[:60]:60s} {b['median_ms']:6.0f}->{a['median_ms']:<6.0f} {b['sql_count']:5d}->{a['sql_count']:<5d} "
          f"{b['rows_examined']:8d}->{a['rows_examined']:<8d}")
print('\n== 返回内容差异 ==')
same = 0
for f in sorted((before / 'snapshots').glob('*.json')):
    g = after / 'snapshots' / f.name
    if not g.exists():
        print(f'{f.name}: 改造后缺失')
        continue
    lines = list(diff(json.loads(f.read_text())['payload'], json.loads(g.read_text())['payload']))
    if lines:
        print(f'-- {f.name}（{len(lines)} 处）')
        for line in lines[:15]:
            print('   ' + line)
    else:
        same += 1
print(f'\n返回内容一致的接口：{same} 个')
