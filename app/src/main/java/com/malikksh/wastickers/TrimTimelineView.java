package com.malikksh.wastickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Visual range handles; the labelled SeekBars provide equivalent TalkBack actions. */
final class TrimTimelineView extends View {
    interface Listener { void onBoundary(long timeMs, boolean start, boolean settled); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap[] frames = new Bitmap[0];
    private long durationMs = 1L, startMs, endMs;
    private boolean draggingStart;
    private Listener listener;

    TrimTimelineView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setListener(Listener value) { listener = value; }
    void setRange(long duration, long start, long end) {
        durationMs = Math.max(1L, duration); startMs = start; endMs = end;
        invalidate();
    }
    void setFrames(Bitmap[] value) {
        Bitmap[] old = frames;
        frames = value == null ? new Bitmap[0] : value;
        invalidate();
        for (Bitmap frame : old) if (frame != null && !frame.isRecycled()) frame.recycle();
    }
    private float inset() { return 12f * getResources().getDisplayMetrics().density; }
    private float x(long time) { return inset() + (getWidth() - 2f * inset()) * time / durationMs; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = inset(), right = getWidth() - inset(), height = getHeight();
        paint.setColor(getContext().getColor(R.color.app_surface_variant));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(left, 4f, right, height - 4f), 8f, 8f, paint);
        for (int i = 0; i < frames.length; i++) {
            Bitmap frame = frames[i];
            if (frame == null || frame.isRecycled()) continue;
            float width = (right - left) / frames.length;
            int side = Math.min(frame.getWidth(), frame.getHeight());
            int sx = (frame.getWidth() - side) / 2, sy = (frame.getHeight() - side) / 2;
            canvas.drawBitmap(frame, new Rect(sx, sy, sx + side, sy + side),
                    new RectF(left + i * width, 4f, left + (i + 1) * width, height - 4f), paint);
        }
        paint.setColor(getContext().getColor(R.color.app_background));
        paint.setAlpha(170);
        canvas.drawRect(left, 4f, x(startMs), height - 4f, paint);
        canvas.drawRect(x(endMs), 4f, right, height - 4f, paint);
        paint.setAlpha(255);
        paint.setColor(getContext().getColor(R.color.app_primary));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        canvas.drawRect(x(startMs), 4f, x(endMs), height - 4f, paint);
        paint.setStyle(Paint.Style.FILL);
        float halfHandle = 4f * getResources().getDisplayMetrics().density;
        canvas.drawRoundRect(new RectF(x(startMs) - halfHandle, 0f, x(startMs) + halfHandle, height), halfHandle, halfHandle, paint);
        canvas.drawRoundRect(new RectF(x(endMs) - halfHandle, 0f, x(endMs) + halfHandle, height), halfHandle, halfHandle, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            draggingStart = Math.abs(event.getX() - x(startMs)) < Math.abs(event.getX() - x(endMs));
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            return false;
        }
        float fraction = (event.getX() - inset()) / Math.max(1f, getWidth() - 2f * inset());
        long requested = Math.round(Math.max(0f, Math.min(1f, fraction)) * durationMs / VideoTrimPolicy.SEEK_STEP_MS)
                * VideoTrimPolicy.SEEK_STEP_MS;
        boolean settled = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        listener.onBoundary(requested, draggingStart, settled);
        if (settled) { getParent().requestDisallowInterceptTouchEvent(false); performClick(); }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
