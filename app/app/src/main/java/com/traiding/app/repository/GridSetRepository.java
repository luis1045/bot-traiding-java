package com.traiding.app.repository;

import com.traiding.app.domain.GridSet;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface GridSetRepository extends ReactiveCrudRepository<GridSet, Long> {

    Flux<GridSet> findByStatus(String status);

    Flux<GridSet> findAllByOrderByCreatedAtDesc();
}
