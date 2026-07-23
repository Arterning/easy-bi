package com.bi.repository;

import com.bi.model.entity.BiTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BiTableRepository extends JpaRepository<BiTable, Long> {
    List<BiTable> findByDataSourceId(Long dataSourceId);
}
