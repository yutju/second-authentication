package com.example.myapplication2222;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 2001;
    private static final int REQUEST_CODE_CAPTURE_IMAGE = 1001;

    private TextView resultTextView;
    private ImageView imageView;
    private ProgressBar progressBar;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private File photoFile;
    private ProcessCameraProvider cameraProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        // UI 구성 요소 초기화
        Button captureButton = findViewById(R.id.captureButton);
        Button recaptureButton = findViewById(R.id.recaptureButton);
        resultTextView = findViewById(R.id.resultTextView);
        imageView = findViewById(R.id.imageView);
        progressBar = findViewById(R.id.progressBar);
        previewView = findViewById(R.id.previewView);

        // 카메라 실행기 초기화
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 화면 크기에 맞게 레이아웃 조정
        adjustLayoutForScreenSize();

        // 카메라 권한 확인
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }

        // 버튼 리스너 설정
        captureButton.setOnClickListener(v -> takePhoto());
        recaptureButton.setOnClickListener(v -> startCamera());
    }

    // 화면 크기에 맞게 레이아웃 조정
    private void adjustLayoutForScreenSize() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);
        int screenWidth = size.x;
        int screenHeight = size.y;

        // 16:9 비율로 높이 계산
        int viewWidth = screenWidth / 2;
        int viewHeight = (viewWidth * 9) / 16;

        // PreviewView 및 ImageView의 너비와 높이 설정
        ViewGroup.LayoutParams previewLayoutParams = previewView.getLayoutParams();
        previewLayoutParams.width = viewWidth;
        previewLayoutParams.height = viewHeight;
        previewView.setLayoutParams(previewLayoutParams);

        ViewGroup.LayoutParams imageViewLayoutParams = imageView.getLayoutParams();
        imageViewLayoutParams.width = viewWidth;
        imageViewLayoutParams.height = viewHeight;
        imageView.setLayoutParams(imageViewLayoutParams);
    }

    // 모든 권한이 부여되었는지 확인
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // 카메라 시작 및 생명주기에 바인딩
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("OcrActivity", "카메라 초기화 실패", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // 카메라 생명주기에 미리보기 및 이미지 캡처 바인딩
    private void bindPreview(ProcessCameraProvider cameraProvider) {
        previewView.post(() -> {
            int previewWidth = previewView.getMeasuredWidth();
            int previewHeight = previewView.getMeasuredHeight();

            Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(previewWidth, previewHeight))
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageCapture = new ImageCapture.Builder()
                    .setTargetResolution(new Size(previewWidth, previewHeight))
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .build();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
        });
    }

    // 사진 캡처 및 저장
    private void takePhoto() {
        if (imageCapture == null) return;

        photoFile = new File(getExternalFilesDir(null), "photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(() -> {
                    Toast.makeText(OcrActivity.this, "사진이 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    updateImageView(Uri.fromFile(photoFile));
                    cameraProvider.unbindAll();
                });
                Log.d("OcrActivity", "사진 저장 위치: " + photoFile.getAbsolutePath());
                processImage(Uri.fromFile(photoFile));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(OcrActivity.this, "사진 캡처 실패", Toast.LENGTH_SHORT).show());
                Log.e("OcrActivity", "사진 캡처 실패", exception);
            }
        });
    }

    // 캡처한 사진으로 ImageView 업데이트
    private void updateImageView(Uri photoUri) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));
            imageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(OcrActivity.this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
        }
    }

    // 캡처한 이미지를 처리하고 OCR 수행
    private void processImage(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String recognizedText = text.getText();
                        Log.d("OcrActivity", "인식된 텍스트: " + recognizedText);

                        // 생년월일 추출
                        String dob = findDateOfBirth(recognizedText);
                        if (dob != null) {
                            boolean isAdult = !isMinor(dob);
                            runOnUiThread(() -> {
                                resultTextView.setText(isAdult ? "성인입니다." : "미성년자입니다.");
                                Intent resultIntent = new Intent();
                                resultIntent.putExtra("IS_ADULT", isAdult);
                                setResult(RESULT_OK, resultIntent);
                                finish();
                            });
                        } else {
                            runOnUiThread(() -> {
                                resultTextView.setText("생년월일을 찾을 수 없습니다.");
                                setResult(RESULT_CANCELED);
                                finish();
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("OcrActivity", "텍스트 인식 실패", e);
                        runOnUiThread(() -> {
                            resultTextView.setText("텍스트 인식 실패");
                            setResult(RESULT_CANCELED);
                            finish();
                        });
                    });
        } catch (IOException e) {
            Log.e("OcrActivity", "이미지 처리 실패", e);
            runOnUiThread(() -> {
                resultTextView.setText("이미지 처리 실패");
                setResult(RESULT_CANCELED);
                finish();
            });
        }
    }

    // 생년월일 찾기
    private String findDateOfBirth(String text) {
        // 한국의 생년월일 형식(YYMMDD) 정규 표현식, 면허증의 경우 맨 위 6자리는 제외
        Pattern pattern = Pattern.compile("(\\d{2}[01]\\d[0-3]\\d)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String year = matcher.group(1).substring(0, 2);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR) % 100;
            int parsedYear = Integer.parseInt(year);
            String fullYear;
            if (parsedYear <= currentYear && (currentYear - parsedYear) <= 90) {
                fullYear = "20" + year;
            } else {
                fullYear = "19" + year;
            }
            return fullYear + matcher.group(1).substring(2); // YY를 19YY 또는 20YY로 변환
        }
        return null;
    }

    // 성인 여부 확인
    private boolean isMinor(String dob) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        try {
            Date birthDate = sdf.parse(dob);
            if (birthDate != null) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.YEAR, -19); // 19세 이상 체크
                return birthDate.after(cal.getTime());
            }
        } catch (ParseException e) {
            Log.e("OcrActivity", "생년월일 파싱 실패", e);
        }
        return true; // 파싱 실패 시 미성년자로 간주
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}