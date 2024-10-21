package com.example.myapplication2222;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class FaceOverlay extends View {
    private Paint paint;

    public FaceOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 얼굴형 가이드라인 그리기 - 화면 중앙에 고정된 위치에 표시
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float faceWidth = getWidth() * 0.5f;
        float faceHeight = getHeight() * 0.6f;

        Path facePath = new Path();
        facePath.moveTo(centerX, centerY - faceHeight / 2); // 이마 부분
        facePath.cubicTo(centerX + faceWidth / 2, centerY - faceHeight / 3, centerX + faceWidth / 2, centerY + faceHeight / 3, centerX, centerY + faceHeight / 2); // 오른쪽 턱 부분
        facePath.cubicTo(centerX - faceWidth / 2, centerY + faceHeight / 3, centerX - faceWidth / 2, centerY - faceHeight / 3, centerX, centerY - faceHeight / 2); // 왼쪽 턱 부분
        facePath.close();

        canvas.drawPath(facePath, paint);
    }
}
