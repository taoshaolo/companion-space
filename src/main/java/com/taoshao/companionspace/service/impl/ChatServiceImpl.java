package com.taoshao.companionspace.service.impl;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.taoshao.companionspace.common.ErrorCode;
import com.taoshao.companionspace.contant.ChatConstant;
import com.taoshao.companionspace.contant.UserConstant;
import com.taoshao.companionspace.exception.BusinessException;
import com.taoshao.companionspace.mapper.ChatMapper;
import com.taoshao.companionspace.model.entity.Chat;
import com.taoshao.companionspace.model.entity.Team;
import com.taoshao.companionspace.model.entity.User;
import com.taoshao.companionspace.model.request.ChatRequest;
import com.taoshao.companionspace.model.vo.MessageVo;
import com.taoshao.companionspace.model.vo.WebSocketVo;
import com.taoshao.companionspace.service.ChatService;
import com.taoshao.companionspace.service.TeamService;
import com.taoshao.companionspace.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author taoshao
 * @description 针对表【chat(聊天消息表)】的数据库操作Service实现
 * @createDate 2023-04-11 11:19:33
 */
@Service
public class ChatServiceImpl extends ServiceImpl<ChatMapper, Chat>
        implements ChatService {

    @Resource
    private RedisTemplate<String, List<MessageVo>> redisTemplate;

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Override
    public List<MessageVo> getPrivateChat(ChatRequest chatRequest, int chatType, User loginUser) {
        Long toId = chatRequest.getToId();
        if (toId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态异常请重试");
        }
        List<MessageVo> chatRecords = getCache(ChatConstant.CACHE_CHAT_PRIVATE, loginUser.getId() + "" + toId);
        if (chatRecords != null) {
            saveCache(ChatConstant.CACHE_CHAT_PRIVATE, loginUser.getId() + "" + toId, chatRecords);
            return chatRecords;
        }
        LambdaQueryWrapper<Chat> chatLambdaQueryWrapper = new LambdaQueryWrapper<>();
        chatLambdaQueryWrapper.
                and(privateChat -> privateChat.eq(Chat::getFromId, loginUser.getId()).eq(Chat::getToId, toId)
                        .or().
                        eq(Chat::getToId, loginUser.getId()).eq(Chat::getFromId, toId)
                ).eq(Chat::getChatType, chatType);
        // 两方共有聊天
        List<Chat> list = this.list(chatLambdaQueryWrapper);
        List<MessageVo> messageVoList = list.stream().map(chat -> {
            MessageVo messageVo = chatResult(loginUser.getId(), toId, chat.getText(), chatType, chat.getCreateTime());
            if (chat.getFromId().equals(loginUser.getId())) {
                messageVo.setIsMy(true);
            }
            return messageVo;
        }).collect(Collectors.toList());
        saveCache(ChatConstant.CACHE_CHAT_PRIVATE, loginUser.getId() + "" + toId, messageVoList);
        return messageVoList;
    }

    @Override
    public List<MessageVo> getHallChat(int chatType, User loginUser) {
        List<MessageVo> chatRecords = getCache(ChatConstant.CACHE_CHAT_HALL, String.valueOf(loginUser.getId()));
        if (chatRecords != null) {
            List<MessageVo> messageVos = checkIsMyMessage(loginUser, chatRecords);
            saveCache(ChatConstant.CACHE_CHAT_HALL, String.valueOf(loginUser.getId()), messageVos);
            return messageVos;
        }
        LambdaQueryWrapper<Chat> chatLambdaQueryWrapper = new LambdaQueryWrapper<>();
        chatLambdaQueryWrapper.eq(Chat::getChatType, chatType);
        List<MessageVo> messageVos = returnMessage(loginUser, null, chatLambdaQueryWrapper);
        saveCache(ChatConstant.CACHE_CHAT_HALL, String.valueOf(loginUser.getId()), messageVos);
        return messageVos;
    }

    private List<MessageVo> checkIsMyMessage(User loginUser, List<MessageVo> chatRecords) {
        return chatRecords.stream().peek(chat -> {
            if (chat.getFormUser().getId() != loginUser.getId() && chat.getIsMy()) {
                chat.setIsMy(false);
            }
            if (chat.getFormUser().getId() == loginUser.getId() && !chat.getIsMy()) {
                chat.setIsMy(true);
            }
        }).collect(Collectors.toList());
    }

    /**
     * 获取缓存
     *
     * @param redisKey
     * @param id
     * @return
     */
    @Override
    public List<MessageVo> getCache(String redisKey, String id) {
        ValueOperations<String, List<MessageVo>> valueOperations = redisTemplate.opsForValue();
        List<MessageVo> chatRecords;
        if (redisKey.equals(ChatConstant.CACHE_CHAT_HALL)) {
            chatRecords = valueOperations.get(redisKey);
        } else {
            chatRecords = valueOperations.get(redisKey + id);
        }
        return chatRecords;
    }

    @Override
    public void deleteKey(String key, String id) {
        if (key.equals(ChatConstant.CACHE_CHAT_HALL)) {
            redisTemplate.delete(key);
        } else {
            redisTemplate.delete(key + id);
        }
    }

    /**
     * 保存缓存
     *
     * @param redisKey
     * @param id
     * @param messageVos
     */
    @Override
    public void saveCache(String redisKey, String id, List<MessageVo> messageVos) {
        try {
            ValueOperations<String, List<MessageVo>> valueOperations = redisTemplate.opsForValue();
            // 解决缓存雪崩
            int i = RandomUtil.randomInt(2, 3);
            if (redisKey.equals(ChatConstant.CACHE_CHAT_HALL)) {
                valueOperations.set(redisKey, messageVos, 2 + i / 10, TimeUnit.MINUTES);
            } else {
                valueOperations.set(redisKey + id, messageVos, 2 + i / 10, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.error("redis set key error");
        }
    }

    @Override
    public MessageVo chatResult(Long userId, Long toId, String text, Integer chatType, Date createTime) {
        MessageVo messageVo = new MessageVo();
        User fromUser = userService.getById(userId);
        User toUser = userService.getById(toId);
        WebSocketVo fromWebSocketVo = new WebSocketVo();
        WebSocketVo toWebSocketVo = new WebSocketVo();
        BeanUtils.copyProperties(fromUser, fromWebSocketVo);
        BeanUtils.copyProperties(toUser, toWebSocketVo);
        messageVo.setFormUser(fromWebSocketVo);
        messageVo.setToUser(toWebSocketVo);
        messageVo.setChatType(chatType);
        messageVo.setText(text);
        messageVo.setCreateTime(DateUtil.format(createTime, "yyyy年MM月dd日 HH:mm:ss"));
        return messageVo;
    }

    @Override
    public List<MessageVo> getTeamChat(ChatRequest chatRequest, int chatType, User loginUser) {
        Long teamId = chatRequest.getTeamId();
        if (teamId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求有误");
        }
        List<MessageVo> chatRecords = getCache(ChatConstant.CACHE_CHAT_TEAM, String.valueOf(teamId));
        if (chatRecords != null) {
            List<MessageVo> messageVos = checkIsMyMessage(loginUser, chatRecords);
            saveCache(ChatConstant.CACHE_CHAT_TEAM, String.valueOf(teamId), messageVos);
            return messageVos;
        }
        Team team = teamService.getById(teamId);
        LambdaQueryWrapper<Chat> chatLambdaQueryWrapper = new LambdaQueryWrapper<>();
        chatLambdaQueryWrapper.eq(Chat::getChatType, chatType).eq(Chat::getTeamId, teamId);
        List<MessageVo> messageVos = returnMessage(loginUser, team.getUserId(), chatLambdaQueryWrapper);
        saveCache(ChatConstant.CACHE_CHAT_TEAM, String.valueOf(teamId), messageVos);
        return messageVos;
    }


    private List<MessageVo> returnMessage(User loginUser, Long userId, LambdaQueryWrapper<Chat> chatLambdaQueryWrapper) {
        List<Chat> chatList = this.list(chatLambdaQueryWrapper);
        return chatList.stream().map(chat -> {
            MessageVo messageVo = chatResult(chat.getFromId(), chat.getText());
            boolean isCaptain = userId != null && userId.equals(chat.getFromId());
            if (userService.getById(chat.getFromId()).getUserRole() == UserConstant.ADMIN_ROLE || isCaptain) {
                messageVo.setIsAdmin(true);
            }
            if (chat.getFromId().equals(loginUser.getId())) {
                messageVo.setIsMy(true);
            }
            messageVo.setCreateTime(DateUtil.format(chat.getCreateTime(), "yyyy年MM月dd日 HH:mm:ss"));
            return messageVo;
        }).collect(Collectors.toList());
    }

    /**
     * Vo映射
     *
     * @param userId
     * @param text
     * @return
     */
    public MessageVo chatResult(Long userId, String text) {
        MessageVo messageVo = new MessageVo();
        User fromUser = userService.getById(userId);
        WebSocketVo fromWebSocketVo = new WebSocketVo();
        BeanUtils.copyProperties(fromUser, fromWebSocketVo);
        messageVo.setFormUser(fromWebSocketVo);
        messageVo.setText(text);
        return messageVo;
    }
}




