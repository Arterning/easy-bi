package com.bi.repository;

import com.bi.model.entity.LlmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LlmConfigRepository extends JpaRepository<LlmConfig, Long> {
}
