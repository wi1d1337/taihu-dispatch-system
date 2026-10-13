SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- taihu-dispatch-system 业务库脚本 v2（工单工作流版，2026-10-13 对齐 contracts.md v2）
-- 前置：先执行 ruoyi-backend/sql/ry_20260417.sql（若依系统表）
-- 内容：1) tz_ 业务表 DDL：保留 tz_zone/tz_boat/tz_station/tz_run，新增 tz_worker/
--          tz_work_order/tz_order_log/tz_assessment/tz_algo_param/tz_param_log
--       2) 废弃并删除 v1 表：tz_dispatch_order / tz_salvage_log / tz_forecast_record
--       3) 「太湖调度」v2 菜单与按钮权限（menu_id 2000 起）
--       4) 角色 taihu_manager(10)/taihu_h5(11)；账号 taihu(3)/leader01(4)/worker01(5)，密码均 admin123
-- 说明：admin 为超管免授权；本脚本可重复执行（先 drop / delete 再建）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、业务表
-- ----------------------------
drop table if exists tz_zone;
create table tz_zone (
  zone_id     bigint       not null auto_increment    comment '分区ID',
  zone_name   varchar(50)  not null                   comment '分区名',
  zone_code   varchar(20)  default null               comment '分区代码（MLW/ZSH/GH/HX/XBAY/NBAY）',
  area_km2    decimal(8,2) default null               comment '面积（平方公里）',
  boundary    json         default null               comment '边界 [[lng,lat],...] 外环 GeoJSON',
  prob_72h    decimal(5,2) default null               comment '研判等级冗余（v2：0-3 等级，algo 刷新）',
  tons_q25    decimal(10,2) default null              comment '预计可收藻 q25（吨/日）',
  tons_q50    decimal(10,2) default null              comment '预计可收藻 q50（吨/日）',
  chla        decimal(7,2) default null               comment '当前叶绿素a（ug/L）',
  create_by   varchar(64)  default ''                 comment '创建者',
  create_time datetime     default null               comment '创建时间',
  update_by   varchar(64)  default ''                 comment '更新者',
  update_time datetime     default null               comment '更新时间（算法侧刷新分区指标）',
  remark      varchar(500) default null               comment '备注',
  primary key (zone_id),
  unique key uk_zone_name (zone_name)
) engine=innodb auto_increment=1 comment = '打捞分区';

drop table if exists tz_boat;
create table tz_boat (
  boat_id     bigint       not null auto_increment    comment '船只ID',
  boat_name   varchar(50)  not null                   comment '船名',
  boat_type   varchar(20)  default null               comment '船只类型（打捞/机动/小型）',
  capacity_t  decimal(8,2) default null               comment '日处理能力（吨/日）',
  speed_kmh   decimal(5,1) default null               comment '航速（km/h）',
  fuel_l_km   decimal(5,2) default null               comment '油耗（L/km）',
  status      char(1)      default '0'                comment '状态（0可用 1维护）',
  home_port   varchar(50)  default null               comment '母港',
  create_by   varchar(64)  default ''                 comment '创建者',
  create_time datetime     default null               comment '创建时间',
  update_by   varchar(64)  default ''                 comment '更新者',
  update_time datetime     default null               comment '更新时间',
  remark      varchar(500) default null               comment '备注',
  primary key (boat_id)
) engine=innodb auto_increment=1 comment = '打捞船只';

drop table if exists tz_station;
create table tz_station (
  station_id     bigint       not null auto_increment  comment '分离站ID',
  station_name   varchar(50)  not null                 comment '站名',
  capacity_t_day decimal(8,2) default null             comment '日处理能力（吨/日）',
  status         char(1)      default '0'              comment '状态（0可用 1停用）',
  create_by   varchar(64)  default ''                 comment '创建者',
  create_time datetime     default null               comment '创建时间',
  update_by   varchar(64)  default ''                 comment '更新者',
  update_time datetime     default null               comment '更新时间',
  remark      varchar(500) default null               comment '备注',
  primary key (station_id)
) engine=innodb auto_increment=1 comment = '藻水分离站';

-- 人员（片长/打捞员）：username 关联 sys_user 登录名（H5）
drop table if exists tz_worker;
create table tz_worker (
  worker_id   bigint       not null auto_increment    comment '人员ID',
  worker_name varchar(50)  not null                   comment '姓名',
  role        char(1)      not null                   comment '角色（1片长 2打捞员/船长）',
  phone       varchar(20)  default null               comment '联系电话',
  zone_id     bigint       default null               comment '负责分区（片长）',
  team_name   varchar(50)  default null               comment '所属队伍/班组',
  username    varchar(64)  default null               comment '关联sys_user登录名（H5登录）',
  status      char(1)      default '0'                comment '状态（0在岗 1停用）',
  create_by   varchar(64)  default ''                 comment '创建者',
  create_time datetime     default null               comment '创建时间',
  update_by   varchar(64)  default ''                 comment '更新者',
  update_time datetime     default null               comment '更新时间',
  remark      varchar(500) default null               comment '备注',
  primary key (worker_id),
  key idx_worker_zone (zone_id),
  key idx_worker_username (username)
) engine=innodb auto_increment=1 comment = '作业人员';

-- 工单（核心表，两级派发）
drop table if exists tz_work_order;
create table tz_work_order (
  order_id       bigint        not null auto_increment comment '工单ID',
  order_no       varchar(32)   not null                comment '工单号 TZ20261014-0001',
  zone_id        bigint        not null                comment '分区ID',
  level          char(1)       not null                comment '藻情等级（1关注 2较大 3重大）',
  source         char(1)       default '1'             comment '来源（1算法自动 2人工创建）',
  assessment_id  bigint        default null            comment '来源研判记录ID',
  status         char(1)       default '0'             comment '状态（0待接单 1已接单 2进行中 3待核查 4已办结 5已退回 6已撤销）',
  leader_id      bigint        default null            comment '片长（首派对象）',
  boat_id        bigint        default null            comment '船只（片长接单时指定）',
  crew_ids       varchar(200)  default null            comment '打捞员ID逗号分隔',
  deadline       datetime      default null            comment '处置时限（按 level 定）',
  dispatched_at  datetime      default null            comment '派单时间',
  accepted_at    datetime      default null            comment '接单时间',
  arrived_at     datetime      default null            comment '到场时间',
  submitted_at   datetime      default null            comment '提交时间',
  checked_at     datetime      default null            comment '核查时间',
  actual_tons    decimal(8,2)  default null            comment '藻水湿重吨（完成时报）',
  photo_urls     varchar(500)  default null            comment '现场照片URL逗号分隔',
  arrive_lng     decimal(10,6) default null            comment '到场经度',
  arrive_lat     decimal(10,6) default null            comment '到场纬度',
  suggest_boats  json          default null            comment 'CP-SAT推荐派船方案[{boat_id,zone_id,tons}]',
  revoke_reason  varchar(200)  default null            comment '撤销原因',
  check_comment  varchar(200)  default null            comment '核查意见',
  model_version  varchar(40)   default null            comment '模型版本',
  param_version  int           default null            comment '参数版本',
  create_by      varchar(64)   default ''              comment '创建者',
  create_time    datetime      default null            comment '创建时间',
  update_by      varchar(64)   default ''              comment '更新者',
  update_time    datetime      default null            comment '更新时间',
  remark         varchar(500)  default null            comment '备注',
  primary key (order_id),
  unique key uk_order_no (order_no),
  key idx_wo_zone (zone_id),
  key idx_wo_status (status),
  key idx_wo_leader (leader_id),
  key idx_wo_create (create_time)
) engine=innodb auto_increment=1 comment = '打捞工单';

-- 工单流转日志（督办动作也记这：催办/改派/升级督办）
drop table if exists tz_order_log;
create table tz_order_log (
  log_id      bigint       not null auto_increment     comment '日志ID',
  order_id    bigint       not null                    comment '工单ID',
  action      varchar(30)  default null                comment '动作（派单/接单/派船/到场/提交/核查/办结/退回/撤销/催办/改派/升级督办）',
  operator    varchar(64)  default null                comment '操作人（系统动作为 督办系统）',
  from_status char(1)      default null                comment '原状态',
  to_status   char(1)      default null                comment '新状态',
  note        varchar(300) default null                comment '说明',
  created_at  datetime     default null                comment '记录时间',
  primary key (log_id),
  key idx_ol_order (order_id),
  key idx_ol_action_time (order_id, action, created_at)
) engine=innodb auto_increment=1 comment = '工单流转日志';

-- 研判记录（algo 写入，Java 侧查询）
drop table if exists tz_assessment;
create table tz_assessment (
  assessment_id bigint        not null auto_increment   comment '研判ID',
  assess_date   date          not null                  comment '研判日期',
  zone_id       bigint        not null                  comment '分区ID',
  level         char(1)       default null              comment '等级（0无需干预 1关注 2较大 3重大）',
  predicted     decimal(7,2)  default null              comment '同比外推预测Chl-a（ug/L）',
  yoy           decimal(7,2)  default null              comment '去年同期值',
  recent7       decimal(7,2)  default null              comment '近7日均值',
  trend         decimal(6,2)  default null              comment '近7日趋势%',
  basis         json          default null              comment '依据明细（阈值引用/超阈天数/同比偏差/参数快照）',
  confidence    decimal(4,2)  default null              comment '置信度',
  model_version varchar(40)   default null              comment '模型版本',
  param_version int           default null              comment '参数版本',
  created_at    datetime      default null              comment '记录时间',
  primary key (assessment_id),
  key idx_as_zone_date (zone_id, assess_date)
) engine=innodb auto_increment=1 comment = '研判记录';

-- 算法参数注册表（AI 动态调参接口位；调整走 API+审计+版本递增）
drop table if exists tz_algo_param;
create table tz_algo_param (
  param_id    bigint        not null auto_increment     comment '参数ID',
  param_key   varchar(64)   not null                    comment '参数键（区分大小写）',
  param_value decimal(12,4) not null                    comment '当前值',
  min_value   decimal(12,4) default null                comment '下限',
  max_value   decimal(12,4) default null                comment '上限',
  unit        varchar(20)   default null                comment '单位',
  meaning     varchar(200)  default null                comment '业务含义',
  updated_by  varchar(64)   default null                comment '更新人',
  updated_at  datetime      default null                comment '更新时间',
  version     int           default 1                   comment '版本（每次修改+1）',
  primary key (param_id),
  unique key uk_param_key (param_key)
) engine=innodb auto_increment=1 comment = '算法参数注册表';

-- 参数变更审计（algo 侧写入；Java 侧只读）
drop table if exists tz_param_log;
create table tz_param_log (
  log_id      bigint        not null auto_increment     comment '日志ID',
  param_key   varchar(64)   not null                    comment '参数键',
  old_value   decimal(12,4) default null                comment '原值',
  new_value   decimal(12,4) default null                comment '新值',
  old_version int           default null                comment '原版本',
  new_version int           default null                comment '新版本',
  `trigger`    char(1)       default null                comment '触发（1人工 2自动调参 3自动建议待确认）',
  evidence    json          default null                comment '证据（窗口命中率/POD/FAR/MAE/候选对比/理由）',
  operator    varchar(64)   default null                comment '操作人',
  created_at  datetime      default null                comment '记录时间',
  primary key (log_id),
  key idx_pl_key_time (param_key, created_at)
) engine=innodb auto_increment=1 comment = '参数变更审计';

-- 管线运行（algo 写入）
drop table if exists tz_run;
create table tz_run (
  run_id       bigint      not null auto_increment     comment '运行ID',
  run_date     date        not null                    comment '运行日期',
  step         varchar(30) default null                comment '步骤（ingest/assess/dispatch/report/autotune）',
  status       char(1)     default null                comment '状态（0运行 1成功 2失败）',
  started_at   datetime    default null                comment '开始时间',
  ended_at     datetime    default null                comment '结束时间',
  model_version varchar(40) default null               comment '模型版本',
  detail       text        default null                comment '运行详情',
  primary key (run_id),
  key idx_run_date (run_date)
) engine=innodb auto_increment=1 comment = '管线运行（算法服务写入）';

-- v1 废弃表（数据清空、模块下线）
drop table if exists tz_dispatch_order;
drop table if exists tz_salvage_log;
drop table if exists tz_forecast_record;

-- ----------------------------
-- 2、菜单与按钮权限（menu_id 2000+，v2 页面清单见契约 §4 末段）
-- ----------------------------
delete from sys_role_menu where menu_id between 2000 and 2099;
delete from sys_menu where menu_id between 2000 and 2099;

-- 一级目录
insert into sys_menu values('2000', '太湖调度', '0', '5', 'taihu', null, '', '', 1, 0, 'M', '0', '0', '', 'international', 'admin', sysdate(), '', null, '太湖调度目录');

-- 调度工作台（研判面板 + 今日工单总览）
insert into sys_menu values('2001', '调度工作台', '2000', '1', 'workbench', 'taihu/workbench/index', '', '', 1, 0, 'C', '0', '0', 'taihu:order:list', 'dashboard', 'admin', sysdate(), '', null, '调度工作台菜单');
insert into sys_menu values('2002', '工单撤销', '2001', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:revoke',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2003', '工单改派', '2001', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:reassign', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2004', '工单核查', '2001', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:check',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2005', '运行今日研判', '2001', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:pipeline:run', '#', 'admin', sysdate(), '', null, '跑当日管线');
insert into sys_menu values('2006', '重置演示数据', '2001', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:pipeline:run', '#', 'admin', sysdate(), '', null, 'seed');

-- 工单管理
insert into sys_menu values('2010', '工单管理', '2000', '2', 'orders', 'taihu/orders/index', '', '', 1, 0, 'C', '0', '0', 'taihu:order:list', 'list', 'admin', sysdate(), '', null, '工单管理菜单');
insert into sys_menu values('2011', '工单查询', '2010', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2012', '工单新增', '2010', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:add',     '#', 'admin', sysdate(), '', null, '人工创建工单');
insert into sys_menu values('2013', '工单撤销', '2010', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:revoke',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2014', '工单改派', '2010', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:reassign', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2015', '工单核查', '2010', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:check',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2016', '工单删除', '2010', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:remove',  '#', 'admin', sysdate(), '', null, '');

-- 研判面板
insert into sys_menu values('2020', '研判面板', '2000', '3', 'assess', 'taihu/assess/index', '', '', 1, 0, 'C', '0', '0', 'taihu:assess:list', 'eyeopen', 'admin', sysdate(), '', null, '研判面板菜单');

-- 算法参数（含自动调参区块）
insert into sys_menu values('2025', '算法参数', '2000', '4', 'params', 'taihu/params/index', '', '', 1, 0, 'C', '0', '0', 'taihu:param:list', 'edit',   'admin', sysdate(), '', null, '算法参数菜单');
insert into sys_menu values('2026', '参数修改', '2025', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:param:edit', '#', 'admin', sysdate(), '', null, '人工调参/立即自动调参');

-- 作业人员
insert into sys_menu values('2030', '作业人员', '2000', '5', 'workers', 'taihu/workers/index', '', '', 1, 0, 'C', '0', '0', 'taihu:worker:list', 'peoples', 'admin', sysdate(), '', null, '作业人员菜单');
insert into sys_menu values('2031', '人员查询', '2030', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:worker:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2032', '人员新增', '2030', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:worker:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2033', '人员修改', '2030', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:worker:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2034', '人员删除', '2030', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:worker:remove', '#', 'admin', sysdate(), '', null, '');

-- 分区
insert into sys_menu values('2040', '分区管理', '2000', '6', 'zones', 'taihu/zones/index', '', '', 1, 0, 'C', '0', '0', 'taihu:zone:list', 'tree', 'admin', sysdate(), '', null, '打捞分区菜单');
insert into sys_menu values('2041', '分区查询', '2040', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:zone:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2042', '分区新增', '2040', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:zone:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2043', '分区修改', '2040', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:zone:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2044', '分区删除', '2040', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:zone:remove', '#', 'admin', sysdate(), '', null, '');

-- 船只
insert into sys_menu values('2050', '船只管理', '2000', '7', 'boats', 'taihu/boats/index', '', '', 1, 0, 'C', '0', '0', 'taihu:boat:list', 'ship', 'admin', sysdate(), '', null, '打捞船只菜单');
insert into sys_menu values('2051', '船只查询', '2050', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:boat:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2052', '船只新增', '2050', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:boat:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2053', '船只修改', '2050', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:boat:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2054', '船只删除', '2050', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:boat:remove', '#', 'admin', sysdate(), '', null, '');

-- 分离站
insert into sys_menu values('2060', '分离站管理', '2000', '8', 'stations', 'taihu/stations/index', '', '', 1, 0, 'C', '0', '0', 'taihu:station:list', 'build', 'admin', sysdate(), '', null, '藻水分离站菜单');
insert into sys_menu values('2061', '分离站查询', '2060', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:station:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2062', '分离站新增', '2060', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:station:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2063', '分离站修改', '2060', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:station:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2064', '分离站删除', '2060', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:station:remove', '#', 'admin', sysdate(), '', null, '');

-- 考核看板 / 日报
insert into sys_menu values('2070', '考核看板', '2000', '9', 'dashboard', 'taihu/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'taihu:order:list', 'chart', 'admin', sysdate(), '', null, '考核看板菜单');
insert into sys_menu values('2080', '日报', '2000', '10', 'daily', 'taihu/daily/index', '', '', 1, 0, 'C', '0', '0', 'taihu:order:list', 'log', 'admin', sysdate(), '', null, '日报菜单');

-- H5 接口权限（隐藏菜单，仅挂权限字符给 taihu_h5 角色）
insert into sys_menu values('2090', 'H5移动端', '2000', '99', 'h5', null, '', '', 1, 0, 'M', '1', '0', '', 'phone', 'admin', sysdate(), '', null, 'H5接口权限（隐藏）');
insert into sys_menu values('2091', 'H5个人信息', '2090', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:h5:me',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2092', 'H5我的工单', '2090', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:h5:order:list',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2093', 'H5接单',     '2090', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:h5:order:accept',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2094', 'H5到场打卡', '2090', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:h5:order:arrive',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2095', 'H5提交完成', '2090', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:h5:order:submit',  '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 3、角色与账号
-- ----------------------------
delete from sys_user_role where role_id in (10, 11) or user_id in (3, 4, 5);
delete from sys_role_menu where role_id in (10, 11);
delete from sys_role where role_id in (10, 11);

-- 管理台角色：太湖调度全部页面与按钮
insert into sys_role values('10', '太湖调度管理员', 'taihu_manager', 10, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '太湖调度管理台演示角色');
insert into sys_role_menu values ('10', '2000');
insert into sys_role_menu values ('10', '2001');
insert into sys_role_menu values ('10', '2002');
insert into sys_role_menu values ('10', '2003');
insert into sys_role_menu values ('10', '2004');
insert into sys_role_menu values ('10', '2005');
insert into sys_role_menu values ('10', '2006');
insert into sys_role_menu values ('10', '2010');
insert into sys_role_menu values ('10', '2011');
insert into sys_role_menu values ('10', '2012');
insert into sys_role_menu values ('10', '2013');
insert into sys_role_menu values ('10', '2014');
insert into sys_role_menu values ('10', '2015');
insert into sys_role_menu values ('10', '2016');
insert into sys_role_menu values ('10', '2020');
insert into sys_role_menu values ('10', '2025');
insert into sys_role_menu values ('10', '2026');
insert into sys_role_menu values ('10', '2030');
insert into sys_role_menu values ('10', '2031');
insert into sys_role_menu values ('10', '2032');
insert into sys_role_menu values ('10', '2033');
insert into sys_role_menu values ('10', '2034');
insert into sys_role_menu values ('10', '2040');
insert into sys_role_menu values ('10', '2041');
insert into sys_role_menu values ('10', '2042');
insert into sys_role_menu values ('10', '2043');
insert into sys_role_menu values ('10', '2044');
insert into sys_role_menu values ('10', '2050');
insert into sys_role_menu values ('10', '2051');
insert into sys_role_menu values ('10', '2052');
insert into sys_role_menu values ('10', '2053');
insert into sys_role_menu values ('10', '2054');
insert into sys_role_menu values ('10', '2060');
insert into sys_role_menu values ('10', '2061');
insert into sys_role_menu values ('10', '2062');
insert into sys_role_menu values ('10', '2063');
insert into sys_role_menu values ('10', '2064');
insert into sys_role_menu values ('10', '2070');
insert into sys_role_menu values ('10', '2080');

-- H5 角色：h5 接口 + 船只/人员查询（接单时选船选人）
insert into sys_role values('11', '太湖H5作业人员', 'taihu_h5', 11, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '太湖H5片长/打捞员角色');
insert into sys_role_menu values ('11', '2031');
insert into sys_role_menu values ('11', '2051');
insert into sys_role_menu values ('11', '2091');
insert into sys_role_menu values ('11', '2092');
insert into sys_role_menu values ('11', '2093');
insert into sys_role_menu values ('11', '2094');
insert into sys_role_menu values ('11', '2095');

-- 演示账号（密码均 admin123，与 admin 同源 bcrypt 串）
delete from sys_user where user_id between 3 and 17;
insert into sys_user values(3, 103, 'taihu',    '太湖调度员', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '太湖调度演示账号（管理台）');
insert into sys_user values(4, 103, 'leader01', '片长演示',   '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5片长演示账号');
insert into sys_user values(5, 103, 'worker01', '打捞员演示', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5打捞员演示账号');
insert into sys_user_role values ('3', '10');
insert into sys_user_role values ('4', '11');
insert into sys_user_role values ('5', '11');
insert into sys_user values(6, 103, 'leader02', '片长演示02', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5片长演示账号');
insert into sys_user values(7, 103, 'leader03', '片长演示03', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5片长演示账号');
insert into sys_user values(8, 103, 'leader04', '片长演示04', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5片长演示账号');
insert into sys_user values(9, 103, 'leader05', '片长演示05', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5片长演示账号');
insert into sys_user values(10, 103, 'leader06', '片长演示06', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5片长演示账号');
insert into sys_user values(11, 103, 'worker02', '打捞员演示02', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5打捞员演示账号');
insert into sys_user values(12, 103, 'worker03', '打捞员演示03', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5打捞员演示账号');
insert into sys_user values(13, 103, 'worker04', '打捞员演示04', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5打捞员演示账号');
insert into sys_user values(14, 103, 'worker05', '打捞员演示05', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5打捞员演示账号');
insert into sys_user values(15, 103, 'worker06', '打捞员演示06', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5打捞员演示账号');
insert into sys_user values(16, 103, 'worker07', '打捞员演示07', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5打捞员演示账号');
insert into sys_user values(17, 103, 'worker08', '打捞员演示08', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, 'H5打捞员演示账号');
insert into sys_user_role values ('6', '11');
insert into sys_user_role values ('7', '11');
insert into sys_user_role values ('8', '11');
insert into sys_user_role values ('9', '11');
insert into sys_user_role values ('10', '11');
insert into sys_user_role values ('11', '11');
insert into sys_user_role values ('12', '11');
insert into sys_user_role values ('13', '11');
insert into sys_user_role values ('14', '11');
insert into sys_user_role values ('15', '11');
insert into sys_user_role values ('16', '11');
insert into sys_user_role values ('17', '11');

-- 作业人员（与演示账号 username 关联；zone 1 为 algo seed 的首个分区）
insert into tz_worker (worker_id, worker_name, role, phone, zone_id, team_name, username, status, create_by, create_time)
values (1, '张长河', '1', '13800000001', 1, '梅梁湖一队', 'leader01', '0', 'admin', sysdate()),
       (2, '王海生', '2', '13800000002', 1, '梅梁湖一队', 'worker01', '0', 'admin', sysdate());

-- ----------------------------
-- 4、算法参数初始集（key 区分大小写；algo seed 会以同键覆盖/补齐）
-- ----------------------------
insert into tz_algo_param (param_key, param_value, min_value, max_value, unit, meaning, updated_by, updated_at, version) values
('assess.level1_chla',    20, 10,  40, 'ug/L', '关注阈值（省预案一般档叶绿素a）', 'seed', sysdate(), 1),
('assess.level2_chla',    50, 30,  70, 'ug/L', '较大阈值（省预案较大档）',         'seed', sysdate(), 1),
('assess.level3_chla',    80, 60, 120, 'ug/L', '重大阈值（省预案重大档）',         'seed', sysdate(), 1),
('assess.yoy_halfwin',     7,  3,  15, '天',   '同比窗口：去年同期±N天',           'seed', sysdate(), 1),
('assess.yoy_weight',    0.6, 0.3, 0.9, '',    '同比基线权重（近7日趋势占0.4）',   'seed', sysdate(), 1),
('assess.exceed_days',     3,  1,   7, '天',   '近N日超阈天数计入升级',            'seed', sysdate(), 1),
('dispatch.auto_level',    2,  1,   3, '级',   '达到该等级及以上自动派单',         'seed', sysdate(), 1),
('dispatch.deadline_h_l2',24,  6,  72, '小时', '较大等级处置时限',                 'seed', sysdate(), 1),
('dispatch.deadline_h_l3', 4,  1,  24, '小时', '重大等级处置时限',                 'seed', sysdate(), 1),
('supervise.remind_h',     2,  0.5, 12, '小时', '超时多久开始催办',                'seed', sysdate(), 1),
('supervise.escalate_h',   6,  1,  48, '小时', '催办多久未动升级督办',             'seed', sysdate(), 1),
('autotune.enabled',       1,  0,   1, '',     '自动调参开关（1开 0关）',          'seed', sysdate(), 1),
('autotune.mode',          2,  1,   2, '',     '1=建议 2=自动（区间内直接生效）',  'seed', sysdate(), 1),
('autotune.window_days',  30,  7, 180, '天',   '回看窗口',                         'seed', sysdate(), 1),
('autotune.max_step_pct', 10,  1,  30, '%',    '单次调整最大幅度',                 'seed', sysdate(), 1),
('autotune.min_gain',      2,  0.5, 10, 'pp',  '最小改进阈值（命中率百分点）',     'seed', sysdate(), 1);
