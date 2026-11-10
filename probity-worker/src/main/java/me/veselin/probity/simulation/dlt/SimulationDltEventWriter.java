package me.veselin.probity.simulation.dlt;

import lombok.RequiredArgsConstructor;
import me.veselin.probity.simulation.persistence.SimulationDltOutboxJpaRepository;
import me.veselin.probity.simulation.persistence.SimulationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SimulationDltEventWriter {

  private final SimulationRepository simulationRepository;
  private final SimulationDltOutboxJpaRepository dltRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public WriteResult recordAttemptFailure(FailureContext context) {
    if (context.dispatchGeneration() == null
        || !simulationRepository.markFailedForAttempt(
            context.simulationId(), context.attemptId(), context.dispatchGeneration())) {
      if (context.dispatchGeneration() == null) {
        insert(context);
        return WriteResult.RECORDED_MALFORMED;
      }
      return WriteResult.NOT_OWNER;
    }
    insert(context);
    return WriteResult.RECORDED;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public WriteResult recordUnclaimedFailure(FailureContext context) {
    if (context.dispatchGeneration() == null) {
      insert(context);
      return WriteResult.RECORDED_MALFORMED;
    }
    if (!simulationRepository.markFailedIfPending(
        context.simulationId(), context.dispatchGeneration())) {
      return simulationRepository
              .findLiveState(context.simulationId())
              .filter(state -> state.dispatchGeneration() > context.dispatchGeneration())
              .map(
                  state -> {
                    insert(context);
                    return WriteResult.RECORDED_STALE;
                  })
              .orElse(WriteResult.NOT_OWNER);
    }
    insert(context);
    return WriteResult.RECORDED;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public WriteResult recordUnattributableFailure(FailureContext context) {
    insert(context);
    return WriteResult.RECORDED;
  }

  private void insert(FailureContext context) {
    dltRepository.insertIfAbsent(
        context.dltEventId(),
        context.simulationId(),
        context.attemptId(),
        context.sourceTopic(),
        context.sourcePartition(),
        context.sourceOffset(),
        context.sourceKey(),
        context.payload(),
        context.exceptionType(),
        context.errorDescription(),
        context.failedAt(),
        context.dispatchGeneration(),
        context.dispatchGenerationRaw(),
        context.dispatchGenerationSource().name());
  }
}
