package top.cnpclines.client.gui;

final class SwipeDragController {

    static final double DELETE_THRESHOLD = 96.0;

    private static final long LONG_PRESS_MILLIS = 88L;

    private static final double GESTURE_DEADZONE = 4.0;

    enum State {
        IDLE,
        MOUSE_DOWN,
        LONG_PRESS,
        DELETE,
    }

    private State state = State.IDLE;

    private double startX;
    private double startY;

    private double x;
    private double y;

    private long startedAt;

    void begin(double x, double y, long now) {
        this.state = State.MOUSE_DOWN;
        this.startX = x;
        this.startY = y;
        this.x = x;
        this.y = y;
        this.startedAt = now;
    }

    void move(double x, double y) {
        this.x = x;
        this.y = y;
    }

    void update(long now) {
        if (this.state == State.MOUSE_DOWN && now - this.startedAt >= LONG_PRESS_MILLIS) {
            this.state = State.LONG_PRESS;
        }
    }

    void chooseGesture() {
        if (this.state != State.LONG_PRESS) {
            return;
        }
        double dx = this.x - this.startX;
        double dy = this.y - this.startY;
        if (Math.abs(dx) < GESTURE_DEADZONE && Math.abs(dy) < GESTURE_DEADZONE) {
            return;
        }
        if (dx < 0 && Math.abs(dx) > Math.abs(dy)) {
            this.state = State.DELETE;
        }
    }

    void end() {
        this.state = State.IDLE;
    }

    boolean active() {
        return this.state != State.IDLE;
    }

    State state() {
        return this.state;
    }

    double x() {
        return this.x;
    }

    double y() {
        return this.y;
    }

    double startX() {
        return this.startX;
    }

    double startY() {
        return this.startY;
    }

    double deltaX() {
        return this.x - this.startX;
    }

    double deltaY() {
        return this.y - this.startY;
    }
}
