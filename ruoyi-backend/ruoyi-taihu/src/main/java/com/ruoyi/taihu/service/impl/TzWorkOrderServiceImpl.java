package com.ruoyi.taihu.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.taihu.domain.TzOrderLog;
import com.ruoyi.taihu.domain.TzWorker;
import com.ruoyi.taihu.domain.TzWorkOrder;
import com.ruoyi.taihu.mapper.TzOrderLogMapper;
import com.ruoyi.taihu.mapper.TzWorkOrderMapper;
import com.ruoyi.taihu.mapper.TzWorkerMapper;
import com.ruoyi.taihu.service.ITzAlgoParamService;
import com.ruoyi.taihu.service.ITzWorkOrderService;

/**
 * 打捞工单 服务实现（状态机校验在本层）
 *
 * @author taihu
 */
@Service
public class TzWorkOrderServiceImpl implements ITzWorkOrderService
{
    /** level=3 重大处置时限参数键 */
    private static final String KEY_DEADLINE_L3 = "dispatch.deadline_h_l3";

    /** level=2 较大处置时限参数键 */
    private static final String KEY_DEADLINE_L2 = "dispatch.deadline_h_l2";

    /** 关注等级（level=1）处置时限缺省（小时），无对应参数 */
    private static final BigDecimal DEFAULT_DEADLINE_L1_H = BigDecimal.valueOf(48);

    @Autowired
    private TzWorkOrderMapper tzWorkOrderMapper;

    @Autowired
    private TzOrderLogMapper tzOrderLogMapper;

    @Autowired
    private TzWorkerMapper tzWorkerMapper;

    @Autowired
    private ITzAlgoParamService tzAlgoParamService;

    @Override
    public List<TzWorkOrder> selectWorkOrderList(TzWorkOrder tzWorkOrder)
    {
        return tzWorkOrderMapper.selectWorkOrderList(tzWorkOrder);
    }

    @Override
    public TzWorkOrder selectWorkOrderById(Long orderId)
    {
        return tzWorkOrderMapper.selectWorkOrderById(orderId);
    }

    @Override
    public List<TzWorkOrder> selectActiveOrders()
    {
        return tzWorkOrderMapper.selectActiveOrders();
    }

    @Override
    @Transactional
    public int insertWorkOrder(TzWorkOrder order)
    {
        validateLevel(order.getLevel());
        Date now = DateUtils.getNowDate();
        order.setSource("2");
        order.setStatus(TzWorkOrder.STATUS_PENDING);
        order.setOrderNo(generateOrderNo(now));
        if (order.getDeadline() == null)
        {
            order.setDeadline(resolveDeadline(order.getLevel(), now));
        }
        order.setDispatchedAt(now);
        order.setCreateTime(now);
        if (order.getLeaderId() == null)
        {
            TzWorker leader = tzWorkerMapper.selectCandidateLeader(order.getZoneId(), 0L);
            if (leader == null)
            {
                throw new ServiceException("该分区没有在岗片长，请先指定片长或维护作业人员");
            }
            order.setLeaderId(leader.getWorkerId());
        }
        else
        {
            requireLeader(order.getLeaderId());
        }
        int rows = tzWorkOrderMapper.insertWorkOrder(order);
        insertLog(order.getOrderId(), TzOrderLog.ACTION_DISPATCH, order.getCreateBy(), null,
                TzWorkOrder.STATUS_PENDING, "人工创建工单，派给片长[" + leaderName(order.getLeaderId()) + "]");
        return rows;
    }

    @Override
    @Transactional
    public int revokeOrder(Long orderId, String revokeReason, String operator)
    {
        if (StringUtils.isEmpty(revokeReason))
        {
            throw new ServiceException("撤销原因必填");
        }
        TzWorkOrder order = requireOrder(orderId);
        if (!TzWorkOrder.STATUS_PENDING.equals(order.getStatus())
                && !TzWorkOrder.STATUS_ACCEPTED.equals(order.getStatus()))
        {
            throw new ServiceException("当前状态[" + order.getStatusLabel() + "]不允许撤销，仅待接单/已接单可撤");
        }
        TzWorkOrder update = baseUpdate(orderId, operator, TzWorkOrder.STATUS_REVOKED);
        update.setRevokeReason(revokeReason);
        int rows = tzWorkOrderMapper.updateWorkOrder(update);
        insertLog(orderId, TzOrderLog.ACTION_REVOKE, operator, order.getStatus(),
                TzWorkOrder.STATUS_REVOKED, revokeReason);
        return rows;
    }

    @Override
    @Transactional
    public int reassignOrder(Long orderId, Long leaderId, String operator, String note)
    {
        if (leaderId == null)
        {
            throw new ServiceException("改派片长必填");
        }
        TzWorkOrder order = requireOrder(orderId);
        if (!TzWorkOrder.STATUS_PENDING.equals(order.getStatus())
                && !TzWorkOrder.STATUS_ACCEPTED.equals(order.getStatus()))
        {
            throw new ServiceException("当前状态[" + order.getStatusLabel() + "]不允许改派，仅待接单/已接单可改派");
        }
        TzWorker leader = requireLeader(leaderId);
        Date now = DateUtils.getNowDate();
        TzWorkOrder update = baseUpdate(orderId, operator, TzWorkOrder.STATUS_PENDING);
        update.setLeaderId(leaderId);
        update.setDispatchedAt(now);
        tzWorkOrderMapper.updateWorkOrder(update);
        // 清船只/打捞员/接单时间，回两级派发第一级
        TzWorkOrder clear = new TzWorkOrder();
        clear.setOrderId(orderId);
        clear.setUpdateBy(operator);
        clear.setUpdateTime(now);
        tzWorkOrderMapper.clearAssignment(clear);
        String logNote = StringUtils.isNotEmpty(note) ? note : "人工改派";
        insertLog(orderId, TzOrderLog.ACTION_REASSIGN, operator, order.getStatus(),
                TzWorkOrder.STATUS_PENDING, logNote + "，改派给片长[" + leader.getWorkerName() + "]");
        return 1;
    }

    @Override
    @Transactional
    public int checkOrder(Long orderId, boolean pass, String checkComment, String operator)
    {
        TzWorkOrder order = requireOrder(orderId);
        if (!TzWorkOrder.STATUS_SUBMITTED.equals(order.getStatus()))
        {
            throw new ServiceException("当前状态[" + order.getStatusLabel() + "]不可核查，仅待核查工单可核查");
        }
        if (!pass && StringUtils.isEmpty(checkComment))
        {
            throw new ServiceException("退回必须填写核查意见");
        }
        String toStatus = pass ? TzWorkOrder.STATUS_DONE : TzWorkOrder.STATUS_REJECTED;
        TzWorkOrder update = baseUpdate(orderId, operator, toStatus);
        update.setCheckComment(checkComment);
        int rows = tzWorkOrderMapper.updateWorkOrder(update);
        String action = pass ? TzOrderLog.ACTION_CHECK : TzOrderLog.ACTION_REJECT;
        String note = pass ? "核查合格办结" : "核查不合格退回：" + checkComment;
        insertLog(orderId, action, operator, order.getStatus(), toStatus, note);
        if (pass)
        {
            insertLog(orderId, TzOrderLog.ACTION_DONE, operator, toStatus, toStatus, "工单办结");
        }
        return rows;
    }

    @Override
    @Transactional
    public int acceptOrder(Long orderId, Long boatId, String crewIds, String operator)
    {
        TzWorkOrder order = requireOrder(orderId);
        if (!TzWorkOrder.STATUS_PENDING.equals(order.getStatus()))
        {
            throw new ServiceException("当前状态[" + order.getStatusLabel() + "]不可接单，仅待接单工单可接单");
        }
        // 身份校验：接单人必须是工单片长本人
        TzWorker worker = requireWorkerByUsername(operator);
        if (!Objects.equals(worker.getWorkerId(), order.getLeaderId()))
        {
            throw new ServiceException("仅工单指派片长[" + leaderName(order.getLeaderId()) + "]可接单");
        }
        if (boatId == null)
        {
            boatId = firstSuggestBoatId(order.getSuggestBoats());
            if (boatId == null)
            {
                throw new ServiceException("请指定船只（本单无推荐派船方案）");
            }
        }
        List<TzWorker> crew = validateCrew(crewIds);
        Date now = DateUtils.getNowDate();
        TzWorkOrder update = baseUpdate(orderId, operator, TzWorkOrder.STATUS_ACCEPTED);
        update.setBoatId(boatId);
        update.setCrewIds(normalizeCrewIds(crewIds));
        update.setAcceptedAt(now);
        int rows = tzWorkOrderMapper.updateWorkOrder(update);
        insertLog(orderId, TzOrderLog.ACTION_ACCEPT, operator, order.getStatus(),
                TzWorkOrder.STATUS_ACCEPTED, "片长接单");
        insertLog(orderId, TzOrderLog.ACTION_ASSIGN_BOAT, operator, TzWorkOrder.STATUS_ACCEPTED,
                TzWorkOrder.STATUS_ACCEPTED, "派船#" + boatId + "，打捞员：" + crewLabel(crew));
        return rows;
    }

    @Override
    @Transactional
    public int arriveOrder(Long orderId, BigDecimal lng, BigDecimal lat, String operator)
    {
        if (lng == null || lat == null)
        {
            throw new ServiceException("到场打卡必须上报经纬度");
        }
        TzWorkOrder order = requireOrder(orderId);
        if (!TzWorkOrder.STATUS_ACCEPTED.equals(order.getStatus()))
        {
            throw new ServiceException("当前状态[" + order.getStatusLabel() + "]不可到场打卡，仅已接单工单可打卡");
        }
        TzWorker worker = requireWorkerByUsername(operator);
        requireCrewMember(order, worker);
        Date now = DateUtils.getNowDate();
        TzWorkOrder update = baseUpdate(orderId, operator, TzWorkOrder.STATUS_WORKING);
        update.setArriveLng(lng);
        update.setArriveLat(lat);
        update.setArrivedAt(now);
        int rows = tzWorkOrderMapper.updateWorkOrder(update);
        insertLog(orderId, TzOrderLog.ACTION_ARRIVE, operator, order.getStatus(),
                TzWorkOrder.STATUS_WORKING, "到场打卡(" + lng + "," + lat + ")");
        return rows;
    }

    @Override
    @Transactional
    public int submitOrder(Long orderId, BigDecimal actualTons, String photoUrls, String operator)
    {
        if (actualTons == null || actualTons.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("藻水湿重吨必填且须大于0");
        }
        TzWorkOrder order = requireOrder(orderId);
        boolean resubmit = TzWorkOrder.STATUS_REJECTED.equals(order.getStatus());
        if (!TzWorkOrder.STATUS_WORKING.equals(order.getStatus()) && !resubmit)
        {
            throw new ServiceException("当前状态[" + order.getStatusLabel() + "]不可提交，仅进行中/已退回工单可提交");
        }
        TzWorker worker = requireWorkerByUsername(operator);
        requireCrewMember(order, worker);
        Date now = DateUtils.getNowDate();
        TzWorkOrder update = baseUpdate(orderId, operator, TzWorkOrder.STATUS_SUBMITTED);
        update.setActualTons(actualTons);
        update.setPhotoUrls(photoUrls);
        update.setSubmittedAt(now);
        int rows = tzWorkOrderMapper.updateWorkOrder(update);
        String note = resubmit ? "整改重报：" + actualTons + "吨" : "提交完成：" + actualTons + "吨";
        if (StringUtils.isNotEmpty(photoUrls))
        {
            note += "，附照片";
        }
        insertLog(orderId, TzOrderLog.ACTION_SUBMIT, operator, order.getStatus(),
                TzWorkOrder.STATUS_SUBMITTED, note);
        return rows;
    }

    @Override
    @Transactional
    public int deleteWorkOrderByIds(Long[] orderIds)
    {
        for (Long orderId : orderIds)
        {
            TzWorkOrder order = tzWorkOrderMapper.selectWorkOrderById(orderId);
            if (order != null && !TzWorkOrder.STATUS_DONE.equals(order.getStatus())
                    && !TzWorkOrder.STATUS_REVOKED.equals(order.getStatus()))
            {
                throw new ServiceException("工单[" + order.getOrderNo() + "]当前状态["
                        + order.getStatusLabel() + "]非终态，不可删除");
            }
        }
        tzOrderLogMapper.deleteLogsByOrderIds(orderIds);
        return tzWorkOrderMapper.deleteWorkOrderByIds(orderIds);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 状态流转基础更新体（状态+审计）
     */
    private TzWorkOrder baseUpdate(Long orderId, String operator, String toStatus)
    {
        TzWorkOrder update = new TzWorkOrder();
        update.setOrderId(orderId);
        update.setStatus(toStatus);
        update.setUpdateBy(operator);
        update.setUpdateTime(DateUtils.getNowDate());
        if (TzWorkOrder.STATUS_DONE.equals(toStatus))
        {
            update.setCheckedAt(DateUtils.getNowDate());
        }
        return update;
    }

    /**
     * 必须存在该工单
     */
    private TzWorkOrder requireOrder(Long orderId)
    {
        TzWorkOrder order = orderId == null ? null : tzWorkOrderMapper.selectWorkOrderById(orderId);
        if (order == null)
        {
            throw new ServiceException("工单不存在：" + orderId);
        }
        return order;
    }

    /**
     * 必须是在岗片长
     */
    private TzWorker requireLeader(Long leaderId)
    {
        TzWorker leader = leaderId == null ? null : tzWorkerMapper.selectWorkerById(leaderId);
        if (leader == null || !leader.isLeader() || !"0".equals(leader.getStatus()))
        {
            throw new ServiceException("指定片长不存在或不是在岗片长：" + leaderId);
        }
        return leader;
    }

    /**
     * H5 操作人必须映射到在岗作业人员
     */
    private TzWorker requireWorkerByUsername(String username)
    {
        TzWorker worker = tzWorkerMapper.selectWorkerByUsername(username);
        if (worker == null)
        {
            throw new ServiceException("当前账号未关联在岗作业人员（tz_worker.username）");
        }
        return worker;
    }

    /**
     * 打捞员必须在工单 crew_ids 中
     */
    private void requireCrewMember(TzWorkOrder order, TzWorker worker)
    {
        if (StringUtils.isEmpty(order.getCrewIds()))
        {
            throw new ServiceException("本工单尚未指派打捞员");
        }
        boolean inCrew = Arrays.stream(order.getCrewIds().split(","))
                .map(String::trim)
                .anyMatch(id -> id.equals(String.valueOf(worker.getWorkerId())));
        if (!inCrew)
        {
            throw new ServiceException("仅本工单指派的打捞员可操作");
        }
    }

    /**
     * 校验打捞员清单：非空、存在、在岗
     */
    private List<TzWorker> validateCrew(String crewIds)
    {
        if (StringUtils.isEmpty(crewIds))
        {
            throw new ServiceException("请至少指派一名打捞员");
        }
        List<TzWorker> crew = new ArrayList<>();
        for (String id : crewIds.split(","))
        {
            String trim = id.trim();
            if (trim.isEmpty())
            {
                continue;
            }
            long workerId;
            try
            {
                workerId = Long.parseLong(trim);
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("打捞员ID非法：" + trim);
            }
            TzWorker worker = tzWorkerMapper.selectWorkerById(workerId);
            if (worker == null || !"0".equals(worker.getStatus()))
            {
                throw new ServiceException("打捞员不存在或已停用：" + trim);
            }
            crew.add(worker);
        }
        if (crew.isEmpty())
        {
            throw new ServiceException("请至少指派一名打捞员");
        }
        return crew;
    }

    /**
     * 去空白归一化 crew_ids（"1, 2" → "1,2"）
     */
    private String normalizeCrewIds(String crewIds)
    {
        StringBuilder sb = new StringBuilder();
        for (String id : crewIds.split(","))
        {
            String trim = id.trim();
            if (!trim.isEmpty())
            {
                if (sb.length() > 0)
                {
                    sb.append(',');
                }
                sb.append(trim);
            }
        }
        return sb.toString();
    }

    /**
     * 打捞员日志展示名（"张三/李四"）
     */
    private String crewLabel(List<TzWorker> crew)
    {
        StringBuilder sb = new StringBuilder();
        for (TzWorker worker : crew)
        {
            if (sb.length() > 0)
            {
                sb.append('/');
            }
            sb.append(worker.getWorkerName()).append('#').append(worker.getWorkerId());
        }
        return sb.toString();
    }

    /**
     * 片长姓名
     */
    private String leaderName(Long leaderId)
    {
        TzWorker leader = leaderId == null ? null : tzWorkerMapper.selectWorkerById(leaderId);
        return leader == null ? String.valueOf(leaderId) : leader.getWorkerName();
    }

    /**
     * suggest_boats 首条 boat_id（CP-SAT 推荐预填）
     */
    private Long firstSuggestBoatId(String suggestBoats)
    {
        if (StringUtils.isEmpty(suggestBoats))
        {
            return null;
        }
        try
        {
            JSONArray array = JSONArray.parseArray(suggestBoats);
            if (array != null && !array.isEmpty())
            {
                JSONObject first = array.getJSONObject(0);
                Long boatId = first.getLong("boat_id");
                if (boatId == null)
                {
                    boatId = first.getLong("boatId");
                }
                return boatId;
            }
        }
        catch (Exception ignore)
        {
        }
        return null;
    }

    /**
     * 生成工单号：TZyyyyMMdd-NNNN
     */
    private String generateOrderNo(Date now)
    {
        String prefix = "TZ" + DateUtils.parseDateToStr("yyyyMMdd", now) + "-";
        String max = tzWorkOrderMapper.selectMaxOrderNo(prefix);
        int seq = 1;
        if (StringUtils.isNotEmpty(max))
        {
            try
            {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            }
            catch (Exception e)
            {
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq);
    }

    /**
     * 按等级定处置时限（从 tz_algo_param 直读，缺省 3级4h/2级24h/1级48h）
     */
    private Date resolveDeadline(String level, Date base)
    {
        BigDecimal hours;
        switch (level)
        {
            case "3":
                hours = tzAlgoParamService.getDecimalValue(KEY_DEADLINE_L3, BigDecimal.valueOf(4));
                break;
            case "2":
                hours = tzAlgoParamService.getDecimalValue(KEY_DEADLINE_L2, BigDecimal.valueOf(24));
                break;
            default:
                hours = DEFAULT_DEADLINE_L1_H;
                break;
        }
        return new Date(base.getTime() + (long) (hours.doubleValue() * 3600_000L));
    }

    /**
     * 等级合法性
     */
    private void validateLevel(String level)
    {
        if (!"1".equals(level) && !"2".equals(level) && !"3".equals(level))
        {
            throw new ServiceException("藻情等级必须为 1关注 / 2较大 / 3重大");
        }
    }

    /**
     * 写流转日志
     */
    private void insertLog(Long orderId, String action, String operator, String fromStatus, String toStatus, String note)
    {
        TzOrderLog log = new TzOrderLog();
        log.setOrderId(orderId);
        log.setAction(action);
        log.setOperator(operator);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setNote(note);
        tzOrderLogMapper.insertOrderLog(log);
    }
}
