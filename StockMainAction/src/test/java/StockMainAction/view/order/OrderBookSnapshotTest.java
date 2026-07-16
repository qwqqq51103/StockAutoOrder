package StockMainAction.view.order;

import StockMainAction.model.core.OrderSide;
import StockMainAction.model.core.OrderStatus;
import StockMainAction.model.core.OrderType;
import StockMainAction.view.order.OrderBookSnapshot.OrderRow;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OrderBookSnapshotTest {
    @Test
    public void copiesInputAndBuildsAllAndPersonalViews() {
        OrderRow buy = row("buy", "PERSONAL", OrderSide.BUY, 1);
        OrderRow sell = row("sell", "MAIN_FORCE", OrderSide.SELL, 2);
        List<OrderRow> buys = new ArrayList<>(List.of(buy));
        OrderBookSnapshot snapshot = new OrderBookSnapshot(buys, List.of(sell));

        buys.clear();

        assertEquals(List.of(buy), snapshot.buys());
        assertEquals(List.of(buy, sell), snapshot.allOrders());
        assertEquals(List.of(buy), snapshot.personalOrders());
    }

    private static OrderRow row(String id, String traderType, OrderSide side, long sequence) {
        return new OrderRow(id, traderType, side, OrderType.LIMIT, OrderStatus.OPEN,
                100.0, 10, 1_000L, sequence);
    }
}
