package StockMainAction.view.main;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class MainViewStateTest {
    @Test
    public void updatesOnePartWithoutDiscardingLatestOtherPart() {
        MainViewState state = new MainViewState(1, 100.0, 99.0, 10);
        state = state.withPrice(2, 101.0, 100.0).withVolume(2, 20);
        assertEquals(2, state.timeStep());
        assertEquals(101.0, state.price(), 0.0);
        assertEquals(100.0, state.sma(), 0.0);
        assertEquals(20, state.volume());
    }
}
