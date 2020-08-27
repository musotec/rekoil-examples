package tech.muso.demo.graph.spark;

import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Exposes simple methods for detecting scrub events.
 */
class ScrubGestureDetector implements View.OnTouchListener {
    static final long LONG_PRESS_TIMEOUT_MS = 250;

    private final ScrubListener scrubListener;
    private final float touchSlop;
    private final Handler handler;
    private boolean started;
    private GestureDetector secondaryGestureDetector;

    private boolean enabled;
    private float downX, downY;

    ScrubGestureDetector(
            @NonNull ScrubListener scrubListener,
            @NonNull Handler handler,
            float touchSlop,
            GestureDetector secondaryGestureDetector
    ) {
        this.scrubListener = scrubListener;
        this.handler = handler;
        this.touchSlop = touchSlop;
        this.secondaryGestureDetector = secondaryGestureDetector;
    }

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            started = true;
            scrubListener.onScrubStart(downX, downY);
            scrubListener.onScrubbed(downX, downY);
        }
    };

    private final Runnable vibrateRunnable = new Runnable() {
        @Override
        public void run() {
            scrubListener.onVibrate();
        }
    };

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    private boolean consuming = false;
    public boolean isConsuming() {
        return this.consuming;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!enabled) return false;

        if (secondaryGestureDetector != null)
            secondaryGestureDetector.onTouchEvent(event);

        final float x = event.getX();
        final float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // store the time to compute whether future events are 'long presses'
                downX = x;
                downY = y;

                handler.postDelayed(vibrateRunnable, LONG_PRESS_TIMEOUT_MS);
                handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                // calculate the elapsed time since the down event
                float timeDelta = event.getEventTime() - event.getDownTime();

                // if the user has intentionally long-pressed
                if (timeDelta >= LONG_PRESS_TIMEOUT_MS) {
                    handler.removeCallbacks(longPressRunnable);
                    scrubListener.onScrubbed(x, y);
                } else {
                    // if we moved before long-press, (we are in this else branch)
                    // -> and we exceed the touch slop -> remove the long press runnable;
                    float deltaX = x - downX;
                    float deltaY = y - downY;
                    if (deltaX >= touchSlop || deltaY >= touchSlop) {
                        // slop exceed, movement non-accidental; stop listening for long-press.
                        handler.removeCallbacks(longPressRunnable);
                        return false;
                    }
                }

                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(vibrateRunnable);
                if (started)
                    scrubListener.onScrubEnded();
                return true;
            default:
                return false;
        }
    }

    interface ScrubListener {
        void onVibrate();
        void onScrubStart(float x, float y);
        void onScrubbed(float x, float y);
        void onScrubEnded();
    }
}

