#!/usr/bin/env bash
# 端到端验证 v2（工单工作流 + 三引擎 + 自动调参）
# 用法：bash scripts/e2e.sh [BASE_URL]   默认 http://localhost:8090
# 前提：sys_config 已关验证码（sys.account.captchaEnabled=false）
set -uo pipefail
BASE="${1:-http://localhost:8090}"

step() { echo "== $1"; }
die()  { echo "FAIL: $1" >&2; exit 1; }
jget() { python -c "import json,sys;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

login() { # $1=user $2=pass -> echo token
  curl -s -X POST "$BASE/prod-api/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\",\"code\":\"\",\"uuid\":\"\"}" \
    | python -c "import json,sys;print(json.load(sys.stdin)['token'])" 2>/dev/null
}

step "1/11 管理台登录"
TOKEN=$(login admin admin123) || die "admin 登录失败"
AUTH="Authorization: Bearer $TOKEN"

step "2/11 重置演示数据（730 天两年史）"
R=$(curl -s -X POST "$BASE/prod-api/taihu/seed?days=730" -H "$AUTH")
echo "$R" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d;print('  ',d['data'])" || die "seed 失败: $R"

step "3/11 运行今日研判管线（含自动派单）"
R=$(curl -s -X POST "$BASE/prod-api/taihu/pipeline/run" -H "$AUTH")
echo "$R" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d;s=d['data']['steps'];print('  steps:',list(s.keys()))" || die "pipeline 失败: $R"

step "4/11 今日研判（六分区·同比定级+概率并列）"
R=$(curl -s "$BASE/prod-api/taihu/assess/today" -H "$AUTH")
echo "$R" | python -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('code')==200, d
z=d['data']['zones'] if isinstance(d['data'],dict) and 'zones' in d['data'] else d['data']
assert len(z)>=6, z
z0=z[0]
assert 'level' in json.dumps(z0), z0
print('  zones:',len(z),'| 首分区 keys:',sorted(z0.keys())[:8])" || die "assess/today 结构异常: $R"

step "5/11 查自动生成的待接单工单"
R=$(curl -s "$BASE/prod-api/taihu/orders/list?pageNum=1&pageSize=10&status=0" -H "$AUTH")
OID=$(echo "$R" | python -c "
import json,sys
d=json.load(sys.stdin)
rows=d.get('rows') or d.get('data',{}).get('rows') or []
print(rows[0]['orderId'] if rows else '')") || die "orders 查询失败"
[ -n "$OID" ] || die "无待接单工单（研判未达自动派单等级？）"
echo "  orderId=$OID"

step "6/11 片长 H5 登录并接单（派船+打捞员）"
# 按工单的 leaderId 反查片长账号（自动派单派给事发分区片长，不固定是 leader01）
LUSER=$(curl -s "$BASE/prod-api/taihu/orders/$OID" -H "$AUTH" -o _o.json; curl -s "$BASE/prod-api/taihu/workers/list?pageNum=1&pageSize=100" -H "$AUTH" | python -c "
import json,sys
o=json.load(open('_o.json'))['data']
lid=o.get('leaderId') or o.get('leader_id') or 0
d=json.load(sys.stdin)
rows=d.get('rows') or []
m=[r['username'] for r in rows if str(r.get('workerId'))==str(lid)]
print(m[0] if m else '')")
[ -n "$LUSER" ] || die "未定位到工单片长账号（leaderId=$LID）"
LT=$(login "$LUSER" admin123) || die "$LUSER 登录失败"
LH="Authorization: Bearer $LT"
echo "  片长=$LUSER"
R=$(curl -s "$BASE/prod-api/taihu/h5/my/orders?status=0" -H "$LH")
MY=$(echo "$R" | python -c "
import json,sys
d=json.load(sys.stdin)
rows=d.get('rows') or d.get('data') or []
ids=[str(r['orderId']) for r in rows]
print(','.join(ids))") || die "片长工单列表失败"
echo "$MY" | grep -q "$OID" || die "片长[$LUSER]工单列表无 $OID ($MY)"
BID=$(curl -s "$BASE/prod-api/taihu/boats/list?pageNum=1&pageSize=1" -H "$AUTH" | python -c "
import json,sys;d=json.load(sys.stdin);r=d.get('rows') or [];print(r[0]['boatId'] if r else 1)")
WID=$(curl -s "$BASE/prod-api/taihu/workers/list?pageNum=1&pageSize=100" -H "$AUTH" | python -c "
import json,sys
d=json.load(sys.stdin)
rows=d.get('rows') or []
m=[r for r in rows if r.get('username')=='worker01']
print(m[0]['workerId'] if m else (rows[0]['workerId'] if rows else ''))")
[ -n "$WID" ] || die "未找到打捞员 workerId"
R=$(curl -s -X PUT "$BASE/prod-api/taihu/h5/orders/$OID/accept" -H "$LH" -H 'Content-Type: application/json' \
  -d "{\"boatId\":$BID,\"crewIds\":\"$WID\"}")
echo "$R" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d" || die "接单失败: $R"

step "7/11 打捞员 H5 到场打卡 + 提交完成"
WT=$(login worker01 admin123) || die "worker01 登录失败"
WH="Authorization: Bearer $WT"
R=$(curl -s -X PUT "$BASE/prod-api/taihu/h5/orders/$OID/arrive" -H "$WH" -H 'Content-Type: application/json' \
  -d '{"lng":120.21,"lat":31.41}')
echo "$R" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d" || die "打卡失败: $R"
R=$(curl -s -X PUT "$BASE/prod-api/taihu/h5/orders/$OID/submit" -H "$WH" -H 'Content-Type: application/json' \
  -d '{"actualTons":25.5,"photoUrls":""}')
echo "$R" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d" || die "提交失败: $R"

step "8/11 管理台核查办结"
R=$(curl -s -X PUT "$BASE/prod-api/taihu/orders/$OID/check" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"pass":true,"checkComment":"e2e 核查通过"}')
echo "$R" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d" || die "核查失败: $R"

step "9/11 人工调参（阈值 50→60）并验证下轮研判生效"
R=$(curl -s -X PUT "$BASE/prod-api/taihu/params/assess.level2_chla" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"value":60,"operator":"e2e"}')
echo "$R" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d" || die "调参失败: $R"
curl -s -X POST "$BASE/prod-api/taihu/pipeline/run" -H "$AUTH" >/dev/null
R=$(curl -s "$BASE/prod-api/taihu/assess/today" -H "$AUTH")
echo "$R" | python -c "
import json,sys
d=json.load(sys.stdin)
s=json.dumps(d,ensure_ascii=False)
assert 'param_version' in s or 'paramVersion' in s, '缺少参数版本字段'
print('  参数版本字段存在 OK')" || die "调参后研判未带参数版本: $R"

step "10/11 自动调参（自我改进闭环）"
R=$(curl -s -X POST "$BASE/prod-api/taihu/autotune/run" -H "$AUTH")
echo "$R" | python -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('code')==200, d
print('  ', json.dumps(d.get('data'),ensure_ascii=False)[:200])" || die "autotune 失败: $R"
R=$(curl -s "$BASE/prod-api/taihu/autotune/history" -H "$AUTH")
echo "$R" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d;print('  history OK')" || die "autotune history 失败: $R"

step "11/11 考核看板与日报"
curl -s "$BASE/prod-api/taihu/dashboard?days=30" -H "$AUTH" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d;print('  dashboard OK')" || die "dashboard 失败"
curl -s "$BASE/prod-api/taihu/daily/report?date=$(date +%F)" -H "$AUTH" | python -c "import json,sys;d=json.load(sys.stdin);assert d.get('code')==200,d;print('  daily OK')" || die "daily 失败"

echo "ALL PASS (v2)"
