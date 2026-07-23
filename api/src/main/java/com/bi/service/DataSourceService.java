package com.bi.service;

import com.bi.exception.BusinessException;
import com.bi.model.entity.BiTable;
import com.bi.model.entity.DataSource;
import com.bi.repository.DataSourceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final TableManagementService tableManagementService;

    public DataSourceService(DataSourceRepository repository,
                             TableManagementService tableManagementService) {
        this.repository = repository;
        this.tableManagementService = tableManagementService;
    }

    public Page<DataSource> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public List<DataSource> listAll() {
        return repository.findAll();
    }

    public DataSource getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("数据源不存在: id=" + id));
    }

    @Transactional
    public void delete(Long id) {
        DataSource ds = getById(id);
        for (BiTable bt : ds.getTables()) {
            tableManagementService.dropTable(bt.getPhysicalName());
        }
        repository.delete(ds);
    }
}
