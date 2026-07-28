package com.mrboard.system.controller;

import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MrsController#validateStatusTransition} 的单元测试。
 *
 * <p>覆盖场景：不同角色（ADMIN/TECHLEAD/PM/DEVELOPER/REVIEWER）对终态/冲突/自身MR的操作权限。</p>
 */
class MrsControllerStatusTransitionTest {

    private final MrsController controller = new MrsController(null, null, null, null, null, null, null, null, null);

    private static final Long MR_AUTHOR_ID = 1L;
    private static final String MR_AUTHOR_NAME = "dev1";
    private static final Long OTHER_AUTHOR_ID = 2L;
    private static final String OTHER_AUTHOR_NAME = "other-dev";

    private Method validateMethod;

    @BeforeEach
    void setUp() throws Exception {
        validateMethod = MrsController.class.getDeclaredMethod(
                "validateStatusTransition", Mrs.class, String.class, User.class);
        validateMethod.setAccessible(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========== helpers ==========

    /** 创建一个 MR，可指定作者名和冲突标识 */
    private Mrs createMr(String authorName, Boolean hasConflict) {
        Mrs mr = new Mrs();
        mr.setId(1L);
        mr.setAuthorName(authorName);
        mr.setHasConflict(hasConflict);
        mr.setBoardStatus("pending_review");
        return mr;
    }

    /** 创建用户 */
    private User createUser(Long id, String username, String platformUsername) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPlatformUsername(platformUsername);
        return user;
    }

    /** 设置安全上下文角色 */
    private void setRoles(String... roles) {
        List<GrantedAuthority> authorities = List.of(roles).stream()
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();
        Authentication auth = new Authentication() {
            @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
            @Override public Object getCredentials() { return null; }
            @Override public Object getDetails() { return null; }
            @Override public Object getPrincipal() { return "1"; }
            @Override public boolean isAuthenticated() { return true; }
            @Override public void setAuthenticated(boolean isAuthenticated) {}
            @Override public String getName() { return "1"; }
        };
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 反射调用 validateStatusTransition */
    private String callValidate(Mrs mr, String newStatus, User user) throws Exception {
        return (String) validateMethod.invoke(controller, mr, newStatus, user);
    }

    // ==========  ADMIN 权限 ==========

    @Test
    @DisplayName("ADMIN 可以将 MR 拖入任意状态（包括 merged/closed）")
    void admin_canDragToAnyStatus() throws Exception {
        setRoles("ROLE_ADMIN");
        User admin = createUser(99L, "admin", "admin_platform");
        Mrs mr = createMr(MR_AUTHOR_NAME, false);

        assertNull(callValidate(mr, "reviewing", admin));
        assertNull(callValidate(mr, "ready", admin));
        assertNull(callValidate(mr, "conflict", admin));
        assertNull(callValidate(mr, "merged", admin));
        assertNull(callValidate(mr, "closed", admin));
    }

    @Test
    @DisplayName("冲突 MR 禁止拖入 merged（包括 ADMIN）")
    void conflictMr_cannotDragToMerged() throws Exception {
        setRoles("ROLE_ADMIN");
        User admin = createUser(99L, "admin", "admin_platform");
        Mrs mr = createMr(MR_AUTHOR_NAME, true);

        // 冲突检查在角色检查之前，所以即使是 ADMIN 也会被拦截
        String error = callValidate(mr, "merged", admin);
        assertNotNull(error);
        assertTrue(error.contains("存在冲突"));
    }

    // ==========  TECHLEAD 权限 ==========

    @Test
    @DisplayName("TECHLEAD 可以将 MR 拖入任意状态")
    void techlead_canDragToAnyStatus() throws Exception {
        setRoles("ROLE_TECHLEAD");
        User techlead = createUser(98L, "techlead", "tl_platform");
        Mrs mr = createMr(MR_AUTHOR_NAME, false);

        assertNull(callValidate(mr, "reviewing", techlead));
        assertNull(callValidate(mr, "ready", techlead));
        assertNull(callValidate(mr, "merged", techlead));
        assertNull(callValidate(mr, "closed", techlead));
    }

    // ==========  PM 权限 ==========

    @Test
    @DisplayName("PM 可将 MR 拖入非终态，但不能设为 merged/closed")
    void pm_cannotSetMergedOrClosed() throws Exception {
        setRoles("ROLE_PM");
        User pm = createUser(97L, "pm_user", "pm_platform");
        Mrs mr = createMr(MR_AUTHOR_NAME, false);

        assertNull(callValidate(mr, "reviewing", pm));
        assertNull(callValidate(mr, "ready", pm));

        String error = callValidate(mr, "merged", pm);
        assertNotNull(error);
        assertTrue(error.contains("ADMIN") && error.contains("TECHLEAD"));

        error = callValidate(mr, "closed", pm);
        assertNotNull(error);
        assertTrue(error.contains("ADMIN") && error.contains("TECHLEAD"));
    }

    // ==========  DEVELOPER 权限 ==========

    @Test
    @DisplayName("DEVELOPER 可将自己的 MR 拖入非终态")
    void developer_canDragOwnMr() throws Exception {
        setRoles("ROLE_DEVELOPER");
        User dev = createUser(MR_AUTHOR_ID, "dev1", "dev1");
        Mrs mr = createMr(MR_AUTHOR_NAME, false);

        assertNull(callValidate(mr, "reviewing", dev));
        assertNull(callValidate(mr, "ready", dev));
    }

    @Test
    @DisplayName("DEVELOPER 不能将自己的 MR 设为 merged")
    void developer_cannotSetOwnMrToMerged() throws Exception {
        setRoles("ROLE_DEVELOPER");
        User dev = createUser(MR_AUTHOR_ID, "dev1", "dev1");
        Mrs mr = createMr(MR_AUTHOR_NAME, false);

        String error = callValidate(mr, "merged", dev);
        assertNotNull(error);
        assertTrue(error.contains("ADMIN") && error.contains("TECHLEAD"));
    }

    @Test
    @DisplayName("DEVELOPER 不能操作他人的 MR")
    void developer_cannotDragOtherMr() throws Exception {
        setRoles("ROLE_DEVELOPER");
        User dev = createUser(OTHER_AUTHOR_ID, "other-dev", "other-dev");
        Mrs mr = createMr(MR_AUTHOR_NAME, false);

        String error = callValidate(mr, "reviewing", dev);
        assertNotNull(error);
        assertTrue(error.contains("只能操作自己提交的MR"));
    }

    @Test
    @DisplayName("DEVELOPER 用 platformUsername 匹配 MR 作者名")
    void developer_matchesByPlatformUsername() throws Exception {
        setRoles("ROLE_DEVELOPER");
        // platformUsername 匹配 MR.authorName
        User dev = createUser(99L, "localname", "dev1");
        Mrs mr = createMr("dev1", false);

        assertNull(callValidate(mr, "reviewing", dev));
    }

    @Test
    @DisplayName("DEVELOPER username 直接匹配 MR 作者名")
    void developer_matchesByUsername() throws Exception {
        setRoles("ROLE_DEVELOPER");
        User dev = createUser(99L, "dev1", null); // no platformUsername
        Mrs mr = createMr("dev1", false);

        assertNull(callValidate(mr, "reviewing", dev));
    }

    // ==========  REVIEWER 权限 ==========

    @Test
    @DisplayName("REVIEWER 不能拖拽 MR（即使有自己的）")
    void reviewer_cannotDrag() throws Exception {
        setRoles("ROLE_REVIEWER");
        User reviewer = createUser(96L, "reviewer1", "reviewer1");
        Mrs mr = createMr("reviewer1", false);

        String error = callValidate(mr, "reviewing", reviewer);
        assertNotNull(error);
        assertTrue(error.contains("无权限"));
    }

    // ==========  冲突 MR 限制 ==========

    @Test
    @DisplayName("冲突 MR 不能被拖入 ready 状态")
    void conflictMr_cannotDragToReady() throws Exception {
        setRoles("ROLE_ADMIN");
        User admin = createUser(99L, "admin", "admin");
        Mrs mr = createMr(MR_AUTHOR_NAME, true);

        String error = callValidate(mr, "ready", admin);
        assertNotNull(error);
        assertTrue(error.contains("存在冲突"));
    }

    @Test
    @DisplayName("冲突 MR 可以被拖入 reviewing（允许中间态）")
    void conflictMr_canDragToReviewing() throws Exception {
        setRoles("ROLE_ADMIN");
        User admin = createUser(99L, "admin", "admin");
        Mrs mr = createMr(MR_AUTHOR_NAME, true);

        // 冲突 MR 允许进入 reviewing（解决冲突前先review）
        assertNull(callValidate(mr, "reviewing", admin));
    }

    @Test
    @DisplayName("无冲突 MR 可以拖入 ready")
    void noConflictMr_canDragToReady() throws Exception {
        setRoles("ROLE_ADMIN");
        User admin = createUser(99L, "admin", "admin");
        Mrs mr = createMr(MR_AUTHOR_NAME, false);

        assertNull(callValidate(mr, "ready", admin));
    }

    // ==========  无权限角色 ==========

    @Test
    @DisplayName("无角色用户不可操作 merged 终态")
    void noAuth_cannotSetMerged() throws Exception {
        setRoles(); // 空权限
        User nobody = createUser(0L, "nobody", null);
        Mrs mr = createMr(MR_AUTHOR_NAME, false);

        // 无角色时 merged/closed 检查会拒绝
        String error = callValidate(mr, "merged", nobody);
        assertNotNull(error);
        assertTrue(error.contains("ADMIN") && error.contains("TECHLEAD"));
    }

    // ==========  边界情况 ==========

    @Test
    @DisplayName("MR 作者为 null 时 DEVELOPER 无法通过作者名匹配")
    void developer_withNullAuthor_shouldFail() throws Exception {
        setRoles("ROLE_DEVELOPER");
        User dev = createUser(MR_AUTHOR_ID, "dev1", "dev1");
        Mrs mr = createMr(null, false); // null authorName

        String error = callValidate(mr, "reviewing", dev);
        assertNotNull(error);
        assertTrue(error.contains("只能操作自己提交的MR"));
    }

    @Test
    @DisplayName("冲突 MR — ADMIN 可拖入 conflict 列（直接放回同一列）")
    void conflictMr_adminCanDragToConflict() throws Exception {
        setRoles("ROLE_ADMIN");
        User admin = createUser(99L, "admin", "admin");
        Mrs mr = createMr(MR_AUTHOR_NAME, true);

        assertNull(callValidate(mr, "conflict", admin));
    }
}
