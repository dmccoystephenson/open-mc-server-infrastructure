package com.openmc.alertmanager.repository;

import com.openmc.alertmanager.model.AlertLevel;
import com.openmc.alertmanager.model.AlertRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@DisplayName("AlertRepository Tests")
class AlertRepositoryTest {

    @Autowired
    private AlertRepository alertRepository;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
    }

    private AlertRecord createRecord(String title) {
        return AlertRecord.builder()
                .title(title)
                .message("Test message")
                .level(AlertLevel.INFO)
                .source("test")
                .receivedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should store alert and return it via findAll")
    void shouldStoreAndRetrieve() {
        alertRepository.save(createRecord("First"));

        List<AlertRecord> records = alertRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 10));
        assertEquals(1, records.size());
        assertEquals("First", records.get(0).getTitle());
    }

    @Test
    @DisplayName("Should persist alerts and return newest first")
    void shouldReturnNewestFirst() {
        AlertRecord alpha = createRecord("Alpha");
        alpha.setReceivedAt(Instant.now().minusSeconds(60));
        alertRepository.save(alpha);

        AlertRecord beta = createRecord("Beta");
        beta.setReceivedAt(Instant.now());
        alertRepository.save(beta);

        List<AlertRecord> records = alertRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 10));

        assertEquals(2, records.size());
        assertEquals("Beta", records.get(0).getTitle());
        assertEquals("Alpha", records.get(1).getTitle());
    }

    @Test
    @DisplayName("Should return empty list when no alerts stored")
    void shouldReturnEmptyListWhenNoAlerts() {
        assertTrue(alertRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 10)).isEmpty());
    }

    @Test
    @DisplayName("Should respect page size limit")
    void shouldRespectLimit() {
        for (int i = 0; i < 20; i++) {
            AlertRecord record = createRecord("Alert " + i);
            record.setReceivedAt(Instant.now().plusSeconds(i));
            alertRepository.save(record);
        }
        assertEquals(5, alertRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 5)).size());
    }

    @Test
    @DisplayName("Should return all records when count is within limit")
    void shouldReturnAllRecordsWithinLimit() {
        for (int i = 0; i < 5; i++) {
            alertRepository.save(createRecord("Alert " + i));
        }
        assertEquals(5, alertRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 100)).size());
    }
}
