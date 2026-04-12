package com.openmc.webapp.mapper;

import com.openmc.webapp.model.LeaderboardEntry;
import com.openmc.webapp.model.PlayerProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlayerProfileMapper {

    @Mapping(target = "leaderboardRank", ignore = true)
    PlayerProfile toPlayerProfile(LeaderboardEntry entry);
}
