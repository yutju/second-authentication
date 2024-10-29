package com.example.myapplication2222;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private ProgressBar progressBar;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        EditText editTextEmail = findViewById(R.id.editTextEmail);
        EditText editTextPassword = findViewById(R.id.editTextPassword);
        EditText editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        EditText editTextName = findViewById(R.id.editTextName);
        EditText editTextSSN = findViewById(R.id.editTextSSN);
        EditText editTextIssueDate = findViewById(R.id.editTextIssueDate);
        Button signupButton = findViewById(R.id.signup_button);
        progressBar = findViewById(R.id.progressBar);

        // 주민등록번호 입력 형식 처리
        editTextSSN.addTextChangedListener(new TextWatcher() {
            private boolean isEditing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isEditing) return;

                isEditing = true;
                String original = s.toString();
                String clean = original.replaceAll("[^\\d]", "");

                StringBuilder formatted = new StringBuilder();
                if (clean.length() > 6) {
                    formatted.append(clean.substring(0, 6)).append("-");
                    if (clean.length() > 6) {
                        formatted.append(clean.substring(6, Math.min(clean.length(), 13)));
                    }
                } else {
                    formatted.append(clean);
                }

                editTextSSN.setText(formatted);
                editTextSSN.setSelection(formatted.length());
                isEditing = false;
            }
        });

        // 발급일자 입력 형식 처리
        editTextIssueDate.addTextChangedListener(new TextWatcher() {
            private boolean isEditing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isEditing) return;

                isEditing = true;
                String original = s.toString();
                String clean = original.replaceAll("[^\\d]", "");
                StringBuilder formatted = new StringBuilder();

                if (clean.length() > 4) {
                    formatted.append(clean.substring(0, 4)).append(".");
                    if (clean.length() > 6) {
                        formatted.append(clean.substring(4, 6)).append(".");
                        if (clean.length() > 8) {
                            formatted.append(clean.substring(6, Math.min(clean.length(), 8)));
                        } else {
                            formatted.append(clean.substring(6));
                        }
                    } else {
                        formatted.append(clean.substring(4));
                    }
                } else {
                    formatted.append(clean);
                }

                editTextIssueDate.setText(formatted);
                editTextIssueDate.setSelection(formatted.length());
                isEditing = false;
            }
        });

        // 회원가입 버튼 클릭 리스너 설정
        signupButton.setOnClickListener(v -> createUser(editTextEmail, editTextPassword, editTextConfirmPassword, editTextName, editTextSSN, editTextIssueDate));
    }

    private void createUser(EditText editTextEmail, EditText editTextPassword, EditText editTextConfirmPassword, EditText editTextName, EditText editTextSSN, EditText editTextIssueDate) {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();
        String name = editTextName.getText().toString().trim();
        String ssn = editTextSSN.getText().toString().trim();
        String issueDate = editTextIssueDate.getText().toString().trim();

        // 유효성 검사
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "유효한 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "비밀번호는 최소 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "비밀번호가 서로 일치하지 않습니다. 다시 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (name.isEmpty()) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ssn.isEmpty() || !ssn.matches("\\d{6}-\\d{7}")) {
            Toast.makeText(this, "유효한 주민등록번호를 입력해주세요. (형식: XXXXXX-XXXXXXX)", Toast.LENGTH_SHORT).show();
            return;
        }

        if (issueDate.isEmpty() || !issueDate.matches("\\d{4}\\.\\d{2}\\.\\d{2}")) {
            Toast.makeText(this, "발급일자는 유효한 형식이어야 합니다. (형식: YYYY.MM.DD)", Toast.LENGTH_SHORT).show();
            return;
        }

        // 로딩 중 상태 표시
        progressBar.setVisibility(View.VISIBLE);

        // Firebase 회원가입 처리
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE); // 로딩 상태 해제
                    if (task.isSuccessful()) {
                        // 회원가입 성공
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // 사용자 프로필 업데이트 (이름 설정)
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();

                            user.updateProfile(profileUpdates).addOnCompleteListener(profileTask -> {
                                if (profileTask.isSuccessful()) {
                                    // 이메일 인증 메일 보내기
                                    user.sendEmailVerification().addOnCompleteListener(verificationTask -> {
                                        if (verificationTask.isSuccessful()) {
                                            Log.d("SignupActivity", "인증 이메일 발송 성공: " + user.getEmail());
                                            Toast.makeText(this, "인증 이메일이 발송되었습니다. 메일을 확인해주세요.", Toast.LENGTH_LONG).show();
                                        } else {
                                            Log.e("SignupActivity", "인증 이메일 발송 실패: " + verificationTask.getException().getMessage());
                                            Toast.makeText(this, "인증 이메일 발송에 실패했습니다: " + verificationTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });

                                    // Firestore에 사용자 정보 저장
                                    saveUserToFirestore(user.getUid(), name, email, ssn, issueDate);

                                    // 로그인 화면으로 이동
                                    startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                                    finish();
                                } else {
                                    Log.e("SignupActivity", "프로필 업데이트 실패: " + profileTask.getException().getMessage());
                                    Toast.makeText(this, "프로필 업데이트에 실패했습니다: " + profileTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } else {
                        // 회원가입 실패
                        String errorMessage = "회원가입 실패: " + task.getException().getMessage();
                        Log.e("SignupActivity", errorMessage);
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToFirestore(String userId, String name, String email, String ssn, String issueDate) {
        // 사용자 정보를 Firestore에 저장하는 메서드
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("ssn", ssn);
        user.put("issueDate", issueDate); // 발급일자 추가

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> Log.d("SignupActivity", "사용자 정보 저장 성공"))
                .addOnFailureListener(e -> Log.e("SignupActivity", "사용자 정보 저장 실패: " + e.getMessage()));
    }
}
