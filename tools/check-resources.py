#!/usr/bin/env python3
"""Checks that config.yml and plugin.yml parse, and that every key the code reads exists in config.yml (and the reverse)."""
import re
import sys
import yaml

cfg = yaml.safe_load(open('src/main/resources/config.yml', encoding='utf-8'))
yaml.safe_load(open('src/main/resources/plugin.yml', encoding='utf-8'))
print('plugin.yml and config.yml parse')

flat = set()


def walk(prefix, node):
    if isinstance(node, dict):
        for k, v in node.items():
            walk(prefix + '.' + k if prefix else k, v)
    else:
        flat.add(prefix)


walk('', cfg)
src = open('src/main/java/com/nexuscraft/nexuscharacter/CharacterConfig.java', encoding='utf-8').read()
read = set(re.findall(r'c\.get(?:Boolean|Int|Double|String|StringList|IntegerList)\("([a-z0-9.\-]+)"', src))
# skills.* and perks.* are lists of things with ids chosen by the server owner: the tests load the shipped file and check them
DYNAMIC = ('skills.', 'perks.')
missing = sorted(read - flat)
unused = sorted(k for k in flat - read if not k.startswith(DYNAMIC))
bad = False
if missing:
    print('FAIL: read by the code but not in config.yml:', missing)
    bad = True
if unused:
    print('FAIL: in config.yml but never read:', unused)
    bad = True
print('config keys checked:', len(read))
sys.exit(1 if bad else 0)
