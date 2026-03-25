package com.heima.smartticket.Service.impl;


import com.heima.smartcommon.Context.BaseContext;
import com.heima.smartcommon.Context.TicketContext;
import com.heima.smartcommon.DTO.TicketCreateDTO;
import com.heima.smartcommon.DTO.TicketMessageDTO;
import com.heima.smartcommon.Exception.BusinessException;
import com.heima.smartcommon.Result.CommonResult;
import com.heima.smartcommon.Result.PageResult;
import com.heima.smartcommon.VO.TicketCreateVO;
import com.heima.smartcommon.enums.TicketStatus;
import com.heima.smartcommon.enums.TicketStateMachine;
import com.heima.smartticket.MQ.FirstUserMessageEvent;
import com.heima.smartticket.MQ.CommentCreatedEvent;
import com.heima.smartticket.Mapper.CommonUserMapper;
import com.heima.smartticket.Mapper.OutboxMessageMapper;
import com.heima.smartticket.Mapper.TicketMapper;
import com.heima.smartticket.Mapper.TicketMessageMapper;
import com.heima.smartticket.Service.TicketService;
import com.heima.smartticket.Service.NotificationService;
import com.heima.smartticket.Utils.TencentCosUtil;
import com.heima.smartticket.entity.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service

public class TicketServiceImpl implements TicketService {

    @Autowired
    private TicketMapper ticketMapper;
    @Autowired
    private TicketMessageMapper ticketMessageMapper;
    @Autowired
    private OutboxMessageMapper outboxMessageMapper;
    @Autowired
    private CommonUserMapper commonUserMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public TicketServiceImpl(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    @Autowired
    private TencentCosUtil tencentCosUtil;
    @Autowired
    private DefaultRedisScript<Long> allocateCsScript;

    private static final String HOT_RANK_KEY = "ticket:hot:rank";
    private static final String LOCK_KEY_PREFIX = "lock:ticket:create:";
    @Override
    @Transactional
    public CommonResult<String> submitTicket(TicketCreateDTO ticketCreateDTO) {
        if(ticketCreateDTO.getTitle()==null) throw new BusinessException("标题不能为空");
        if(ticketCreateDTO.getDescription()==null) throw new BusinessException("描述不能为空");
        if(ticketCreateDTO.getUserId()==null) throw new BusinessException("用户id不能为空");
        String key="ticket:"+ticketCreateDTO.getUserId();
        String lockKey = LOCK_KEY_PREFIX + ticketCreateDTO.getUserId();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new RuntimeException("请勿重复提交工单");
        }
        Ticket ticket = new Ticket();
        BeanUtils.copyProperties(ticketCreateDTO,ticket);
        ticket.setStatus(TicketStatus.NEW.name());
        Long id = ticketMapper.insert(ticket);
        TicketContext.setTicketId(id);
        return CommonResult.success(id);

    }

    @Override
    public CommonResult<PageResult> getTicket(Long UserId, Integer pageNum, Integer pageSize) {
        if(UserId==null){
            throw new BusinessException("用户id不能为空");
        }

        int offset= (pageNum - 1) * pageSize;
        List<TicketCreateVO> list = ticketMapper.getTicket(UserId, offset, pageSize);
        if (list == null || list.isEmpty()) {
            return CommonResult.success(new PageResult<>(0L, Collections.emptyList()));
        }
        Long total = ticketMapper.getTicketCount(UserId);
        System.out.println(total);
// 构建分页结果
        PageResult<List<TicketCreateVO>> pageResult = new PageResult<>(total, list);
        return CommonResult.success(pageResult);

    }

    @Override
    @Transactional
    public CommonResult<String> addMessage(TicketMessageDTO ticketMessage) {
        if(ticketMessage.getTicketId()==null){
            throw new BusinessException("工单id不能为空");
        }
        if(ticketMessage.getContent()==null){
            throw new BusinessException("内容不能为空");
        }

        Ticket ticket = ticketMapper.findById(ticketMessage.getTicketId());
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        // B：用户首次 addMessage 发消息时触发 AI 自动回复
        Long msgCount = ticketMessageMapper.countByTicketId(ticketMessage.getTicketId());
        boolean isFirstUserMessage =
                msgCount != null
                        && msgCount == 0
                        && ticketMessage.getSenderId() != null
                        && ticketMessage.getSenderId().equals(ticket.getUserId());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse(null);
        TicketMessage message = new TicketMessage(null,ticketMessage.getTicketId(),ticketMessage.getSenderId(),role,ticketMessage.getContent(),(short) 0,null);
        int messageid = addComment(message);

        // 自动更新工单状态
        try {
            if (role != null) {
                String senderType = role;
                // 将ADMIN也视为AGENT进行状态更新
                if ("ADMIN".equalsIgnoreCase(senderType)) {
                    senderType = "AGENT";
                }
                updateStatusByBusinessAction(ticketMessage.getTicketId(), senderType, ticketMessage.getContent());
            }
        } catch (Exception e) {
            System.err.println("自动更新工单状态失败: " + e.getMessage());
            // 不抛出异常，避免影响主流程
        }

        if (isFirstUserMessage) {
            eventPublisher.publishEvent(
                    new FirstUserMessageEvent(this, ticketMessage.getTicketId(), ticketMessage.getContent(),
                            ticketMessage.getImageUrl(), ticketMessage.getImageType())
            );
        }

        // 双向消息推送：根据发送者角色推送给对方
        try {
            if (role != null) {
                if (role.equalsIgnoreCase("USER")) {
                    // 用户发送消息，推送给分配的客服
                    if (ticket.getAssigneeId() != null) {
                        notificationService.notifyAgent(ticket.getAssigneeId(),
                                "用户[" + ticket.getUserId() + "] 发送新消息: " + ticketMessage.getContent());
                    }
                } else if (role.equalsIgnoreCase("AGENT")) {
                    // 客服发送消息，推送给用户
                    notificationService.notifyUser(ticket.getUserId(),
                            "客服[" + ticketMessage.getSenderId() + "] 回复: " + ticketMessage.getContent());
                }
                // AI消息不推送（senderType="AI"）
            }
        } catch (Exception e) {
            // 推送失败不影响主流程
            System.err.println("消息推送失败: " + e.getMessage());
        }

        return CommonResult.success(messageid);
    }

    @Override
    @Transactional
    public CommonResult<String> Assign(Long ticketId) {
        Long userId = BaseContext.getCurrentId();
        // 1 判断用户是否已有客服
        String sessionKey = "session:user:" + userId+":"+ticketId;

        Object exist = redisTemplate.opsForValue().get(sessionKey);

        if (exist != null) {
            return CommonResult.error("已指派客服");
        }

        // 2 调用 Lua 脚本
        Long agentId = (Long) redisTemplate.execute(
                allocateCsScript,
                Arrays.asList(
                        "agent:load",
                        "online:agent:",
                         sessionKey
                ),
                userId.toString()
        );
        if(agentId==null){
            return CommonResult.error("没有可用的客服");
        }
       ticketMapper.insertAssignId( ticketId, agentId);
        //通过websocket进行提示客服已经被分配

        // 自动更新工单状态：NEW -> ASSIGNED
        try {
            Ticket ticket = ticketMapper.findById(ticketId);
            if (ticket != null && TicketStatus.NEW.name().equals(ticket.getStatus())) {
                if (TicketStateMachine.canTransition(TicketStatus.NEW, TicketStatus.ASSIGNED)) {
                    ticket.setStatus(TicketStatus.ASSIGNED.name());
                    ticketMapper.update(ticket);
                    System.out.println("工单 " + ticketId + " 状态自动更新: NEW -> ASSIGNED");
                }
            }
        } catch (Exception e) {
            System.err.println("自动更新工单状态失败: " + e.getMessage());
            // 不抛出异常，避免影响主流程
        }

        return CommonResult.success("指派成功");
    }

    @Override
    @Transactional
    public CommonResult<String> updateStatus(Long ticketId, String status) {
        Ticket ticket = ticketMapper.findById(ticketId);
        if(ticket == null){
            throw new BusinessException("工单不存在");
        }

        // 验证状态有效性
        TicketStatus newStatus = TicketStatus.fromString(status);
        if (newStatus == null) {
            throw new BusinessException("无效的状态: " + status);
        }

        // 检查状态流转是否允许
        if (!TicketStateMachine.canTransition(TicketStatus.valueOf(ticket.getStatus()), newStatus)) {
            throw new BusinessException("状态流转不允许: 从 " + ticket.getStatus() + " 到 " + status);
        }

        // 保存旧状态用于负载减少判断
        String oldStatus = ticket.getStatus();

        // 更新状态
        ticket.setStatus(status);
        ticketMapper.update(ticket);

        // 负载减少机制：工单完成时减少客服负载
        // 只有当旧状态不是完成状态，且新状态是完成状态时才减少负载（避免重复减少）
        TicketStatus oldStatusEnum = TicketStatus.fromString(oldStatus);
        boolean isNewlyCompleted = (TicketStatus.RESOLVED.equals(newStatus) || TicketStatus.CLOSED.equals(newStatus));
        boolean wasAlreadyCompleted = (TicketStatus.RESOLVED.equals(oldStatusEnum) || TicketStatus.CLOSED.equals(oldStatusEnum));

        if (isNewlyCompleted && !wasAlreadyCompleted && ticket.getAssigneeId() != null) {
            reduceAgentLoad(ticket.getAssigneeId());
        }

        return CommonResult.success("更新成功");
    }

    @Override
    @Transactional
    public CommonResult<String> closeTicket(Long ticketId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        if (ticketId == null) {
            throw new BusinessException("工单id不能为空");
        }

        Ticket ticket = ticketMapper.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        // 验证是否是工单创建者
        if (!userId.equals(ticket.getUserId())) {
            throw new BusinessException("无权操作该工单");
        }

        // 检查当前状态是否可以关闭
        TicketStatus currentStatus = TicketStatus.fromString(ticket.getStatus());
        if (!TicketStateMachine.canTransition(currentStatus, TicketStatus.CLOSED)) {
            throw new BusinessException("当前状态不能关闭工单: " + ticket.getStatus());
        }

        // 检查是否是终态
        if (TicketStateMachine.isFinalStatus(currentStatus)) {
            throw new BusinessException("工单已结束，无需关闭");
        }

        // 更新状态为已关闭
        ticket.setStatus(TicketStatus.CLOSED.name());
        ticketMapper.update(ticket);

        // 减少客服负载（如果已分配客服）
        if (ticket.getAssigneeId() != null) {
            reduceAgentLoad(ticket.getAssigneeId());
        }

        return CommonResult.success("工单已关闭");
    }

    @Override
    @Transactional
    public CommonResult<String> resolveTicket(Long ticketId) {
        Long agentId = BaseContext.getCurrentId();
        if (agentId == null) {
            throw new BusinessException("用户未登录");
        }
        if (ticketId == null) {
            throw new BusinessException("工单id不能为空");
        }

        Ticket ticket = ticketMapper.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        // 验证是否是分配的客服或管理员
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse(null);

        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        boolean isAssignedAgent = agentId.equals(ticket.getAssigneeId());

        if (!isAdmin && !isAssignedAgent) {
            throw new BusinessException("无权操作该工单，只有分配的客服或管理员可以标记解决");
        }

        // 检查当前状态是否可以标记为已解决
        TicketStatus currentStatus = TicketStatus.fromString(ticket.getStatus());
        if (!TicketStateMachine.canTransition(currentStatus, TicketStatus.RESOLVED)) {
            throw new BusinessException("当前状态不能标记为已解决: " + ticket.getStatus());
        }

        // 检查是否是终态
        if (TicketStateMachine.isFinalStatus(currentStatus)) {
            throw new BusinessException("工单已结束");
        }

        // 更新状态为已解决
        ticket.setStatus(TicketStatus.RESOLVED.name());
        ticketMapper.update(ticket);

        // 减少客服负载
        if (ticket.getAssigneeId() != null) {
            reduceAgentLoad(ticket.getAssigneeId());
        }

        // 通知用户工单已解决
        try {
            notificationService.notifyUser(ticket.getUserId(),
                    "您的工单[#" + ticketId + "]已被客服标记为已解决");
        } catch (Exception e) {
            System.err.println("通知用户失败: " + e.getMessage());
        }

        return CommonResult.success("工单已标记为已解决");
    }

    @Override
    @Transactional
    public CommonResult<String> cancelTicket(Long ticketId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        if (ticketId == null) {
            throw new BusinessException("工单id不能为空");
        }

        Ticket ticket = ticketMapper.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        // 验证是否是工单创建者
        if (!userId.equals(ticket.getUserId())) {
            throw new BusinessException("无权操作该工单");
        }

        // 检查当前状态是否可以取消
        TicketStatus currentStatus = TicketStatus.fromString(ticket.getStatus());
        if (!TicketStateMachine.canTransition(currentStatus, TicketStatus.CANCELLED)) {
            throw new BusinessException("当前状态不能取消工单: " + ticket.getStatus());
        }

        // 检查是否是终态
        if (TicketStateMachine.isFinalStatus(currentStatus)) {
            throw new BusinessException("工单已结束，无需取消");
        }

        // 更新状态为已取消
        ticket.setStatus(TicketStatus.CANCELLED.name());
        ticketMapper.update(ticket);

        // 减少客服负载（如果已分配客服）
        if (ticket.getAssigneeId() != null) {
            reduceAgentLoad(ticket.getAssigneeId());
        }

        return CommonResult.success("工单已取消");
    }
    @Transactional
    public int addComment(TicketMessage ticketMessage){
        Long messageid=  ticketMessageMapper.insert(ticketMessage);
        OutboxMessage outboxMessage=new OutboxMessage();
        outboxMessage.setEventType("ticket.comment");
        outboxMessage.setPayload(messageid.toString());
        outboxMessage.setStatus("PENDING");
        outboxMessageMapper.insert(outboxMessage);
        eventPublisher.publishEvent(new CommentCreatedEvent(this,outboxMessage));
        return messageid.intValue();
    }
    public Boolean Messageexists(Long messageid){
        return ticketMessageMapper.exists(messageid);
    }

    @Override
    public CommonResult<PageResult> getAssignTicket(Long agentId, Integer pageNum, Integer pageSize) {
        if(agentId==null){
            throw new BusinessException("用户id不能为空");
        }

        int offset= (pageNum - 1) * pageSize;
        List<TicketCreateVO> list = ticketMapper.getAssignTicket(agentId, offset, pageSize);
        if (list == null || list.isEmpty()) {
            return CommonResult.success(new PageResult<>(0L, Collections.emptyList()));
        }
        Long total = ticketMapper.getAssignTicketCount(agentId);

// 构建分页结果
        PageResult<List<TicketCreateVO>> pageResult = new PageResult<>(total, list);
        return CommonResult.success(pageResult);
    }

    @Override
    public CommonResult<String> PostTicketAttachment(Long ticketId, MultipartFile file) {
        if(ticketId==null){
            throw new BusinessException("工单id不能为空");
        }
        if(file==null){
            throw new BusinessException("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String url= tencentCosUtil.uploadFile(file,originalFilename);
        TicketAttachment ticketAttachment = new TicketAttachment();
        ticketAttachment.setTicketId(ticketId);
        ticketAttachment.setFileName(originalFilename);
        ticketAttachment.setFileUrl(url);
        Long uerId= BaseContext.getCurrentId();
        ticketAttachment.setUploaderId(String.valueOf(uerId));
        ticketMapper.insertAttachment(ticketAttachment);
        return CommonResult.success(ticketAttachment.getId());
    }

    @Override
    public CommonResult<TicketVo> GetTicketAttachment(Long ticketId) {
        if(ticketId==null){
            throw new BusinessException("工单id不能为空");
        }
        List<TicketAttachment> ticketAttachment = ticketMapper.getAttachment(ticketId);
        if(ticketAttachment==null){
            return CommonResult.error("文件不存在");
        }
        TicketVo ticketVo = new TicketVo();
        ticketVo.setAttachments(ticketAttachment);
        String content= ticketMapper.findById(ticketId).getDescription();
        ticketVo.setContent(content);
        ticketVo.setSenderId(String.valueOf(ticketMapper.findById(ticketId).getUserId()));
        //增加热度
        incrementHot(ticketId);
        return CommonResult.success(ticketVo);
    }

    @Override
    public CommonResult<List<TicketMessage>> GetComment(Long ticketId) {
       if(ticketId==null){
           throw new BusinessException("工单id不能为空");
       }
       List<TicketMessage> ticketMessages = ticketMessageMapper.getComment(ticketId);
       return CommonResult.success(ticketMessages);
    }

    @Override
    public CommonResult<List<TicketCreateVO>> GetTopTickets() {
        Set<ZSetOperations.TypedTuple<Object>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(HOT_RANK_KEY, 0,  19);
        if (tuples == null || tuples.isEmpty()) {
            return CommonResult.success(Collections.emptyList());
        }
        // 提取 Ticket ID 列表
        List<Long> ticketIds = tuples.stream()
                .map(tuple -> {
                    Object value = tuple.getValue();
                    if (value instanceof String) {
                        return Long.valueOf((String) value);
                    } else if (value instanceof Long) {
                        return (Long) value;
                    } else {
                        throw new IllegalArgumentException("Unsupported value type in Redis ZSet: " + value);
                    }
                })
                .collect(Collectors.toList());
         List<TicketCreateVO> ticketVos = ticketMapper.getTickets(ticketIds);
        // 1️ 转成 Map，key 为 ticketId，value 为 TicketCreateVO
        Map<Long, TicketCreateVO> ticketMap = ticketVos.stream()
                .collect(Collectors.toMap(TicketCreateVO::getId, vo -> vo));

        // 2️ 按 Redis 排序的 ticketIds 顺序重新组装
        List<TicketCreateVO> sortedList = ticketIds.stream()
                .map(ticketMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return CommonResult.success(sortedList);


    }

    @Override
    public CommonResult<TicketOverviewVO> overview() {
        return null;
    }

    @Override
    public CommonResult<List <DailyTicketStatsVO>> daily() {
        return null;
    }

    @Override
    public CommonResult<List<UserTicketRankVO>> topUsers() {
        return null;
    }

    /** 每次有人查看工单详情时调用 */
    public void incrementHot(Long ticketId) {
        redisTemplate.opsForZSet().incrementScore(HOT_RANK_KEY, ticketId.toString(), 1);
    }

    /**
     * 根据业务动作自动更新工单状态
     * @param ticketId 工单ID
     * @param senderType 发送者类型 (USER, AGENT, AI)
     * @param messageContent 消息内容（用于判断是否解决问题）
     */
    public void updateStatusByBusinessAction(Long ticketId, String senderType, String messageContent) {
        try {
            Ticket ticket = ticketMapper.findById(ticketId);
            if (ticket == null) {
                System.err.println("工单不存在: " + ticketId);
                return;
            }

            TicketStatus currentStatus = TicketStatus.fromString(ticket.getStatus());
            if (currentStatus == null) {
                System.err.println("工单状态无效: " + ticket.getStatus());
                return;
            }

            TicketStatus newStatus = null;
            String senderTypeUpper = senderType != null ? senderType.toUpperCase() : "";

            // 根据发送者类型和当前状态决定新状态
            if ("USER".equals(senderTypeUpper)) {
                // 用户发送消息
                if (currentStatus == TicketStatus.WAITING_CUSTOMER) {
                    // 用户回复了客服的询问，需要客服继续处理
                    newStatus = TicketStatus.IN_PROGRESS;
                } else if (currentStatus == TicketStatus.NEW || currentStatus == TicketStatus.ASSIGNED ||
                          currentStatus == TicketStatus.IN_PROGRESS || currentStatus == TicketStatus.WAITING_AGENT) {
                    // 用户主动发送消息，等待客服回复
                    newStatus = TicketStatus.WAITING_AGENT;
                }
            } else if ("AGENT".equals(senderTypeUpper)) {
                // 客服发送消息
                if (currentStatus == TicketStatus.ASSIGNED) {
                    // 客服开始处理已分配的工单
                    newStatus = TicketStatus.IN_PROGRESS;
                } else if (currentStatus == TicketStatus.WAITING_AGENT) {
                    // 客服回复了用户的询问
                    newStatus = TicketStatus.IN_PROGRESS;
                } else if (currentStatus == TicketStatus.IN_PROGRESS || currentStatus == TicketStatus.WAITING_CUSTOMER) {
                    // 客服主动发送消息或询问用户，等待用户回复
                    newStatus = TicketStatus.WAITING_CUSTOMER;
                }

                // 检查客服是否在消息中表示问题已解决
                if (messageContent != null &&
                    (messageContent.contains("已解决") || messageContent.contains("处理完成") ||
                     messageContent.contains("已完成") || messageContent.contains("解决您的问题"))) {
                    // 客服明确表示问题已解决
                    newStatus = TicketStatus.RESOLVED;
                }
            } else if ("AI".equals(senderTypeUpper)) {
                // AI自动回复
                if (currentStatus == TicketStatus.NEW || currentStatus == TicketStatus.ASSIGNED) {
                    // AI首次回复，等待用户确认
                    newStatus = TicketStatus.WAITING_CUSTOMER;
                }
            }

            // 如果确定了新状态且与当前状态不同，则更新状态
            if (newStatus != null && newStatus != currentStatus) {
                // 检查状态流转是否允许
                if (TicketStateMachine.canTransition(currentStatus, newStatus)) {
                    ticket.setStatus(newStatus.name());
                    ticketMapper.update(ticket);
                    System.out.println("工单 " + ticketId + " 状态自动更新: " + currentStatus + " -> " + newStatus);

                    // 如果是完成状态，减少客服负载
                    if ((newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CLOSED)
                            && ticket.getAssigneeId() != null) {
                        reduceAgentLoad(ticket.getAssigneeId());
                    }
                } else {
                    System.err.println("工单状态流转不允许: " + currentStatus + " -> " + newStatus);
                }
            }
        } catch (Exception e) {
            System.err.println("自动更新工单状态失败: " + e.getMessage());
            // 不抛出异常，避免影响主业务逻辑
        }
    }

    /**
     * 减少客服负载
     */
    private void reduceAgentLoad(Long agentId) {
        try {
            // 检查客服是否还在负载集合中（可能已下线）
            Double currentScore = redisTemplate.opsForZSet().score("agent:load", agentId.toString());
            if (currentScore != null && currentScore > 0) {
                redisTemplate.opsForZSet().incrementScore("agent:load", agentId.toString(), -1);
                System.out.println("客服 " + agentId + " 负载减少，当前分数: " + (currentScore - 1));
            } else {
                // 客服不在负载集合中，可能已下线或负载为0
                System.out.println("客服 " + agentId + " 不在负载集合中或负载为0");
            }
        } catch (Exception e) {
            System.err.println("减少客服负载失败: " + e.getMessage());
            // 不抛出异常，避免影响主流程
        }
    }

}
