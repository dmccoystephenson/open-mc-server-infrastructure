package com.openmc.webapp.mapper;

import com.openmc.webapp.model.LeaderboardEntry;
import com.openmc.webapp.model.PlayerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayerProfileMapper Tests")
class PlayerProfileMapperTest {

    private PlayerProfileMapper playerProfileMapper;

    @BeforeEach
    void setUp() {
        playerProfileMapper = Mappers.getMapper(PlayerProfileMapper.class);
    }

    @Test
    @DisplayName("Should map every leaderboard entry field to the player profile")
    void shouldMapLeaderboardEntryToPlayerProfile() {
        LeaderboardEntry entry = new LeaderboardEntry(
                "0d2d0f0a-1b2c-4d3e-8f90-abcdef123456", "Notch", 42.5, 17);

        PlayerProfile profile = playerProfileMapper.toPlayerProfile(entry);

        assertNotNull(profile);
        assertEquals("0d2d0f0a-1b2c-4d3e-8f90-abcdef123456", profile.getPlayerUuid());
        assertEquals("Notch", profile.getPlayerName());
        assertEquals(42.5, profile.getHoursPlayed());
        assertEquals(17, profile.getTotalLogins());
    }

    @Test
    @DisplayName("Should leave leaderboardRank unset so the caller can assign the real rank")
    void shouldNotPopulateLeaderboardRank() {
        LeaderboardEntry entry = new LeaderboardEntry("uuid", "Steve", 1.0, 1);

        PlayerProfile profile = playerProfileMapper.toPlayerProfile(entry);

        assertEquals(0, profile.getLeaderboardRank());
    }

    @Test
    @DisplayName("Should map an entry with default values without failing")
    void shouldMapEmptyEntry() {
        PlayerProfile profile = playerProfileMapper.toPlayerProfile(new LeaderboardEntry());

        assertNotNull(profile);
        assertNull(profile.getPlayerUuid());
        assertNull(profile.getPlayerName());
        assertEquals(0.0, profile.getHoursPlayed());
        assertEquals(0, profile.getTotalLogins());
        assertEquals(0, profile.getLeaderboardRank());
    }

    @Test
    @DisplayName("Should return null when the entry is null")
    void shouldReturnNullWhenEntryIsNull() {
        assertNull(playerProfileMapper.toPlayerProfile(null));
    }
}
