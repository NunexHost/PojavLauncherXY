package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.GestureDetector;
import android.view.MotionEvent;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.SingleTapConfirm;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

public class InGUIEventProcessor implements TouchEventProcessor {

    // Constants for touch thresholds
    private static final float FINGER_SCROLL_THRESHOLD = Tools.dpToPx(6);
    private static final float FINGER_STILL_THRESHOLD = Tools.dpToPx(5);

    // Objects for tracking touch events and gestures
    private final PointerTracker mTracker = new PointerTracker();
    private final TapDetector mSingleTapDetector;
    private AbstractTouchpad mTouchpad;
    private boolean mIsMouseDown = false;
    private float mStartX, mStartY;
    private final float mScaleFactor;
    private final Scroller mScroller = new Scroller(FINGER_SCROLL_THRESHOLD);

    // Constructor
    public InGUIEventProcessor(float scaleFactor) {
        mSingleTapDetector = new TapDetector(1, TapDetector.DETECTION_METHOD_BOTH);
        mScaleFactor = scaleFactor;
    }

    // Process touch events
    @Override
    public boolean processTouchEvent(MotionEvent motionEvent) {
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTracker.startTracking(motionEvent);
                if (!touchpadDisplayed()) {
                    sendTouchCoordinates(motionEvent.getX(), motionEvent.getY());

                    // Disable gestures if disabled in preferences
                    if (LauncherPreferences.PREF_DISABLE_GESTURES) {
                        enableMouse();
                    } else {
                        setGestureStart(motionEvent);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                int pointerCount = motionEvent.getPointerCount();
                int pointerIndex = mTracker.trackEvent(motionEvent);

                // Handle single pointer movement
                if (pointerCount == 1 || LauncherPreferences.PREF_DISABLE_GESTURES) {
                    if (touchpadDisplayed()) {
                        // Apply motion vector to touchpad (considering inversion)
                        float[] motionVector = mTracker.getMotionVector();
                        if (!CallbackBridge.nativeGetInverted()) {
                            mTouchpad.applyMotionVector(motionVector);
                        } else {
                            float[] reversed = new float[2];
                            reversed[0] = motionVector[1];
                            reversed[1] = motionVector[0];
                            mTouchpad.applyMotionVector(reversed);
                        }
                    } else {
                        float mainPointerX = motionEvent.getX(pointerIndex);
                        float mainPointerY = motionEvent.getY(pointerIndex);
                        sendTouchCoordinates(mainPointerX, mainPointerY);

                        // Enable mouse if movement exceeds still threshold
                        if (!mIsMouseDown) {
                            if (!hasGestureStarted()) {
                                setGestureStart(motionEvent);
                            }
                            if (!LeftClickGesture.isFingerStill(mStartX, mStartY, FINGER_STILL_THRESHOLD)) {
                                enableMouse();
                            }
                        }
                    }
                } else {
                    // Handle multi-pointer scrolling
                    mScroller.performScroll(mTracker.getMotionVector());
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mScroller.resetScrollOvershoot();
                mTracker.cancelTracking();
                if (mIsMouseDown) {
                    disableMouse();
                }
                resetGesture();
        }

        // Check for single tap and click mouse if detected
        if ((!LauncherPreferences.PREF_DISABLE_GESTURES || touchpadDisplayed()) && mSingleTapDetector.onTouchEvent(motionEvent)) {
            clickMouse();
        }
        return true;
    }

    // Check if touchpad is displayed
    private boolean touchpadDisplayed() {
        return mTouchpad != null && mTouchpad.getDisplayState();
    }

    public void setAbstractTouchpad(AbstractTouchpad touchpad) {
        mTouchpad = touchpad;
    }

    private void sendTouchCoordinates(float x, float y) {
        CallbackBridge.sendCursorPos( x * mScaleFactor, y * mScaleFactor);
    }

    private void enableMouse() {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true);
        mIsMouseDown = true;
    }

    private void disableMouse() {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
        mIsMouseDown = false;
    }

    private void clickMouse() {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true);
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
    }

    private void setGestureStart(MotionEvent event) {
        mStartX = event.getX();
        mStartY = event.getY();
    }

    private void resetGesture() {
        mStartX = mStartY = -1;
    }

    private boolean hasGestureStarted() {
        return mStartX != -1 || mStartY != -1;
    }

    @Override
    public void cancelPendingActions() {
        mScroller.resetScrollOvershoot();
        disableMouse();
    }
}
