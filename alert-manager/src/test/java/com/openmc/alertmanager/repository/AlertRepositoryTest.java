package com.openmc.alertmanager.repository;

import com.openmc.alertmanager.entity.AlertRecordEntity;
import com.openmc.alertmanager.model.Alert;
import com.openmc.alertmanager.model.AlertLevel;
import com.openmc.alertmanager.model.AlertRecord;
import com.openmc.alertmanager.repository.jpa.AlertRecordJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AlertRepository Tests")
class AlertRepositoryTest {

    private AlertRecordJpaRepository jpaRepository;
    private AlertRepository alertRepository;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(AlertRecordJpaRepository.class);
        when(jpaRepository.findTopByOrderByReceivedAtDesc(any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(jpaRepository.save(any(AlertRecordEntity.class))).thenAnswer(i -> i.getArgument(0));
        alertRepository = new AlertRepository(jpaRepository);
    }

    private Alert alert(String title) {
        return Alert.builder()
                .title(title)
                .message("Test message")
                .level(AlertLevel.INFO)
                .source("test")
                .build();
    }

    @Test
    @DisplayName("Should store alert and return it via getRecent")
    void shouldStoreAndRetrieve() {
        alertRepository.store(alert("First"));

        List<AlertRecord> records = alertRepository.getRecent(10);
        assertEquals(1, records.size());
        assertEquals("First", records.get(0).getTitle());
    }

    @Test
    @DisplayName("Should persist each alert to the database")
    void shouldPersistToDatabase() {
        alertRepository.store(alert("Alpha"));
        alertRepository.store(alert("Beta"));

        verify(jpaRepository, times(2)).save(any(AlertRecordEntity.class));
    }

    @Test
    @DisplayName("Should return empty list when no alerts stored")
    void shouldReturnEmptyListWhenNoAlerts() {
        assertTrue(alertRepository.getRecent(10).isEmpty());
    }

    @Test
    @DisplayName("Should cap stored alerts at MAX_STORED_ALERTS")
    void shouldCapAtMaxStoredAlerts() {
        for (int i = 0; i < AlertRepository.MAX_STORED_ALERTS + 10; i++) {
            alertRepository.store(alert("Alert " + i));
        }
        assertEquals(AlertRepository.MAX_STORED_ALERTS, alertRepository.getRecent(200).size());
    }

    @Test
    @DisplayName("getRecent limit should be honoured")
    void shouldRespectLimit() {
        for (int i = 0; i < 20; i++) {
            alertRepository.store(alert("Alert " + i));
        }
        assertEquals(5, alertRepository.getRecent(5).size());
    }

    @Test
    @DisplayName("New instance starts empty when no records in database")
    void newInstanceStartsEmptyWithNoRecords() {
        assertTrue(alertRepository.getRecent(10).isEmpty());
    }
}
