package com.mrboard.system.security;

import com.mrboard.system.entity.Permission;
import com.mrboard.system.entity.Role;
import com.mrboard.system.entity.User;
import com.mrboard.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String userIdStr) throws UsernameNotFoundException {
        Long userId = Long.valueOf(userIdStr);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<Role> roles = userMapper.selectRolesByUserId(userId);
        List<Permission> permissions = userMapper.selectPermissionsByUserId(userId);

        List<SimpleGrantedAuthority> authorities = Stream.concat(
                roles.stream().map(r -> {
                    String code = r.getCode().toUpperCase();
                    if ("DEV".equals(code)) {
                        code = "DEVELOPER";
                    }
                    return new SimpleGrantedAuthority("ROLE_" + code);
                }),
                permissions.stream().map(p -> new SimpleGrantedAuthority(p.getCode()))
        ).collect(Collectors.toList());

        return new org.springframework.security.core.userdetails.User(
                String.valueOf(user.getId()),
                user.getPassword(),
                user.getDeleted() == null || user.getDeleted() == 0,
                true, true, true,
                authorities
        );
    }

    public UserDetails loadUserByUsernameAndPassword(String username) throws UsernameNotFoundException {
        User user = userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        ).stream().findFirst().orElse(null);

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return loadUserByUsername(String.valueOf(user.getId()));
    }
}
