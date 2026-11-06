package top.cnpclines.faction;

/** Reference image measured in GUI pixels. Windows can only shrink the whole layout. */
public record FactionLayout(float scale, int x, int y) {
    public static final int WIDTH = 216, HEIGHT = 202, LEFT = 80, RIGHT = 114;
    public static final int FLAG_WIDTH = 26, FLAG_HEIGHT = 50, BAR_WIDTH = 102, BAR_HEIGHT = 6, HEART = 11;

    public static FactionLayout fit(int width, int height, boolean edit) {
        int totalHeight = HEIGHT + (edit ? 40 : 30);
        float scale = Math.min(1f, Math.min((width - 16f) / WIDTH, (height - 16f) / totalHeight));
        return new FactionLayout(scale, (int)(width / scale - WIDTH) / 2,
            (int)(height / scale - totalHeight) / 2 + (edit ? 0 : 30));
    }
}
