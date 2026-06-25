package com.example.price_tracker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.price_tracker.common.ResultCode;
import com.example.price_tracker.context.UserContext;
import com.example.price_tracker.entity.User;
import com.example.price_tracker.exception.BusinessException;
import com.example.price_tracker.mapper.UserMapper;
import com.example.price_tracker.service.UserService;
import com.example.price_tracker.vo.UserVo;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public UserVo getCurrentUser() {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "current user is not authenticated");
        }
        return toUserVo(getRequiredById(currentUserId));
    }

    @Override
    public User getByUsername(String username) {
        return baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("limit 1"));
    }

    @Override
    public User getRequiredById(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "user not found");
        }
        return user;
    }

    @Override
    public UserVo toUserVo(User user) {
        return UserVo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
