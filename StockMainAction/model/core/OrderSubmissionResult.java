package StockMainAction.model.core;

public record OrderSubmissionResult(String orderId, boolean accepted, String failureReason) { }
