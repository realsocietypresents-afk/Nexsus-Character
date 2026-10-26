#!/usr/bin/env python3
"""Turns a YAML file into the sandbox YamlConfiguration's own file format (path TAB type TAB value), so the tests can load the SHIPPED config.yml."""
import sys
import yaml

src, dst = sys.argv[1], sys.argv[2]
data = yaml.safe_load(open(src, encoding='utf-8'))
lines = []


def walk(prefix, node):
    if isinstance(node, dict):
        for k, v in node.items():
            walk(prefix + '.' + str(k) if prefix else str(k), v)
    elif isinstance(node, bool):
        lines.append(f'{prefix}\tb\t{str(node).lower()}')
    elif isinstance(node, int):
        lines.append(f'{prefix}\ti\t{node}')
    elif isinstance(node, float):
        lines.append(f'{prefix}\td\t{node}')
    elif isinstance(node, list):
        lines.append(f'{prefix}\tl\t' + '\u0001'.join(str(x) for x in node))
    else:
        lines.append(f'{prefix}\ts\t{node}')


walk('', data)
open(dst, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
