package StockMainAction.service;

import StockMainAction.model.PersonalAI;
import StockMainAction.model.core.ExecutionResult;
import StockMainAction.model.core.OrderSubmissionResult;
import java.util.Objects;

/** Application service for personal trading commands. */
public final class PersonalTradeService {
    private final PersonalAI trader;

    public PersonalTradeService(PersonalAI trader) {
        this.trader = Objects.requireNonNull(trader, "trader");
    }

    public ExecutionResult marketBuy(int quantity) {
        return trader.executeMarketBuyResult(quantity);
    }

    public ExecutionResult marketSell(int quantity) {
        return trader.executeMarketSellResult(quantity);
    }

    public OrderSubmissionResult limitBuy(int quantity, double price) {
        return trader.submitLimitBuy(quantity, price);
    }

    public OrderSubmissionResult limitSell(int quantity, double price) {
        return trader.submitLimitSell(quantity, price);
    }

    public ExecutionResult fokBuy(int quantity, double price) {
        return trader.executeFokBuyResult(quantity, price);
    }

    public ExecutionResult fokSell(int quantity, double price) {
        return trader.executeFokSellResult(quantity, price);
    }
}
