package top.cnpclines.faction;

/** A stable axis based on thresholds, never on the player's current points. */
public final class FactionStanding {
    private final double min, max;
    public FactionStanding(double min, double max) { this.min = min; this.max = max; }
    public double min() { return min; }
    public double max() { return max; }
    public static int relation(int points, int neutral, int friendly) {
        return points < neutral ? -1 : points < friendly ? 0 : 1;
    }

    public static FactionStanding scale(int neutral, int friendly) {
        double low = Math.min(neutral, friendly), high = Math.max(neutral, friendly);
        double margin = Math.max(1, high - low) / 2;
        return new FactionStanding(Math.min(0, low - margin), high + margin);
    }

    public double fraction(int points) {
        return Math.max(0, Math.min(1, (points - min) / (max - min)));
    }
}
