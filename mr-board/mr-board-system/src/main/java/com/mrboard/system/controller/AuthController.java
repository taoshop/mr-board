package com.mrboard.system.controller;

import com.mrboard.common.result.Result;
import com.mrboard.common.utils.JwtUtil;
import com.mrboard.system.dto.LoginRequest;
import com.mrboard.system.dto.LoginResponse;
import com.mrboard.system.entity.Permission;
import com.mrboard.system.entity.Role;
import com.mrboard.system.entity.User;
import com.mrboard.system.mapper.UserMapper;
import com.mrboard.system.security.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String lockKey = "login:fail:" + request.getUsername();
        String lockValue = redisTemplate.opsForValue().get(lockKey);
        if (lockValue != null && Integer.parseInt(lockValue) >= 5) {
            return Result.error(1003, "账号已锁定，请15分钟后重试");
        }

        try {
            User user = userMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getUsername, request.getUsername())
            ).stream().findFirst().orElse(null);

            if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                incrementFailCount(lockKey);
                return Result.error(1002, "用户名或密码错误");
            }

            redisTemplate.delete(lockKey);

            List<Role> roles = userMapper.selectRolesByUserId(user.getId());
            List<Permission> permissions = userMapper.selectPermissionsByUserId(user.getId());

            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", roles.stream().map(Role::getCode).collect(Collectors.toList()));
            claims.put("permissions", permissions.stream().map(Permission::getCode).collect(Collectors.toList()));

            String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), claims);
            String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

            LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .avatar(user.getAvatar())
                    .roles(roles.stream().map(Role::getCode).collect(Collectors.toList()))
                    .build();

            return Result.success(LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(7200L)
                    .user(userInfo)
                    .build());
        } catch (Exception e) {
            incrementFailCount(lockKey);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestHeader("Authorization") String bearerToken) {
        String refreshToken = bearerToken.replace("Bearer ", "");
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            return Result.error(1005, "Token刷新失败");
        }
        Claims claims = jwtUtil.parseRefreshToken(refreshToken);
        Long userId = Long.valueOf(claims.getSubject());

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(1001, "用户不存在");
        }

        List<Role> roles = userMapper.selectRolesByUserId(userId);
        List<Permission> permissions = userMapper.selectPermissionsByUserId(userId);

        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put("roles", roles.stream().map(Role::getCode).collect(Collectors.toList()));
        newClaims.put("permissions", permissions.stream().map(Permission::getCode).collect(Collectors.toList()));

        String newAccessToken = jwtUtil.generateAccessToken(userId, user.getUsername(), newClaims);

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatar(user.getAvatar())
                .roles(roles.stream().map(Role::getCode).collect(Collectors.toList()))
                .build();

        return Result.success(LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresIn(7200L)
                .user(userInfo)
                .build());
    }

    @GetMapping("/me")
    public Result<LoginResponse.UserInfo> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userIdStr = authentication.getName();
        Long userId = Long.valueOf(userIdStr);

        User user = userMapper.selectById(userId);
        List<Role> roles = userMapper.selectRolesByUserId(userId);

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatar(user.getAvatar())
                .roles(roles.stream().map(Role::getCode).collect(Collectors.toList()))
                .build();

        return Result.success(userInfo);
    }

    private void incrementFailCount(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 15, TimeUnit.MINUTES);
        }
    }
}
