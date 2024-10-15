package com.example.myapplication2222;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
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
    private TextView adultVerificationResult;
    private TextView dobExtracted;
    private LinearLayout adultVerificationLayout;
    private LinearLayout nameVerificationLayout;
    private TextView loggedInUserName;
    private TextView idCardName;
    private TextView nameComparisonResult;
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
        adultVerificationLayout = findViewById(R.id.adultVerificationLayout);
        nameVerificationLayout = findViewById(R.id.nameVerificationLayout);
        dobExtracted = findViewById(R.id.dobExtracted);

        loggedInUserName = findViewById(R.id.loggedInUserName);
        idCardName = findViewById(R.id.idCardName);
        nameComparisonResult = findViewById(R.id.nameComparisonResult);
        adultVerificationResult = findViewById(R.id.adultVerificationResult);
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

        // 초기에는 성인 인증 및 본인 인증 레이아웃을 숨김
        adultVerificationLayout.setVisibility(View.GONE);
        nameVerificationLayout.setVisibility(View.GONE);
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

    // OCR 후 이미지 처리 개선
    private void processImage(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String recognizedText = text.getText();
                        Log.d("OcrActivity", "인식된 텍스트: " + recognizedText);

                        // 생년월일, 이름 및 주민등록번호 추출
                        String dob = findDateOfBirth(recognizedText);
                        String name = findName(recognizedText);
                        String ssn = findSSN(recognizedText);
                        if (ssn != null) {
                            ssn = ssn.replaceAll("\\s+", "").replaceAll("-", ""); // 공백과 하이픈 제거
                        }

                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

                        // 주민등록번호에 하이픈 추가
                        String ssnWithHyphen = null;
                        if (ssn != null && ssn.length() == 13) {
                            ssnWithHyphen = ssn.substring(0, 6) + "-" + ssn.substring(6);
                        }
                        final String finalSSNWithHyphen = ssnWithHyphen;

                        runOnUiThread(() -> {
                            if (dob != null && !isMinor(dob)) {
                                // 1차 성인 인증 진행
                                adultVerificationLayout.setVisibility(View.VISIBLE);
                                adultVerificationResult.setText("1차 성인 인증을 진행합니다...");

                                new Handler().postDelayed(() -> {
                                    // 1차 성인 인증 성공
                                    dobExtracted.setText("추출된 생년월일: " + dob);
                                    dobExtracted.setVisibility(View.VISIBLE);
                                    adultVerificationResult.setText("1차 성인 인증이 완료되었습니다.");

                                    // 2차 본인 인증 진행 (지연 후 진행)
                                    nameComparisonResult.setText("2차 본인 인증을 진행합니다...");
                                    nameVerificationLayout.setVisibility(View.VISIBLE);

                                    new Handler().postDelayed(() -> {
                                        if (name != null && finalSSNWithHyphen != null && currentUser != null) {
                                            getCurrentUserSSN(currentUser.getUid(), currentUserSSN -> {
                                                if (currentUserSSN != null) {
                                                    // 주민등록번호 비교 시 '-'를 제거하고 비교
                                                    currentUserSSN = currentUserSSN.replace("-", "");

                                                }else {
                                                    // 오류 처리
                                                    runOnUiThread(() -> {
                                                        Toast.makeText(OcrActivity.this, "사용자 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                                                    });
                                                }

                                                final String currentUserName = currentUser.getDisplayName();
                                                final String finalCurrentUserSSN = currentUserSSN;

                                                boolean isNameMatch = currentUserName != null && currentUserName.equalsIgnoreCase(name);
                                                boolean isSSNMatch = finalCurrentUserSSN != null && finalCurrentUserSSN.equals(finalSSNWithHyphen.replace("-", ""));

                                                runOnUiThread(() -> {
                                                    idCardName.setText("신분증 이름: " + name);
                                                    loggedInUserName.setText("로그인된 사용자 이름: " + currentUserName);

                                                    if (isNameMatch && isSSNMatch) {
                                                        nameComparisonResult.setText("2차 본인 인증이 완료되었습니다.");
                                                        // OrderSummaryActivity로 이동
                                                        Intent intent = new Intent(OcrActivity.this, OrderSummaryActivity.class);
                                                        startActivity(intent);
                                                        finish();
                                                    } else {
                                                        nameComparisonResult.setText("본인 인증 실패: 이름 또는 주민등록번호가 일치하지 않습니다.");
                                                    }
                                                });
                                            });
                                        } else {
                                            runOnUiThread(() -> {
                                                resultTextView.setText("본인 인증 실패: 이름 또는 주민등록번호를 확인할 수 없습니다.");
                                                Toast.makeText(OcrActivity.this, "본인 인증 실패: 이름 또는 주민등록번호를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show();
                                            });
                                        }
                                    }, 1500); // 1.5초 후 2차 본인 인증 진행
                                }, 1500); // 1.5초 후 1차 성인 인증 완료 메시지
                            } else if (dob != null) {
                                resultTextView.setText("미성년자입니다.");
                                Toast.makeText(OcrActivity.this, "미성년자는 구매가 불가합니다.", Toast.LENGTH_SHORT).show();
                            } else {
                                resultTextView.setText("생년월일을 찾을 수 없습니다.");
                                Toast.makeText(OcrActivity.this, "생년월일을 찾을 수 없습니다. 신분증을 다시 확인해주세요.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e("OcrActivity", "텍스트 인식 실패", e);
                        runOnUiThread(() -> {
                            resultTextView.setText("텍스트 인식 실패");
                            Toast.makeText(OcrActivity.this, "텍스트 인식 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
                        });
                    });
        } catch (IOException e) {
            Log.e("OcrActivity", "이미지 처리 실패", e);
            runOnUiThread(() -> {
                resultTextView.setText("이미지 처리 실패");
                Toast.makeText(OcrActivity.this, "이미지 처리 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    // Firestore에서 사용자의 주민등록번호를 가져오는 메서드 (콜백 방식으로 수정)
    private void getCurrentUserSSN(String userId, FirestoreCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String ssn = documentSnapshot.getString("ssn");
                callback.onCallback(ssn);
            } else {
                callback.onCallback(null);
            }
        }).addOnFailureListener(e -> {
            Log.e("OcrActivity", "사용자 주민등록번호 가져오기 실패", e);
            callback.onCallback(null);
        });
    }

    private interface FirestoreCallback {
        void onCallback(String ssn);
    }
    // 주민등록번호 찾기 추가
    private String findSSN(String text) {
        // 주민등록번호 형식 (YYMMDD-XXXXXXX)을 추출
        Pattern ssnPattern = Pattern.compile("\\d{6}-\\d{7}");
        Matcher ssnMatcher = ssnPattern.matcher(text.replaceAll("\\s+", "")); // 공백 제거
        if (ssnMatcher.find()) {
            return ssnMatcher.group(0);
        }
        return null;
    }


    // 생년월일 찾기 개선
    private String findDateOfBirth(String text) {
        // 생년월일 형식 (YYMMDD)을 추출
        Pattern dobPattern = Pattern.compile("(\\d{2}[01]\\d[0-3]\\d)");
        Matcher dobMatcher = dobPattern.matcher(text);
        if (dobMatcher.find()) {
            String year = dobMatcher.group(1).substring(0, 2);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR) % 100;
            int parsedYear = Integer.parseInt(year);
            String fullYear;
            if (parsedYear <= currentYear) {
                fullYear = "20" + year;
            } else {
                fullYear = "19" + year;
            }

            String month = dobMatcher.group(1).substring(2, 4);
            String day = dobMatcher.group(1).substring(4, 6);
            return fullYear + "년 " + Integer.parseInt(month) + "월 " + Integer.parseInt(day) + "일";
        }
        return null;
    }

    private String findName(String text) {
        // 주민등록번호 패턴을 찾아서 그 위의 텍스트를 찾음
        Pattern ssnPattern = Pattern.compile("\\d{6}-\\d{7}");
        Matcher ssnMatcher = ssnPattern.matcher(text);

        if (ssnMatcher.find()) {
            // 주민등록번호의 위치를 기준으로 그 위의 텍스트를 찾음
            int ssnStartIndex = ssnMatcher.start();

            // 주민등록번호 위의 부분 텍스트 추출
            String textBeforeSSN = text.substring(0, ssnStartIndex).trim();

            // 마지막 줄의 이름 추출
            String[] lines = textBeforeSSN.split("\\n");
            if (lines.length > 0) {
                String possibleNameLine = lines[lines.length - 1].trim();

                // 괄호 안의 텍스트 제거
                possibleNameLine = possibleNameLine.replaceAll("\\([^)]*\\)", "").trim();

                // 한글 이름 패턴 (2~4자)
                Pattern namePattern = Pattern.compile("[가-힣]{2,4}");
                Matcher nameMatcher = namePattern.matcher(possibleNameLine);
                if (nameMatcher.find()) {
                    // 이름을 찾아서 전처리 후 반환
                    return cleanName(nameMatcher.group(0));
                }
            }
        }

        return null;
    }

    // 이름 전처리 메서드 개선 (괄호 및 특수문자 제거)
    private String cleanName(String name) {
        // 한글만 남기기 (특수문자, 괄호, 숫자, 영어 등 제거)
        return name.replaceAll("[^가-힣]", "");
    }



    // 성인 여부 확인
    private boolean isMinor(String dob) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault());
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
