package StockMainAction.model.core;

@FunctionalInterface
public interface TradeExecutedListener {
    void onTradeExecuted(TradeExecuted event);
}
