package StockMainAction.model.strategy;

import StockMainAction.model.core.ExecutionResult;
import StockMainAction.model.core.OrderSubmissionResult;

public record StrategyExecutionResult(
        boolean accepted,
        OrderSubmissionResult submission,
        ExecutionResult execution,
        String failureReason) { }
