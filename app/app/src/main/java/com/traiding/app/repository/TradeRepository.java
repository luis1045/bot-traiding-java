package com.traiding.app.repository;

import com.traiding.app.domain.Trade;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TradeRepository extends ReactiveCrudRepository<Trade, Long> {

    Flux<Trade> findByGridSetIdOrderByCreatedAtDesc(Long gridSetId);

    Flux<Trade> findByGridLevelIdOrderByCreatedAtDesc(Long gridLevelId);

    Mono<Void> deleteByGridSetId(Long gridSetId);
}
