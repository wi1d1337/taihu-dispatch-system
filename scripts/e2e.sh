#!/usr/bin/env bash
# 端到端验证：登录 → 种子 → 管线 → 调度单确认 → 台账回填 → 看板
# 用法：bash scripts/e2e.sh [BASE_URL]  （默认 http://localhost:8090）
set -uo pipefail
BASE="${1:-http://localhost:8090}"
TOKEN=""

step() { echo "== $1"; }
die()  { echo "FAIL: $1" >&2; exit 1; }

step "1/7 登录（admin/admin123）"
R=$(curl -s -X POST "$BASE/prod-api/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123","code":"","uuid":""}')
TOKEN=$(echo "$R" | python -c "import json,sys; print(json.load(sys.stdin)['token'])" 2>/dev/null) \
  || die "登录失败: $R"
AUTH="Authorization: Bearer $TOKEN"
echo "  token OK"

step "2/7 重置演示数据（seed，经若依代理）"
R=$(curl -s -X POST "$BASE/prod-api/taihu/seed?days=120" -H "$AUTH")
echo "$R" | python -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==200, d; print('  ', d['data'])" || die "seed 失败: $R"

step "3/7 触发每日管线"
R=$(curl -s -X POST "$BASE/prod-api/taihu/pipeline/run" -H "$AUTH")
echo "$R" | python -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==200, d; print('  steps:', list(d['data']['steps'].keys()))" || die "pipeline 失败: $R"

step "4/7 查询今日待确认调度单"
R=$(curl -s "$BASE/prod-api/taihu/orders/list?pageNum=1&pageSize=5&status=0" -H "$AUTH")
OID=$(echo "$R" | python -c "
import json,sys
d=json.load(sys.stdin)
rows=d.get('rows') or []
print(rows[0]['orderId'] if rows else '')") || die "orders 查询失败"
[ -n "$OID" ] || die "无待确认调度单"
echo "  orderId=$OID"

step "5/7 确认调度单 $OID"
R=$(curl -s -X PUT "$BASE/prod-api/taihu/orders/$OID/confirm" -H "$AUTH")
echo "$R" | python -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==200, d" || die "confirm 失败: $R"

step "6/7 台账回填（关联调度单，只填实收吨数）"
R=$(curl -s -X POST "$BASE/prod-api/taihu/logs" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"orderId\":$OID,\"actualTons\":18.5,\"fuelLiters\":42.0,\"crew\":5,\"remark\":\"e2e\"}")
echo "$R" | python -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==200, d" || die "log 回填失败: $R"

step "7/7 看板数据（POD/FAR/CSI）"
R=$(curl -s "$BASE/prod-api/taihu/dashboard?days=30" -H "$AUTH")
echo "$R" | python -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('code')==200, d
kpi=d['data']['kpi'] if isinstance(d['data'],dict) and 'kpi' in d['data'] else d['data']['eval']['kpi']
print('  KPI:', kpi)
assert kpi.get('POD') is not None" || die "dashboard 失败: $R"

echo "ALL PASS"
