package com.traiding.app.repository;

import com.traiding.app.domain.GridLevel;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GridLevelRepository extends ReactiveCrudRepository<GridLevel, Long> {

    Flux<GridLevel> findByGridSetIdOrderByLevelIndexAsc(Long gridSetId);

    Mono<Void> deleteByGridSetId(Long gridSetId);
}
