package com.hmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.exception.ForbiddenException;
import com.hmall.common.utils.UserContext;
import com.hmall.config.JwtProperties;
import com.hmall.domain.dto.LoginFormDTO;
import com.hmall.domain.po.User;
import com.hmall.domain.vo.UserLoginVO;
import com.hmall.enums.UserStatus;
import com.hmall.mapper.UserMapper;
import com.hmall.service.IUserService;
import com.hmall.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalTime;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final PasswordEncoder passwordEncoder;

    private final JwtTool jwtTool;

    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;

    @Override
    public UserLoginVO login(LoginFormDTO loginDTO) {


        // 1. 构造查询条件：只根据用户名查询（不要在 SQL 里比对密码）
        User user = lambdaQuery()
                .eq(User::getUsername, loginDTO.getUsername())
                .one();

        // 2. 校验用户是否存在
        if (user == null) {
            throw new BadRequestException("用户名或密码错误");
        }

        // 3. 优化：先校验状态 (轻量级判断，保护 CPU)
        if (user.getStatus() == UserStatus.FROZEN) { //
            throw new BadRequestException("用户状态被冻结，禁止登录");
        }

        // 4. 再校验密码 (重量级计算)
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BadRequestException("用户名或密码错误");
        }

        //5.根据id生成令牌
        String token = jwtTool.createToken(user.getId(), jwtProperties.getTokenTTL());

        // 6. 封装返回结果 UserLoginVO
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setBalance(user.getBalance());
        vo.setToken(token); // 设置生成的 token

        return vo;
    }

    @Override
    public void deductMoney(String pw, Integer totalFee) {
        log.info("开始扣款");
        // 1.校验密码
        User user = getById(UserContext.getUser());
        if(user == null || !passwordEncoder.matches(pw, user.getPassword())){
            // 密码错误
            throw new BizIllegalException("用户密码错误");
        }

        // 2.尝试扣款
        try {
            baseMapper.updateMoney(UserContext.getUser(), totalFee);
        } catch (Exception e) {
            throw new RuntimeException("扣款失败，可能是余额不足！", e);
        }
        log.info("扣款成功");
    }
}
