#!/usr/bin/env bash
#
# check-sql-parity.sh
#
# 校验 dsh-java 三处 SQL 表集合口径一致：
#   1) dsh-java-app/src/main/resources/schema.sql          （启动生效脚本）
#   2) docs/dev-ops/mysql/sql/dsh_java.sql                 （MySQL dump 参考）
#   3) dsh-java-app/src/main/resources/db/migration/*.sql  （迁移脚本，只读对照）
#
# 校验项：
#   - schema.sql 与 dsh_java.sql 表集合差异为 0
#   - db/migration 表集合 ⊆ 两脚本表集合的并集
#   - README.md / README.en.md 声明的表数量与实际一致
#
# 退出码：0=全部一致；1=存在差异。
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

SCHEMA_SQL="$ROOT_DIR/dsh-java-app/src/main/resources/schema.sql"
DUMP_SQL="$ROOT_DIR/docs/dev-ops/mysql/sql/dsh_java.sql"
MIGRATION_DIR="$ROOT_DIR/dsh-java-app/src/main/resources/db/migration"
README_CN="$ROOT_DIR/README.md"
README_EN="$ROOT_DIR/README.en.md"

for f in "$SCHEMA_SQL" "$DUMP_SQL" "$MIGRATION_DIR" "$README_CN" "$README_EN"; do
    if [ ! -e "$f" ]; then
        echo "[ERROR] 路径不存在：$f" >&2
        exit 1
    fi
done

# 从 stdin 提取 CREATE TABLE 表名并规范化：小写、去反引号、去 IF NOT EXISTS 修饰
extract_names() {
    grep -ioE 'CREATE TABLE( IF NOT EXISTS)?[[:space:]]+`?[A-Za-z0-9_]+`?' \
        | sed -E 's/.*[[:space:]]`?([A-Za-z0-9_]+)`?$/\1/' \
        | tr 'A-Z' 'a-z' \
        | sort -u
}

schema_tables=$(extract_names < "$SCHEMA_SQL")
dump_tables=$(extract_names < "$DUMP_SQL")
migration_tables=$(cat "$MIGRATION_DIR"/*.sql | extract_names)

schema_count=$(printf '%s\n' "$schema_tables" | grep -c . || true)
dump_count=$(printf '%s\n' "$dump_tables" | grep -c . || true)
migration_count=$(printf '%s\n' "$migration_tables" | grep -c . || true)

only_schema=$(comm -23 <(printf '%s\n' "$schema_tables") <(printf '%s\n' "$dump_tables") | grep -c . || true)
only_dump=$(comm -13 <(printf '%s\n' "$schema_tables") <(printf '%s\n' "$dump_tables") | grep -c . || true)

union_tables=$(printf '%s\n%s\n' "$schema_tables" "$dump_tables" | sort -u)
only_migration=$(comm -23 <(printf '%s\n' "$migration_tables") <(printf '%s\n' "$union_tables") | grep -c . || true)

cn_declared=$(grep -oE '共 [0-9]+ 张表' "$README_CN" | head -1 | grep -oE '[0-9]+' || echo "")
en_declared=$(grep -oE '\([0-9]+ in total\)' "$README_EN" | head -1 | grep -oE '[0-9]+' || echo "")

echo "==================== SQL 表集合一致性校验 ===================="
echo "schema.sql        : $schema_count 张"
echo "dsh_java.sql      : $dump_count 张"
echo "db/migration/*.sql: $migration_count 张"
echo "-------------------------------------------------------------"
echo "schema 独有（仅左有）: $only_schema"
echo "dump   独有（仅右有）: $only_dump"
echo "migration 未被两脚本覆盖: $only_migration"
echo "README.md 声明        : ${cn_declared:-<未找到>}"
echo "README.en.md 声明     : ${en_declared:-<未找到>}"
echo "============================================================="

fail=0

if [ "$only_schema" -ne 0 ] || [ "$only_dump" -ne 0 ]; then
    echo "[FAIL] schema.sql 与 dsh_java.sql 表集合不一致："
    comm -23 <(printf '%s\n' "$schema_tables") <(printf '%s\n' "$dump_tables") | sed 's/^/  仅 schema 有: /'
    comm -13 <(printf '%s\n' "$schema_tables") <(printf '%s\n' "$dump_tables") | sed 's/^/  仅 dump 有  : /'
    fail=1
fi

if [ "$only_migration" -ne 0 ]; then
    echo "[FAIL] db/migration 存在未被两脚本覆盖的表："
    comm -23 <(printf '%s\n' "$migration_tables") <(printf '%s\n' "$union_tables") | sed 's/^/  未被覆盖: /'
    fail=1
fi

if [ "$cn_declared" != "$schema_count" ]; then
    echo "[FAIL] README.md 声明 ($cn_declared) != schema.sql 实际 ($schema_count)"
    fail=1
fi

if [ "$en_declared" != "$schema_count" ]; then
    echo "[FAIL] README.en.md 声明 ($en_declared) != schema.sql 实际 ($schema_count)"
    fail=1
fi

if [ "$fail" -eq 0 ]; then
    echo "[PASS] 三方表集合一致，文档声明与事实一致。"
    exit 0
fi

exit 1