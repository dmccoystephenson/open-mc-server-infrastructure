package com.openmc.alertmanager.repository.jpa;

import com.openmc.alertmanager.entity.AlertRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRecordJpaRepository extends JpaRepository<AlertRecordEntity, Long> {

    List<AlertRecordEntity> findTopByOrderByReceivedAtDesc(org.springframework.data.domain.Pageable pageable);
}
