package com.engine.chronos.domain.port.out;

import com.engine.chronos.domain.event.DomainEvent;

public interface DomainEventPublisherPort {
    void publish(DomainEvent event);
}
