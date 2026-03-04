package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.ToolDefinition;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoleFilterService Tests")
class RoleFilterServiceTest {

    @InjectMocks
    private RoleFilterService roleFilterService;

    @Mock
    private Member member;

    @Mock
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(roleFilterService, "adminRoleId", "admin-role-id");
        ReflectionTestUtils.setField(roleFilterService, "moderatorRoleId", "moderator-role-id");
        ReflectionTestUtils.setField(roleFilterService, "memberRoleId", "member-role-id");
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "");
        when(member.getUser()).thenReturn(user);
        when(user.getName()).thenReturn("testuser");
    }

    // ── resolveRoleTier ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return UNRECOGNIZED for null member")
    void shouldReturnUnrecognizedForNullMember() {
        assertEquals(RoleFilterService.RoleTier.UNRECOGNIZED, roleFilterService.resolveRoleTier(null));
    }

    @Test
    @DisplayName("Should return ADMIN when member has admin role")
    void shouldReturnAdminWhenMemberHasAdminRole() {
        Role adminRole = mock(Role.class);
        when(adminRole.getId()).thenReturn("admin-role-id");
        when(member.getRoles()).thenReturn(List.of(adminRole));

        assertEquals(RoleFilterService.RoleTier.ADMIN, roleFilterService.resolveRoleTier(member));
    }

    @Test
    @DisplayName("Should return MODERATOR when member has moderator role")
    void shouldReturnModeratorWhenMemberHasModeratorRole() {
        Role modRole = mock(Role.class);
        when(modRole.getId()).thenReturn("moderator-role-id");
        when(member.getRoles()).thenReturn(List.of(modRole));

        assertEquals(RoleFilterService.RoleTier.MODERATOR, roleFilterService.resolveRoleTier(member));
    }

    @Test
    @DisplayName("Should return MEMBER when member has member role")
    void shouldReturnMemberWhenMemberHasMemberRole() {
        Role memRole = mock(Role.class);
        when(memRole.getId()).thenReturn("member-role-id");
        when(member.getRoles()).thenReturn(List.of(memRole));

        assertEquals(RoleFilterService.RoleTier.MEMBER, roleFilterService.resolveRoleTier(member));
    }

    @Test
    @DisplayName("Should return UNRECOGNIZED when member has no matching roles")
    void shouldReturnUnrecognizedWhenNoMatchingRoles() {
        Role otherRole = mock(Role.class);
        when(otherRole.getId()).thenReturn("some-other-role");
        when(member.getRoles()).thenReturn(List.of(otherRole));

        assertEquals(RoleFilterService.RoleTier.UNRECOGNIZED, roleFilterService.resolveRoleTier(member));
    }

    @Test
    @DisplayName("Should return UNRECOGNIZED when member has no roles")
    void shouldReturnUnrecognizedWhenNoRoles() {
        when(member.getRoles()).thenReturn(List.of());

        assertEquals(RoleFilterService.RoleTier.UNRECOGNIZED, roleFilterService.resolveRoleTier(member));
    }

    @Test
    @DisplayName("Should prefer ADMIN over MODERATOR when member has both roles")
    void shouldPreferAdminOverModeratorWhenBothPresent() {
        Role adminRole = mock(Role.class);
        when(adminRole.getId()).thenReturn("admin-role-id");
        Role modRole = mock(Role.class);
        when(modRole.getId()).thenReturn("moderator-role-id");
        when(member.getRoles()).thenReturn(List.of(modRole, adminRole));

        assertEquals(RoleFilterService.RoleTier.ADMIN, roleFilterService.resolveRoleTier(member));
    }

    @Test
    @DisplayName("Should return UNRECOGNIZED when admin role ID is blank")
    void shouldReturnUnrecognizedWhenAdminRoleIdIsBlank() {
        ReflectionTestUtils.setField(roleFilterService, "adminRoleId", "");
        ReflectionTestUtils.setField(roleFilterService, "moderatorRoleId", "");
        ReflectionTestUtils.setField(roleFilterService, "memberRoleId", "");

        Role someRole = mock(Role.class);
        when(someRole.getId()).thenReturn("admin-role-id");
        when(member.getRoles()).thenReturn(List.of(someRole));

        assertEquals(RoleFilterService.RoleTier.UNRECOGNIZED, roleFilterService.resolveRoleTier(member));
    }

    // ── getPermittedTools ────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin should have all tools")
    void adminShouldHaveAllTools() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.ADMIN);
        assertEquals(ToolDefinition.allTools().size(), tools.size());
    }

    @Test
    @DisplayName("Admin should have start_server tool")
    void adminShouldHaveStartServerTool() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.ADMIN);
        assertTrue(tools.stream().anyMatch(t -> "start_server".equals(t.getName())));
    }

    @Test
    @DisplayName("Admin should have stop_server tool")
    void adminShouldHaveStopServerTool() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.ADMIN);
        assertTrue(tools.stream().anyMatch(t -> "stop_server".equals(t.getName())));
    }

    @Test
    @DisplayName("Moderator should not have start_server tool")
    void moderatorShouldNotHaveStartServerTool() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MODERATOR);
        assertFalse(tools.stream().anyMatch(t -> "start_server".equals(t.getName())));
    }

    @Test
    @DisplayName("Moderator should not have stop_server tool")
    void moderatorShouldNotHaveStopServerTool() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MODERATOR);
        assertFalse(tools.stream().anyMatch(t -> "stop_server".equals(t.getName())));
    }

    @Test
    @DisplayName("Moderator should have restart_server tool")
    void moderatorShouldHaveRestartServerTool() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MODERATOR);
        assertTrue(tools.stream().anyMatch(t -> "restart_server".equals(t.getName())));
    }

    @Test
    @DisplayName("Moderator should have trigger_backup tool")
    void moderatorShouldHaveTriggerBackupTool() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MODERATOR);
        assertTrue(tools.stream().anyMatch(t -> "trigger_backup".equals(t.getName())));
    }

    @Test
    @DisplayName("Moderator should have get_server_status tool")
    void moderatorShouldHaveGetServerStatusTool() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MODERATOR);
        assertTrue(tools.stream().anyMatch(t -> "get_server_status".equals(t.getName())));
    }

    @Test
    @DisplayName("Member should only have read-only tools")
    void memberShouldOnlyHaveReadOnlyTools() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MEMBER);
        assertFalse(tools.stream().anyMatch(t -> "start_server".equals(t.getName())));
        assertFalse(tools.stream().anyMatch(t -> "stop_server".equals(t.getName())));
        assertFalse(tools.stream().anyMatch(t -> "restart_server".equals(t.getName())));
        assertFalse(tools.stream().anyMatch(t -> "trigger_backup".equals(t.getName())));
    }

    @Test
    @DisplayName("Member should have get_server_status tool")
    void memberShouldHaveGetServerStatusTool() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MEMBER);
        assertTrue(tools.stream().anyMatch(t -> "get_server_status".equals(t.getName())));
    }

    @Test
    @DisplayName("Unrecognized should have no tools")
    void unrecognizedShouldHaveNoTools() {
        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.UNRECOGNIZED);
        assertTrue(tools.isEmpty());
    }

    // ── getRoleDisplayName ───────────────────────────────────────────────────

    @Test
    @DisplayName("Admin display name should be 'Admin'")
    void adminDisplayNameShouldBeAdmin() {
        assertEquals("Admin", roleFilterService.getRoleDisplayName(RoleFilterService.RoleTier.ADMIN));
    }

    @Test
    @DisplayName("Moderator display name should be 'Moderator'")
    void moderatorDisplayNameShouldBeModerator() {
        assertEquals("Moderator", roleFilterService.getRoleDisplayName(RoleFilterService.RoleTier.MODERATOR));
    }

    @Test
    @DisplayName("Member display name should be 'Member'")
    void memberDisplayNameShouldBeMember() {
        assertEquals("Member", roleFilterService.getRoleDisplayName(RoleFilterService.RoleTier.MEMBER));
    }

    @Test
    @DisplayName("Unrecognized display name should be 'Unrecognized'")
    void unrecognizedDisplayNameShouldBeUnrecognized() {
        assertEquals("Unrecognized", roleFilterService.getRoleDisplayName(RoleFilterService.RoleTier.UNRECOGNIZED));
    }

    // ── public tools ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Unrecognized user should get public tools when configured")
    void unrecognizedShouldGetPublicToolsWhenConfigured() {
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "get_server_status");

        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.UNRECOGNIZED);

        assertEquals(1, tools.size());
        assertEquals("get_server_status", tools.get(0).getName());
    }

    @Test
    @DisplayName("Unrecognized user should get multiple public tools when configured")
    void unrecognizedShouldGetMultiplePublicToolsWhenConfigured() {
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "get_server_status,get_server_metrics");

        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.UNRECOGNIZED);

        assertEquals(2, tools.size());
        assertTrue(tools.stream().anyMatch(t -> "get_server_status".equals(t.getName())));
        assertTrue(tools.stream().anyMatch(t -> "get_server_metrics".equals(t.getName())));
    }

    @Test
    @DisplayName("Public tools should not be duplicated for member who already has them")
    void publicToolsShouldNotBeDuplicatedForMember() {
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "get_server_status");

        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MEMBER);

        long count = tools.stream().filter(t -> "get_server_status".equals(t.getName())).count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Public tools should add new tools to member tier")
    void publicToolsShouldAddNewToolsToMemberTier() {
        // restart_server is not in MEMBER tier by default
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "restart_server");

        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.MEMBER);

        assertTrue(tools.stream().anyMatch(t -> "restart_server".equals(t.getName())));
    }

    @Test
    @DisplayName("Empty public tools config should return empty set of public names")
    void emptyPublicToolsConfigShouldReturnEmptySet() {
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "");
        assertTrue(roleFilterService.getPublicToolNames().isEmpty());
    }

    @Test
    @DisplayName("Blank public tools config should return empty set of public names")
    void blankPublicToolsConfigShouldReturnEmptySet() {
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "   ");
        assertTrue(roleFilterService.getPublicToolNames().isEmpty());
    }

    @Test
    @DisplayName("Public tool names should be parsed from comma-separated config")
    void publicToolNamesShouldBeParsedFromCommaSeparatedConfig() {
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "get_server_status, get_server_metrics");

        var names = roleFilterService.getPublicToolNames();

        assertTrue(names.contains("get_server_status"));
        assertTrue(names.contains("get_server_metrics"));
    }

    @Test
    @DisplayName("Unknown tool name in public tools should be silently ignored in tool list")
    void unknownToolNameInPublicToolsShouldBeIgnored() {
        ReflectionTestUtils.setField(roleFilterService, "publicToolsConfig", "nonexistent_tool");

        List<ToolDefinition> tools = roleFilterService.getPermittedTools(RoleFilterService.RoleTier.UNRECOGNIZED);

        assertTrue(tools.isEmpty());
    }
}
