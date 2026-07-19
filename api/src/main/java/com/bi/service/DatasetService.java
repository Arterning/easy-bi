package com.bi.service;

import com.bi.exception.BusinessException;
import com.bi.model.dto.DatasetCreateRequest;
import com.bi.model.entity.Dataset;
import com.bi.repository.DatasetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetService {

    private final DatasetRepository repository;
    private final QueryService queryService;

    public DatasetService(DatasetRepository repository, QueryService queryService) {
        this.repository = repository;
        this.queryService = queryService;
    }

    @Transactional
    public Dataset create(DatasetCreateRequest req) {
        queryService.validate(req.getSql());
        queryService.validateSyntax(req.getSql());

        Dataset dataset = new Dataset();
        dataset.setName(req.getName());
        dataset.setSql(req.getSql());
        dataset.setDescription(req.getDescription());
        return repository.save(dataset);
    }

    @Transactional
    public Dataset update(Long id, DatasetCreateRequest req) {
        Dataset dataset = repository.findById(id)
                .orElseThrow(() -> new BusinessException("数据集不存在: id=" + id));

        queryService.validate(req.getSql());
        queryService.validateSyntax(req.getSql());

        dataset.setName(req.getName());
        dataset.setSql(req.getSql());
        dataset.setDescription(req.getDescription());
        return repository.save(dataset);
    }

    public Page<Dataset> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Dataset getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("数据集不存在: id=" + id));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException("数据集不存在: id=" + id);
        }
        repository.deleteById(id);
    }
}
