package com.heima.smartticket.Service.impl;


import com.heima.smartcommon.Context.BaseContext;
import com.heima.smartcommon.Context.TicketContext;
import com.heima.smartcommon.DTO.TicketCreateDTO;
import com.heima.smartcommon.DTO.TicketMessageDTO;
import com.heima.smartcommon.Exception.BusinessException;
import com.heima.smartcommon.Result.CommonResult;
import com.heima.smartcommon.Result.PageResult;
import com.heima.smartcommon.VO.TicketCreateVO;
import com.heima.smartticket.MQ.FirstUserMessageEvent;
import com.heima.smartticket.MQ.CommentCreatedEvent;
import com.heima.smartticket.Mapper.CommonUserMapper;
import com.heima.smartticket.Mapper.OutboxMessageMapper;
import com.heima.smartticket.Mapper.TicketMapper;
import com.heima.smartticket.Mapper.TicketMessageMapper;
import com.heima.smartticket.Service.TicketService;
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
        ticket.setStatus("NEW");
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

        if (isFirstUserMessage) {
            eventPublisher.publishEvent(
                    new FirstUserMessageEvent(this, ticketMessage.getTicketId(), ticketMessage.getContent())
            );
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


        return CommonResult.success("指派成功");
    }

    @Override
    @Transactional
    public CommonResult<String> updateStatus(Long ticketId, String status) {
        Ticket ticket = ticketMapper.findById(ticketId);
        if(ticket==null){
            throw new BusinessException("工单不存在");
        }
        ticket.setStatus(status);
        ticketMapper.update(ticket);
        return CommonResult.success("更新成功");
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

}
