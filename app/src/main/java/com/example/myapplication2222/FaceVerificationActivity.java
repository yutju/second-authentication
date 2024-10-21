package com.example.myapplication2222;

import android.os.Handler;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

public class FaceVerificationActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 2001;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private ProgressBar progressBar;
    private Button captureLivePhotoButton;
    private Button verifyFaceButton;
    private Button switchCameraButton;
    private ImageView idCardFaceImageView;
    private ImageView liveFaceImageView;
    private Bitmap idCardFaceBitmap;
    private boolean isFaceMatched = false;
    private static final float FACE_MATCH_THRESHOLD = 0.9f;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private Interpreter tflite; // 기존 mobileFaceNet을 tflite로 대체


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_verification);

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressBar);
        captureLivePhotoButton = findViewById(R.id.captureLivePhotoButton);
        verifyFaceButton = findViewById(R.id.verifyFaceButton);
        idCardFaceImageView = findViewById(R.id.idCardFaceImageView);
        liveFaceImageView = findViewById(R.id.liveFaceImageView);
        switchCameraButton = findViewById(R.id.switchCameraButton);

        cameraExecutor = Executors.newSingleThreadExecutor();
        // Load DNN model for face detection
        try {
            loadMobileFaceNetModel(); // tflite 초기화
        } catch (IOException e) {
            Log.e("FaceVerificationActivity", "DNN 모델 로드 실패", e);
        }
        // 카메라 권한 확인
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }

        captureLivePhotoButton.setOnClickListener(v -> takeIdCardPhoto());
        verifyFaceButton.setOnClickListener(v -> takeLivePhotoAndCompare());
        switchCameraButton.setOnClickListener(v -> switchCamera());
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                if (cameraProvider != null) {
                    bindPreview(cameraProvider);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("FaceVerificationActivity", "카메라 초기화 실패", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().build();

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }

    private void switchCamera() {
        if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        } else {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        }
        if (cameraProvider != null) {
            bindPreview(cameraProvider);
        }
    }

    private void takeIdCardPhoto() {
        if (imageCapture == null) return;

        File idCardPhotoFile = new File(getExternalFilesDir(null), "id_card_photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(idCardPhotoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Bitmap originalBitmap = BitmapFactory.decodeFile(idCardPhotoFile.getAbsolutePath());
                if (originalBitmap != null) {
                    Bitmap rotatedBitmap = rotateBitmap(originalBitmap, 90);
                    idCardFaceBitmap = rotatedBitmap;
                    runOnUiThread(() -> {
                        idCardFaceImageView.setImageBitmap(rotatedBitmap);
                        Toast.makeText(FaceVerificationActivity.this, "신분증 얼굴 사진이 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    });
                    Log.d("FaceVerificationActivity", "신분증 사진이 성공적으로 저장되었습니다.");
                } else {
                    runOnUiThread(() -> Toast.makeText(FaceVerificationActivity.this, "신분증 얼굴 사진 저장에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show());
                    Log.e("FaceVerificationActivity", "신분증 사진 저장에 실패했습니다.");
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("FaceVerificationActivity", "신분증 얼굴 캡처 실패", exception);
            }
        });
    }

    private void takeLivePhotoAndCompare() {
        if (imageCapture == null) return;

        File livePhotoFile = new File(getExternalFilesDir(null), "live_photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(livePhotoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Bitmap originalBitmap = BitmapFactory.decodeFile(livePhotoFile.getAbsolutePath());
                if (originalBitmap != null) {
                    // Rotate live image by 270 degrees
                    Bitmap rotatedBitmap = rotateBitmap(originalBitmap, 270);
                    runOnUiThread(() -> liveFaceImageView.setImageBitmap(rotatedBitmap));
                    compareFacesUsingMLKit(rotatedBitmap);
                } else {
                    runOnUiThread(() -> Toast.makeText(FaceVerificationActivity.this, "실시간 얼굴 사진 저장에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("FaceVerificationActivity", "실시간 얼굴 캡처 실패", exception);
            }
        });
    }


    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void compareFacesUsingMLKit(Bitmap liveFaceBitmap) {
        if (idCardFaceBitmap == null) {
            runOnUiThread(() -> Toast.makeText(this, "신분증 얼굴이 없습니다. 신분증을 다시 스캔해주세요.", Toast.LENGTH_SHORT).show());
            return;
        }

        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));

        // Detect face from ID card
        detectFaceUsingMLKit(idCardFaceBitmap, new OnFaceDetectedListener() {
            @Override
            public void onFaceDetected(Bitmap idCardFace) {
                // Detect face from live photo
                detectFaceUsingMLKit(liveFaceBitmap, new OnFaceDetectedListener() {
                    @Override
                    public void onFaceDetected(Bitmap liveFace) {

                        float similarity = calculateCosineSimilarity(extractFaceEmbedding(idCardFaceBitmap), extractFaceEmbedding(liveFaceBitmap));

                        isFaceMatched = similarity >= FACE_MATCH_THRESHOLD;

                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);

                            // Update the similarity TextView
                            TextView similarityTextView = findViewById(R.id.similarityTextView);
                            similarityTextView.setText(String.format("얼굴 비교 결과: %.2f%% 일치", similarity * 100));

                            if (isFaceMatched) {
                                Toast.makeText(FaceVerificationActivity.this, "3차 얼굴 인증이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                                // 저장된 모든 이미지를 삭제합니다.
                                File directory = getExternalFilesDir(null);
                                if (directory != null && directory.isDirectory()) {
                                    for (File file : directory.listFiles()) {
                                        if (file.isFile()) {
                                            file.delete();
                                        }
                                    }
                                }
                                new Handler().postDelayed(() -> {
                                    Intent intent = new Intent(FaceVerificationActivity.this, OrderSummaryActivity.class);
                                    startActivity(intent);
                                    finish();
                                }, 2000); // 2초 지연
                            }else {
                                Toast.makeText(FaceVerificationActivity.this, "얼굴 인증 실패: 얼굴이 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                                // 저장된 모든 이미지를 삭제합니다.
                                File directory = getExternalFilesDir(null);
                                if (directory != null && directory.isDirectory()) {
                                    for (File file : directory.listFiles()) {
                                        if (file.isFile()) {
                                            file.delete();
                                        }
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onFaceDetectionFailed() {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(FaceVerificationActivity.this, "실시간 얼굴 감지에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onFaceDetectionFailed() {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(FaceVerificationActivity.this, "신분증 얼굴 감지에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void detectFaceUsingMLKit(Bitmap bitmap, OnFaceDetectedListener listener) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // Configure options for ML Kit face detection
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() > 0) {
                        // Assume we're working with the first detected face
                        Face face = faces.get(0);
                        Bitmap croppedFace = Bitmap.createBitmap(
                                bitmap,
                                (int) face.getBoundingBox().left,
                                (int) face.getBoundingBox().top,
                                (int) face.getBoundingBox().width(),
                                (int) face.getBoundingBox().height()
                        );
                        listener.onFaceDetected(croppedFace);
                    } else {
                        listener.onFaceDetectionFailed();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FaceVerificationActivity", "ML Kit face detection failed", e);
                    listener.onFaceDetectionFailed();
                });
    }

    // Interface to handle face detection callbacks
    interface OnFaceDetectedListener {
        void onFaceDetected(Bitmap faceBitmap);
        void onFaceDetectionFailed();
    }





    private void loadMobileFaceNetModel() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("mobilefacenet.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        tflite = new Interpreter(tfliteModel);
    }


    private float[] extractFaceEmbedding(Bitmap bitmap) {
        // Bitmap을 112x112 크기로 리사이즈
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 112, 112, true);

        // Bitmap을 ByteBuffer로 변환
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[112 * 112];
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < 112; ++i) {
            for (int j = 0; j < 112; ++j) {
                final int val = intValues[pixel++];
                inputBuffer.putFloat(((val >> 16) & 0xFF) / 127.5f - 1.0f);
                inputBuffer.putFloat(((val >> 8) & 0xFF) / 127.5f - 1.0f);
                inputBuffer.putFloat((val & 0xFF) / 127.5f - 1.0f);
            }
        }

        // TFLite 모델을 사용하여 특징 벡터 추출
        float[][] embedding = new float[1][192];  // 모델의 출력 크기에 맞게 수정
        tflite.run(inputBuffer, embedding);

        return embedding[0];
    }




    private float calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        cameraExecutor.shutdown();
    }
}
