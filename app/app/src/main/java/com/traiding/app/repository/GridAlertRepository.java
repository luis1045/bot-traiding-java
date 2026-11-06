package com.traiding.app.repository;

import com.traiding.app.domain.GridAlert;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GridAlertRepository extends ReactiveCrudRepository<GridAlert, Long> {

    Flux<GridAlert> findByStatusOrderByIdAsc(String status);

    Flux<GridAlert> findByGridSetIdOrderByIdAsc(Long gridSetId);

    Flux<GridAlert> findByGridSetIdAndStatusOrderByIdAsc(Long gridSetId, String status);

    Mono<Void> deleteByGridSetId(Long gridSetId);
}
