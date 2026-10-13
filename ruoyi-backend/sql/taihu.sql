-- ----------------------------------------------------------------------------
-- taihu-dispatch-system 业务库脚本（ry-vue）
-- 前置：先执行 ruoyi-backend/sql/ry_20260417.sql（若依系统表）
-- 内容：1) tz_ 业务表 DDL（含算法服务使用的 tz_forecast_record / tz_run）
--       2) 「太湖调度」菜单与按钮权限（menu_id 从 2000 起，避开若依默认段）
--       3) 演示角色 taihu_manager（role_id=10）与演示账号 taihu（user_id=3，密码 admin123）
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
  prob_72h    decimal(5,2) default null               comment '72h水华概率 0-1',
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
  create_by      varchar(64)  default ''               comment '创建者',
  create_time    datetime     default null             comment '创建时间',
  update_by      varchar(64)  default ''               comment '更新者',
  update_time    datetime     default null             comment '更新时间',
  remark         varchar(500) default null             comment '备注',
  primary key (station_id)
) engine=innodb auto_increment=1 comment = '藻水分离站';

-- 调度单：状态机 0待确认 → 1已确认 → 3已完成；0 → 2已驳回
drop table if exists tz_dispatch_order;
create table tz_dispatch_order (
  order_id     bigint        not null auto_increment   comment '调度单ID',
  order_date   date          not null                  comment '作业日期',
  half_day     char(1)       not null                  comment '半天（0上午 1下午）',
  boat_id      bigint        not null                  comment '船只ID',
  zone_id      bigint        not null                  comment '分区ID',
  station_id   bigint        default null              comment '分离站ID',
  plan_tons    decimal(8,2)  default null              comment '建议预计收藻（吨）',
  actual_tons  decimal(8,2)  default null              comment '实收（完成时回填）',
  status       char(1)       default '0'               comment '状态（0待确认 1已确认 2已驳回 3已完成）',
  solver       varchar(20)   default null              comment '求解器（CP_SAT/GREEDY）',
  objective    decimal(12,2) default null              comment '目标函数值',
  model_version varchar(40)  default null              comment '模型版本',
  run_id       bigint        default null              comment '管线运行ID（tz_run）',
  create_by    varchar(64)   default ''                comment '创建者',
  create_time  datetime      default null              comment '创建时间',
  update_by    varchar(64)   default ''                comment '更新者',
  update_time  datetime      default null              comment '更新时间',
  remark       varchar(200)  default null              comment '备注（驳回原因写这里）',
  primary key (order_id),
  key idx_order_date (order_date),
  key idx_order_status (status)
) engine=innodb auto_increment=1 comment = '调度单';

-- 打捞台账：全系统唯一人工录入点（带 order_id 时船/分区/日期/半天自动从调度单带出）
drop table if exists tz_salvage_log;
create table tz_salvage_log (
  log_id       bigint       not null auto_increment    comment '台账ID',
  order_id     bigint       default null               comment '关联调度单ID（可空=计划外作业）',
  boat_id      bigint       not null                   comment '船只ID',
  zone_id      bigint       not null                   comment '分区ID',
  log_date     date         not null                   comment '作业日期',
  half_day     char(1)      default null               comment '半天（0上午 1下午）',
  actual_tons  decimal(8,2) not null                   comment '实收吨数',
  fuel_liters  decimal(8,1) default null               comment '油耗（L）',
  crew         int          default null               comment '作业人数',
  photo_url    varchar(200) default null               comment '现场照片URL',
  lng          decimal(10,6) default null              comment '经度',
  lat          decimal(10,6) default null              comment '纬度',
  create_by    varchar(64)  default ''                 comment '创建者',
  create_time  datetime     default null               comment '创建时间',
  update_by    varchar(64)  default ''                 comment '更新者',
  update_time  datetime     default null               comment '更新时间',
  remark       varchar(200) default null               comment '备注',
  primary key (log_id),
  key idx_log_order (order_id),
  key idx_log_date (log_date)
) engine=innodb auto_increment=1 comment = '打捞台账';

-- 以下两表由算法服务（algo）写入，Java 侧只读/建表，供复盘与审计
drop table if exists tz_forecast_record;
create table tz_forecast_record (
  fc_id      bigint       not null auto_increment      comment '预报ID',
  zone_id    bigint       not null                     comment '分区ID',
  fc_date    date         not null                     comment '预报日期',
  horizon_h  int          default 72                   comment '预报时效（小时）',
  prob       decimal(5,2) default null                 comment '水华概率',
  chla_q25   decimal(7,2) default null                 comment '叶绿素a q25',
  chla_q50   decimal(7,2) default null                 comment '叶绿素a q50',
  chla_q75   decimal(7,2) default null                 comment '叶绿素a q75',
  create_time datetime    default null                 comment '创建时间',
  primary key (fc_id),
  key idx_fc_zone_date (zone_id, fc_date)
) engine=innodb auto_increment=1 comment = '预报记录（算法服务写入）';

drop table if exists tz_run;
create table tz_run (
  run_id       bigint      not null auto_increment     comment '运行ID',
  run_date     date        not null                    comment '运行日期',
  step         varchar(30) default null                comment '步骤（ingest/forecast/solve/report）',
  status       char(1)     default null                comment '状态（0运行 1成功 2失败）',
  started_at   datetime    default null                comment '开始时间',
  ended_at     datetime    default null                comment '结束时间',
  model_version varchar(40) default null               comment '模型版本',
  detail       text        default null                comment '运行详情',
  primary key (run_id),
  key idx_run_date (run_date)
) engine=innodb auto_increment=1 comment = '管线运行（算法服务写入，run_id 贯穿调度单）';

-- ----------------------------
-- 2、菜单与按钮权限（menu_id 2000+）
-- ----------------------------
delete from sys_role_menu where menu_id between 2000 and 2099;
delete from sys_menu where menu_id between 2000 and 2099;

-- 一级目录
insert into sys_menu values('2000', '太湖调度', '0', '5', 'taihu', null, '', '', 1, 0, 'M', '0', '0', '', 'international', 'admin', sysdate(), '', null, '太湖调度目录');

-- 调度工作台（页面聚合：地图 + 调度单卡片）
insert into sys_menu values('2001', '调度工作台', '2000', '1', 'workbench', 'taihu/workbench/index', '', '', 1, 0, 'C', '0', '0', '', 'dashboard',    'admin', sysdate(), '', null, '调度工作台菜单');
insert into sys_menu values('2002', '调度确认',   '2001', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:confirm',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2003', '调度驳回',   '2001', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:reject',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2004', '调度完成',   '2001', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:done',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2005', '生成调度单', '2001', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:pipeline:run',    '#', 'admin', sysdate(), '', null, '跑当日管线（含求解）');
insert into sys_menu values('2006', '重置演示',   '2001', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:pipeline:run',    '#', 'admin', sysdate(), '', null, '重置并生成演示数据');

-- 调度单
insert into sys_menu values('2010', '调度单', '2000', '2', 'orders', 'taihu/orders/index', '', '', 1, 0, 'C', '0', '0', 'taihu:order:list', 'list',    'admin', sysdate(), '', null, '调度单菜单');
insert into sys_menu values('2011', '调度查询', '2010', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2012', '调度新增', '2010', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:add',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2013', '调度修改', '2010', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:edit',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2014', '调度删除', '2010', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2015', '确认调度', '2010', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:confirm', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2016', '驳回调度', '2010', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:reject',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2017', '完成调度', '2010', '7', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:order:done',    '#', 'admin', sysdate(), '', null, '');

-- 打捞台账
insert into sys_menu values('2020', '打捞台账', '2000', '3', 'logs', 'taihu/logs/index', '', '', 1, 0, 'C', '0', '0', 'taihu:log:list', 'form', 'admin', sysdate(), '', null, '打捞台账菜单');
insert into sys_menu values('2021', '台账查询', '2020', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:log:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2022', '台账新增', '2020', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:log:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2023', '台账修改', '2020', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:log:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2024', '台账删除', '2020', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:log:remove', '#', 'admin', sysdate(), '', null, '');

-- 船只
insert into sys_menu values('2030', '船只管理', '2000', '4', 'boats', 'taihu/boats/index', '', '', 1, 0, 'C', '0', '0', 'taihu:boat:list', 'ship', 'admin', sysdate(), '', null, '打捞船只菜单');
insert into sys_menu values('2031', '船只查询', '2030', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:boat:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2032', '船只新增', '2030', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:boat:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2033', '船只修改', '2030', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:boat:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2034', '船只删除', '2030', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:boat:remove', '#', 'admin', sysdate(), '', null, '');

-- 分区
insert into sys_menu values('2040', '分区管理', '2000', '5', 'zones', 'taihu/zones/index', '', '', 1, 0, 'C', '0', '0', 'taihu:zone:list', 'tree', 'admin', sysdate(), '', null, '打捞分区菜单');
insert into sys_menu values('2041', '分区查询', '2040', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:zone:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2042', '分区新增', '2040', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:zone:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2043', '分区修改', '2040', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:zone:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2044', '分区删除', '2040', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:zone:remove', '#', 'admin', sysdate(), '', null, '');

-- 藻水分离站
insert into sys_menu values('2050', '分离站管理', '2000', '6', 'stations', 'taihu/stations/index', '', '', 1, 0, 'C', '0', '0', 'taihu:station:list', 'build', 'admin', sysdate(), '', null, '藻水分离站菜单');
insert into sys_menu values('2051', '分离站查询', '2050', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:station:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2052', '分离站新增', '2050', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:station:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2053', '分离站修改', '2050', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:station:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2054', '分离站删除', '2050', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'taihu:station:remove', '#', 'admin', sysdate(), '', null, '');

-- 运行看板
insert into sys_menu values('2060', '运行看板', '2000', '7', 'dashboard', 'taihu/dashboard/index', '', '', 1, 0, 'C', '0', '0', '', 'chart', 'admin', sysdate(), '', null, '运行看板菜单');

-- ----------------------------
-- 3、演示角色与账号（admin 超管免授权）
-- ----------------------------
delete from sys_user_role where role_id = 10;
delete from sys_role_menu where role_id = 10;
delete from sys_role where role_id = 10;

insert into sys_role values('10', '太湖调度管理员', 'taihu_manager', 10, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '太湖调度演示角色');

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
insert into sys_role_menu values ('10', '2017');
insert into sys_role_menu values ('10', '2020');
insert into sys_role_menu values ('10', '2021');
insert into sys_role_menu values ('10', '2022');
insert into sys_role_menu values ('10', '2023');
insert into sys_role_menu values ('10', '2024');
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

-- 演示账号 taihu（密码 admin123，与 admin 同源 bcrypt 串）；仅授权太湖业务，不授系统管理
delete from sys_user where user_id = 3;
insert into sys_user values(3, 103, 'taihu', '太湖调度员', '00', '', '', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '太湖调度演示账号');
insert into sys_user_role values ('3', '10');
