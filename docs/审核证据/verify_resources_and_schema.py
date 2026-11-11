"""资源及导出 schema 的静态契约检查；不用 Android 构建、不连接真实数据库。"""
from collections import Counter
import json
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MODULES = ['app/phone', 'core', 'data', 'modes/film', 'setup', 'player/core', 'player/local', 'settings', 'logging']
SCHEMAS = ROOT / 'data/schemas/dev.jdtech.jellyfin.database.ServerDatabase'


def format_signature(text):
    sequence = 0
    result = {}
    for match in re.finditer(r'%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])', text):
        if match[2] in '%n':
            continue
        if match[1]:
            index = int(match[1])
        else:
            sequence += 1
            index = sequence
        result[index] = match[2].lower()
    return result


def verify_resources(expect_missing_settings=True):
    paths = sorted({p for module in MODULES for p in (ROOT / module).rglob('*.xml')
                    if 'build' not in p.relative_to(ROOT).parts})
    paths += [ROOT / 'lint.xml', ROOT / '.idea/ktfmt.xml']
    parsed = {p: ET.parse(p).getroot() for p in paths}
    duplicates = []
    placeholders = []
    missing = {}
    for path, node in parsed.items():
        if node.tag != 'resources':
            continue
        names = [(child.get('type', child.tag), child.get('name')) for child in node if child.get('name')]
        duplicates.extend((str(path.relative_to(ROOT)), key) for key, count in Counter(names).items() if count > 1)
    for module in MODULES:
        base = ROOT / module / 'src/main/res/values/strings.xml'
        if base not in parsed:
            continue
        original = {c.get('name'): c for c in parsed[base] if c.tag in ('string', 'plurals')}
        for path, node in parsed.items():
            if path == base or path.name != 'strings.xml' or path.parent.parent != base.parent.parent:
                continue
            translated = {c.get('name'): c for c in node}
            if path.parent.name in ('values-zh-rCN', 'values-zh-rTW'):
                missing[str(path.relative_to(ROOT))] = [key for key, c in original.items()
                    if key not in translated and c.get('translatable') != 'false']
            for key, child in translated.items():
                if key not in original:
                    continue
                english = original[key]
                if child.tag == 'string':
                    pairs = [(''.join(english.itertext()), ''.join(child.itertext()))]
                elif child.tag == 'plurals':
                    quantities = {c.get('quantity'): ''.join(c.itertext()) for c in english}
                    pairs = [(quantities.get(c.get('quantity'), quantities.get('other', '')), ''.join(c.itertext())) for c in child]
                else:
                    continue
                for before, after in pairs:
                    if format_signature(before) != format_signature(after):
                        placeholders.append((str(path.relative_to(ROOT)), key))
    assert not duplicates, duplicates
    assert not placeholders, placeholders
    expected = {'edit_file_title', 'pref_player_backend', 'mpv_options', 'advanced', 'discard_changes_dialog_title', 'discard_changes_dialog_text'} if expect_missing_settings else set()
    assert all(set(keys) == (expected if path.startswith('settings/') else set()) for path, keys in missing.items())
    return {'xml_parsed': len(parsed), 'duplicate_keys': duplicates,
            'placeholder_mismatch': placeholders, 'missing_zh': {k:v for k,v in missing.items() if v}}


def schema_connection(version):
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    schema = json.loads((SCHEMAS / f'{version}.json').read_text())['database']
    for entity in schema['entities']:
        table = entity['tableName']
        db.execute(entity['createSql'].replace('${TABLE_NAME}', table))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', table))
        actual = {row[1]: (row[2], bool(row[3])) for row in db.execute(f'PRAGMA table_info(`{table}`)')}
        expected = {f['columnName']: (f['affinity'], f.get('notNull', False)) for f in entity['fields']}
        assert actual == expected, (version, table)
        for fk in entity.get('foreignKeys', []):
            parent = next(e for e in schema['entities'] if e['tableName'] == fk['table'])
            assert set(fk['referencedColumns']) <= {f['columnName'] for f in parent['fields']}
    assert db.execute('PRAGMA foreign_key_check').fetchall() == []
    return db, schema


def structure(db):
    tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")]
    return {table: {'columns': db.execute(f'PRAGMA table_info(`{table}`)').fetchall(),
                    'foreign_keys': db.execute(f'PRAGMA foreign_key_list(`{table}`)').fetchall(),
                    'indices': db.execute(f'PRAGMA index_list(`{table}`)').fetchall()} for table in tables}


def verify_schemas():
    versions = {}
    for version in range(2, 10):
        db, schema = schema_connection(version)
        versions[version] = len(schema['entities'])
        db.close()
    source = (ROOT / 'data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabase.kt').read_text()
    for start, end in [(6, 7), (8, 9)]:
        actual, _ = schema_connection(start)
        target, _ = schema_connection(end)
        block = source.split(f'val MIGRATION_{start}_{end} =', 1)[1].split('\nval MIGRATION_', 1)[0]
        statements = re.findall(r'connection\.execSQL\(\s*"([^"]+)"\s*\)', block)
        assert len(statements) == (2 if start == 6 else 8)
        for sql in statements:
            actual.execute(sql)
        assert structure(actual) == structure(target), (start, end)
        actual.close()
        target.close()
    # 自动迁移只核对声明、schema 差异与删除表说明，不模拟 Room 的生成器。
    for start, end in [(2, 3), (3, 4), (4, 5), (5, 6), (7, 8)]:
        assert f'AutoMigration(from = {start}, to = {end}' in source
    return {'schema_tables': versions, 'manual_migrations': ['6→7 结构匹配', '8→9 结构匹配'],
            'auto_migrations': '声明及 schema 静态核对；未执行 Room 生成的迁移代码',
            'limitation': '仓库只有 2～9 的导出 schema，未验证 1 及更早版本'}


if __name__ == '__main__':
    print(json.dumps({'resources': verify_resources(), 'schemas': verify_schemas()}, ensure_ascii=False, indent=2))
