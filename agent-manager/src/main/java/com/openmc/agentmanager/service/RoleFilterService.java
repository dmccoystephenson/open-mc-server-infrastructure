package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that resolves a Discord member's role tier and returns the set of
 * tools they are permitted to use. Role IDs are configurable via environment
 * variables so that server operators can adjust permissions without recompiling.
 *
 * <p>In addition to the per-tier tool sets, individual tools can be designated
 * as <em>public</em> via {@code AGENT_PUBLIC_TOOLS}: these tools are available
 * to every user regardless of their role tier, including users whose role is
 * UNRECOGNIZED. Public tool names are specified as a comma-separated list of
 * tool names, e.g. {@code get_server_status,get_server_metrics}.</p>
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
     * Comma-separated list of tool names that are available to every user
     * regardless of their Discord role. Leave blank to keep all tools
     * role-gated (default behaviour).
     */
    @Value("${agent.public.tools:}")
    private String publicToolsConfig;

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

        log.debug("Member {} resolved to UNRECOGNIZED (no configured role IDs matched member roles)", member.getUser().getName());
        return RoleTier.UNRECOGNIZED;
    }

    /**
     * Return the set of tools permitted for the given role tier, merged with
     * any public tools configured via {@code AGENT_PUBLIC_TOOLS}.
     *
     * <ul>
     *   <li>Admin — all tools</li>
     *   <li>Moderator — restart, backup, and all read-only/status tools, plus public tools</li>
     *   <li>Member — read-only/status tools, plus public tools</li>
     *   <li>Unrecognized — public tools only (empty list if none configured)</li>
     * </ul>
     *
     * @param tier the resolved role tier
     * @return a list of permitted ToolDefinitions (ordered, deduplicated)
     */
    public List<ToolDefinition> getPermittedTools(RoleTier tier) {
        List<ToolDefinition> tierTools = switch (tier) {
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

        List<ToolDefinition> publicTools = resolvePublicTools();
        if (publicTools.isEmpty()) {
            return tierTools;
        }

        // Merge: tier tools first (preserves ordering), then public tools not already present.
        // Use a LinkedHashMap keyed by tool name to deduplicate while preserving insertion order.
        Map<String, ToolDefinition> merged = new LinkedHashMap<>();
        for (ToolDefinition t : tierTools) {
            merged.put(t.getName(), t);
        }
        for (ToolDefinition t : publicTools) {
            merged.putIfAbsent(t.getName(), t);
        }
        return List.copyOf(merged.values());
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

    /**
     * Return the set of tools names that are configured as public (no role required).
     *
     * @return an unmodifiable set of tool names
     */
    public Set<String> getPublicToolNames() {
        if (publicToolsConfig == null || publicToolsConfig.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(publicToolsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<ToolDefinition> resolvePublicTools() {
        Set<String> publicNames = getPublicToolNames();
        if (publicNames.isEmpty()) {
            return List.of();
        }
        List<ToolDefinition> allTools = ToolDefinition.allTools();
        List<ToolDefinition> result = new ArrayList<>();
        for (ToolDefinition t : allTools) {
            if (publicNames.contains(t.getName())) {
                result.add(t);
            }
        }
        Set<String> recognized = allTools.stream()
                .map(ToolDefinition::getName)
                .collect(Collectors.toSet());
        for (String name : publicNames) {
            if (!recognized.contains(name)) {
                log.warn("Configured public tool '{}' is not a recognized tool name and will be ignored", name);
            }
        }
        return result;
    }
}
