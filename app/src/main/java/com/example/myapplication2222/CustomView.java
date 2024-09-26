package com.example.myapplication2222;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

public class CustomView extends View {
    private float[] beaconX = new float[3];
    private float[] beaconY = new float[3];
    private float[] beaconRadius = new float[3];
    private int[] beaconColors = new int[3];
    private float userX = -1;
    private float userY = -1;
    private Paint paint;
    private Drawable backgroundImage;

    public CustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        backgroundImage = ContextCompat.getDrawable(context, R.drawable.mart_img_example);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background image
        if (backgroundImage != null) {
            backgroundImage.setBounds(0, 0, getWidth(), getHeight());
            backgroundImage.draw(canvas);
        }

        // Draw beacons
        for (int i = 0; i < 3; i++) {
            paint.setColor(beaconColors[i]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            canvas.drawCircle(beaconX[i], beaconY[i], beaconRadius[i], paint);
        }

        // Draw user position
        if (userX >= 0 && userY >= 0) {
            paint.setColor(Color.BLUE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(userX, userY, 20, paint);
        }
    }

    public void updateBeaconPosition(int index, float x, float y, float radius, int color) {
        beaconX[index] = x;
        beaconY[index] = y;
        beaconRadius[index] = radius;
        beaconColors[index] = color;
        invalidate();
    }

    public void setUserPosition(float x, float y) {
        userX = x;
        userY = y;
        invalidate();
    }
}