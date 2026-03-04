package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that resolves a Discord member's role tier and returns the set of
 * tools they are permitted to use. Role IDs are configurable via environment
 * variables so that server operators can adjust permissions without recompiling.
 */
@Slf4j
@Service
public class RoleFilterService {

    /** Role tiers in descending privilege order. */
    public enum RoleTier {
        ADMIN, MODERATOR, MEMBER, UNRECOGNIZED
    }

    @Value("${agent.role.admin.id:}")
    private String adminRoleId;

    @Value("${agent.role.moderator.id:}")
    private String moderatorRoleId;

    @Value("${agent.role.member.id:}")
    private String memberRoleId;

    /**
     * Resolve the highest-privilege role tier held by the given Discord member.
     * Checks Admin first, then Moderator, then Member. Returns UNRECOGNIZED if
     * the member holds none of the configured roles, or if the member is null.
     *
     * @param member the JDA Member object from the message event
     * @return the resolved RoleTier
     */
    public RoleTier resolveRoleTier(Member member) {
        if (member == null) {
            log.debug("Null member passed to resolveRoleTier; returning UNRECOGNIZED");
            return RoleTier.UNRECOGNIZED;
        }

        List<String> memberRoleIds = member.getRoles().stream()
                .map(net.dv8tion.jda.api.entities.Role::getId)
                .collect(Collectors.toList());

        log.debug("Resolving role tier for member {} with {} role(s)", member.getUser().getName(), memberRoleIds.size());

        if (!adminRoleId.isBlank() && memberRoleIds.contains(adminRoleId)) {
            log.debug("Member {} resolved to ADMIN", member.getUser().getName());
            return RoleTier.ADMIN;
        }
        if (!moderatorRoleId.isBlank() && memberRoleIds.contains(moderatorRoleId)) {
            log.debug("Member {} resolved to MODERATOR", member.getUser().getName());
            return RoleTier.MODERATOR;
        }
        if (!memberRoleId.isBlank() && memberRoleIds.contains(memberRoleId)) {
            log.debug("Member {} resolved to MEMBER", member.getUser().getName());
            return RoleTier.MEMBER;
        }

        log.debug("Member {} resolved to UNRECOGNIZED (no matching role IDs configured)", member.getUser().getName());
        return RoleTier.UNRECOGNIZED;
    }

    /**
     * Return the set of tools permitted for the given role tier.
     *
     * <ul>
     *   <li>Admin — all tools</li>
     *   <li>Moderator — restart, backup, and all read-only/status tools</li>
     *   <li>Member — read-only/status tools only</li>
     *   <li>Unrecognized — no tools</li>
     * </ul>
     *
     * @param tier the resolved role tier
     * @return an immutable list of permitted ToolDefinitions
     */
    public List<ToolDefinition> getPermittedTools(RoleTier tier) {
        return switch (tier) {
            case ADMIN -> ToolDefinition.allTools();
            case MODERATOR -> List.of(
                    ToolDefinition.restartServer(),
                    ToolDefinition.triggerBackup(),
                    ToolDefinition.getServerStatus(),
                    ToolDefinition.getServerMetrics(),
                    ToolDefinition.getActivityTrackerStats(),
                    ToolDefinition.getActivityTrackerLeaderboard(),
                    ToolDefinition.getServerDiagnostics()
            );
            case MEMBER -> List.of(
                    ToolDefinition.getServerStatus(),
                    ToolDefinition.getServerMetrics(),
                    ToolDefinition.getActivityTrackerStats(),
                    ToolDefinition.getActivityTrackerLeaderboard(),
                    ToolDefinition.getServerDiagnostics()
            );
            case UNRECOGNIZED -> List.of();
        };
    }

    /**
     * Return a human-readable display name for a role tier.
     *
     * @param tier the role tier
     * @return display name string
     */
    public String getRoleDisplayName(RoleTier tier) {
        return switch (tier) {
            case ADMIN -> "Admin";
            case MODERATOR -> "Moderator";
            case MEMBER -> "Member";
            case UNRECOGNIZED -> "Unrecognized";
        };
    }
}
