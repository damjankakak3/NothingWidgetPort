import dev.dipa.widgets.Logic;
public class LogicTest {
    public static void main(String[] args) {
        if (Logic.daysUntil("2026-03-28", "2026-03-30") != 2) throw new AssertionError("DST day count");
        if (Logic.daysUntil("2026-10-06", "2026-10-05") != -1) throw new AssertionError("Past countdown");
        if (Logic.stepDelta(980, 1000) != 20 || Logic.stepDelta(1000, 5) != 0 || Logic.stepDelta(-1, 8000) != 0) throw new AssertionError("Counter reset/baseline");
        if (!Logic.duration(3700000).equals("1h 1m") || !Logic.duration(-10).equals("0m")) throw new AssertionError("Duration");
        if (!Logic.weather(0).equals("Clear") || !Logic.weather(95).equals("Thunderstorm")) throw new AssertionError("Weather code");
        System.out.println("Logic checks passed");
    }
}
