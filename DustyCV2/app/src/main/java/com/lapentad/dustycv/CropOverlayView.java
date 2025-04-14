package com.lapentad.dustycv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CropOverlayView extends View {
    private Paint borderPaint;
    private Paint cornerPaint;
    private Paint overlayPaint;
    private Bitmap imageBitmap;
    private RectF cropRect;
    private float cornerSize = 50;
    private float touchTolerance = 20;
    private boolean isDragging = false;
    private boolean isResizing = false;
    private int activeCorner = -1;
    private float lastX, lastY;
    private float scale = 1.0f;
    private float offsetX = 0;
    private float offsetY = 0;
    private float padding = 50; // Padding in pixels

    public CropOverlayView(Context context) {
        super(context);
        init();
    }

    public CropOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);

        cornerPaint = new Paint();
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.FILL);

        overlayPaint = new Paint();
        overlayPaint.setColor(Color.argb(128, 0, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (imageBitmap != null) {
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            
            // Calculate scale to fit the image with padding
            float availableWidth = width - (2 * padding);
            float availableHeight = height - (2 * padding);
            float scaleX = availableWidth / imageBitmap.getWidth();
            float scaleY = availableHeight / imageBitmap.getHeight();
            scale = Math.min(scaleX, scaleY);
            
            // Calculate offsets to center the image with padding
            offsetX = (width - (imageBitmap.getWidth() * scale)) / 2;
            offsetY = (height - (imageBitmap.getHeight() * scale)) / 2;
            
            // Initialize crop rect if it doesn't exist
            if (cropRect == null) {
                cropRect = new RectF(0, 0, imageBitmap.getWidth(), imageBitmap.getHeight());
            }
        }
    }

    public void setImageBitmap(Bitmap bitmap) {
        this.imageBitmap = bitmap;
        if (bitmap != null) {
            // Initialize crop rect to match image dimensions
            cropRect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            // Request layout to recalculate scale and offsets
            requestLayout();
            invalidate();
        }
    }

    public Bitmap getCroppedBitmap() {
        if (imageBitmap == null || cropRect == null) return null;
        
        // Convert crop rectangle coordinates back to bitmap space
        int x = (int) cropRect.left;
        int y = (int) cropRect.top;
        int width = (int) cropRect.width();
        int height = (int) cropRect.height();
        
        // Ensure coordinates are within bitmap bounds
        x = Math.max(0, Math.min(x, imageBitmap.getWidth() - 1));
        y = Math.max(0, Math.min(y, imageBitmap.getHeight() - 1));
        width = Math.min(width, imageBitmap.getWidth() - x);
        height = Math.min(height, imageBitmap.getHeight() - y);
        
        return Bitmap.createBitmap(imageBitmap, x, y, width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (imageBitmap == null || cropRect == null) return;

        // Draw semi-transparent overlay
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        
        // Scale and translate the canvas to match the image
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        
        // Draw the crop rectangle
        canvas.drawRect(cropRect, borderPaint);

        // Draw corners
        float[] corners = {
            cropRect.left, cropRect.top,
            cropRect.right, cropRect.top,
            cropRect.right, cropRect.bottom,
            cropRect.left, cropRect.bottom
        };

        for (int i = 0; i < 8; i += 2) {
            canvas.drawCircle(corners[i], corners[i + 1], cornerSize / 2, cornerPaint);
        }
        
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (cropRect == null) return false;

        // Convert screen coordinates to bitmap coordinates, accounting for padding
        float x = (event.getX() - offsetX) / scale;
        float y = (event.getY() - offsetY) / scale;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                activeCorner = getCornerAt(x, y);
                isDragging = activeCorner == -1;
                isResizing = activeCorner != -1;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = x - lastX;
                float dy = y - lastY;

                if (isDragging) {
                    // Move the crop rectangle
                    float newLeft = cropRect.left + dx;
                    float newTop = cropRect.top + dy;
                    float newRight = cropRect.right + dx;
                    float newBottom = cropRect.bottom + dy;

                    // Check bounds with padding
                    if (newLeft >= 0 && newRight <= imageBitmap.getWidth() &&
                        newTop >= 0 && newBottom <= imageBitmap.getHeight()) {
                        cropRect.offset(dx, dy);
                    }
                } else if (isResizing) {
                    resizeCropRect(x, y);
                }

                lastX = x;
                lastY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                isDragging = false;
                isResizing = false;
                activeCorner = -1;
                return true;
        }
        return false;
    }

    private int getCornerAt(float x, float y) {
        float[] corners = {
            cropRect.left, cropRect.top,
            cropRect.right, cropRect.top,
            cropRect.right, cropRect.bottom,
            cropRect.left, cropRect.bottom
        };

        for (int i = 0; i < 8; i += 2) {
            if (Math.abs(x - corners[i]) < touchTolerance && 
                Math.abs(y - corners[i + 1]) < touchTolerance) {
                return i / 2;
            }
        }
        return -1;
    }

    private void resizeCropRect(float x, float y) {
        // Ensure coordinates are within bitmap bounds
        x = Math.max(0, Math.min(x, imageBitmap.getWidth()));
        y = Math.max(0, Math.min(y, imageBitmap.getHeight()));

        switch (activeCorner) {
            case 0: // Top-left
                cropRect.left = Math.min(x, cropRect.right - cornerSize);
                cropRect.top = Math.min(y, cropRect.bottom - cornerSize);
                break;
            case 1: // Top-right
                cropRect.right = Math.max(x, cropRect.left + cornerSize);
                cropRect.top = Math.min(y, cropRect.bottom - cornerSize);
                break;
            case 2: // Bottom-right
                cropRect.right = Math.max(x, cropRect.left + cornerSize);
                cropRect.bottom = Math.max(y, cropRect.top + cornerSize);
                break;
            case 3: // Bottom-left
                cropRect.left = Math.min(x, cropRect.right - cornerSize);
                cropRect.bottom = Math.max(y, cropRect.top + cornerSize);
                break;
        }
    }
} 