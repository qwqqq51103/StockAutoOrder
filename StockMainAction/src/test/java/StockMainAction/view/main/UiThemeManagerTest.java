package StockMainAction.view.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import javax.swing.UIManager;
import org.junit.Test;

public class UiThemeManagerTest {
    @Test
    public void lightThemeRestoresCapturedDefaults() {
        Object original = UIManager.get("Panel.background");
        UiThemeManager manager = new UiThemeManager();
        manager.apply(true, null);
        assertNotEquals(original, UIManager.get("Panel.background"));
        manager.apply(false, null);
        assertEquals(original, UIManager.get("Panel.background"));
    }
}
