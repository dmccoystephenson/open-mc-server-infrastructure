package com.openmc.webapp.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerProfileTest {
    
    @Test
    void testDefaultConstructor() {
        PlayerProfile profile = new PlayerProfile();
        assertNotNull(profile);
    }
    
    @Test
    void testParameterizedConstructor() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String name = "TestPlayer";
        double hours = 123.5;
        int logins = 50;
        int rank = 1;
        
        PlayerProfile profile = new PlayerProfile(uuid, name, hours, logins, rank);
        
        assertEquals(uuid, profile.getPlayerUuid());
        assertEquals(name, profile.getPlayerName());
        assertEquals(hours, profile.getHoursPlayed());
        assertEquals(logins, profile.getTotalLogins());
        assertEquals(rank, profile.getLeaderboardRank());
    }
    
    @Test
    void testSettersAndGetters() {
        PlayerProfile profile = new PlayerProfile();
        
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        profile.setPlayerUuid(uuid);
        assertEquals(uuid, profile.getPlayerUuid());
        
        String name = "TestPlayer";
        profile.setPlayerName(name);
        assertEquals(name, profile.getPlayerName());
        
        double hours = 99.9;
        profile.setHoursPlayed(hours);
        assertEquals(hours, profile.getHoursPlayed());
        
        int logins = 25;
        profile.setTotalLogins(logins);
        assertEquals(logins, profile.getTotalLogins());
        
        int rank = 5;
        profile.setLeaderboardRank(rank);
        assertEquals(rank, profile.getLeaderboardRank());
    }
}
