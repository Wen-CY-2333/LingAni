package com.lingani.service;

import com.lingani.common.BizException;
import com.lingani.dto.UpdatePasswordRequest;
import com.lingani.dto.UpdateProfileRequest;
import com.lingani.entity.SysUser;
import com.lingani.mapper.SysUserMapper;
import com.lingani.security.SecurityUtils;
import com.lingani.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserVO getCurrentUser() {
        SysUser user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BizException.unauthorized("用户不存在或已注销");
        }
        return AuthService.toUserVO(user);
    }

    @Transactional
    public UserVO updateProfile(UpdateProfileRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在或已注销");
        }

        user.setNickname(request.getNickname().trim());
        String avatar = StringUtils.hasText(request.getAvatar()) ? request.getAvatar().trim() : null;
        // 使用 UpdateWrapper 显式 set，允许用户清空头像（updateById 会忽略 null 字段）
        userMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getNickname, user.getNickname())
                .set(SysUser::getAvatar, avatar));
        user.setAvatar(avatar);
        return AuthService.toUserVO(user);
    }

    @Transactional
    public void updatePassword(UpdatePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BizException("两次输入的新密码不一致");
        }

        Long userId = SecurityUtils.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在或已注销");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
    }
}
