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
    private float[] beaconX = new float[3]; // 비콘 X 위치
    private float[] beaconY = new float[3]; // 비콘 Y 위치
    private float[] beaconRadius = new float[3]; // 비콘 반지름
    private int[] beaconColors = new int[3]; // 비콘 색상
    private float userX = -1; // 사용자 X 위치
    private float userY = -1; // 사용자 Y 위치
    private Paint paint;
    private Drawable backgroundImage;

    public CustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        backgroundImage = ContextCompat.getDrawable(context, R.drawable.mart_img_example);

        // 비콘의 고정된 위치와 색상 설정
        beaconX[0] = 0; // 첫 번째 비콘 X 위치
        beaconY[0] = 3; // 첫 번째 비콘 Y 위치
        beaconColors[0] = Color.RED; // 첫 번째 비콘 색상

        beaconX[1] = 1.5f; // 두 번째 비콘 X 위치
        beaconY[1] = 0; // 두 번째 비콘 Y 위치
        beaconColors[1] = Color.YELLOW; // 두 번째 비콘 색상

        beaconX[2] = 3; // 세 번째 비콘 X 위치
        beaconY[2] = 3; // 세 번째 비콘 Y 위치
        beaconColors[2] = Color.GREEN; // 세 번째 비콘 색상
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 배경 이미지 그리기
        if (backgroundImage != null) {
            backgroundImage.setBounds(0, 0, getWidth(), getHeight());
            backgroundImage.draw(canvas);
        }

        // 고정된 비콘 위치 그리기
        for (int i = 0; i < 3; i++) {
            // 비콘 위치를 화면 크기에 맞춰 변환
            float beaconXScreen = (float) (beaconX[i] / 3 * getWidth());
            float beaconYScreen = (float) ((3 - beaconY[i]) / 3 * getHeight());
            paint.setColor(beaconColors[i]);
            paint.setStyle(Paint.Style.FILL);
            // 고정된 비콘 위치 점으로 그리기
            canvas.drawCircle(beaconXScreen, beaconYScreen, 10, paint); // 고정된 위치를 점으로 표시

            // 비콘 원 그리기
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            canvas.drawCircle(beaconXScreen, beaconYScreen, beaconRadius[i], paint);
        }

        // 사용자 위치 그리기
        if (userX >= 0 && userY >= 0) {
            paint.setColor(Color.BLUE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(userX, userY, 20, paint);
        }
    }

    public void updateBeaconPosition(int index, float x, float y, float radius, int color) {
        // 고정된 위치에 따라 비콘의 색상 및 반지름만 업데이트하도록 수정
        beaconRadius[index] = radius; // 반지름만 업데이트
        beaconColors[index] = color; // 색상만 업데이트
        invalidate();
    }

    public void setUserPosition(float x, float y) {
        userX = x;
        userY = y;
        invalidate();
    }
}
