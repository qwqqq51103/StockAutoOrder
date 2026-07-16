package StockMainAction.model.account;

public record AccountMutationResult(
        long sequence,
        AccountOperation operation,
        boolean success,
        String failureReason,
        AccountSnapshot before,
        AccountSnapshot after) { }
