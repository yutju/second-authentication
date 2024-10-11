package com.example.myapplication2222;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.ArrayList;

public class PaymentSuccessActivity extends AppCompatActivity {
    private static final String TAG = "PaymentSuccessActivity";
    private FirebaseFirestore firestore;
    private CollectionReference cartCollectionRef;
    private CollectionReference inventoryCollectionRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_success);

        firestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        cartCollectionRef = firestore.collection("kartrider");
        inventoryCollectionRef = firestore.collection("inventory");

        String paymentData = getIntent().getStringExtra("paymentData");
        if (paymentData != null) {
            processPaymentSuccess(paymentData);
        } else {
            Log.e(TAG, "Payment data is null");
            showErrorMessage("결제 데이터가 없습니다.");
        }
    }

    private void processPaymentSuccess(String paymentData) {
        try {
            JSONObject jsonObject = new JSONObject(paymentData);
            JSONObject dataObject = jsonObject.getJSONObject("data");
            String status = dataObject.getString("status");

            if ("1".equals(status)) {
                updateInventoryAndClearCart();
            } else {
                String errorMessage = dataObject.optString("error_message", "결제 실패");
                showErrorMessage(errorMessage);
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON 파싱 오류", e);
            showErrorMessage("결제 데이터 파싱 오류");
        }
    }

    private void updateInventoryAndClearCart() {
        cartCollectionRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            List<Kartrider> cartItems = new ArrayList<>();
            for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                Kartrider cartItem = document.toObject(Kartrider.class);
                if (cartItem != null) {
                    cartItem.setId(document.getId()); // 문서 ID 수동 설정
                    cartItems.add(cartItem);
                }
            }
            updateInventory(cartItems);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "장바구니 데이터 가져오기 실패", e);
            showErrorMessage("장바구니 데이터를 가져오는데 실패했습니다.");
        });
    }

    private void updateInventory(List<Kartrider> cartItems) {
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            for (Kartrider cartItem : cartItems) {
                String itemId = cartItem.getId();
                if (itemId == null || itemId.isEmpty()) {
                    Log.e(TAG, "상품 ID가 null 또는 빈 문자열입니다: " + cartItem.toString());
                    continue; // 잘못된 ID는 건너뜁니다.
                }

                DocumentReference inventoryDocRef = inventoryCollectionRef.document(itemId);
                DocumentReference cartItemDocRef = cartCollectionRef.document(itemId);

                Long currentStock = transaction.get(inventoryDocRef).getLong("stock");
                if (currentStock == null) {
                    Log.e(TAG, "재고 정보가 null입니다: " + itemId);
                    throw new IllegalStateException("재고 정보가 없습니다: " + itemId);
                }

                long newStock = currentStock - cartItem.getQuantity();
                if (newStock < 0) {
                    Log.e(TAG, "재고가 부족합니다: " + itemId);
                    throw new IllegalStateException("재고가 부족합니다: " + itemId);
                }

                transaction.update(inventoryDocRef, "stock", newStock);
                transaction.delete(cartItemDocRef);
            }
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "재고 업데이트 및 장바구니 초기화 성공");
            clearUserSession(); // 결제 후 사용자 세션 초기화
            showSuccessMessage();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "재고 업데이트 또는 장바구니 초기화 실패", e);
            showErrorMessage("재고 업데이트 또는 장바구니 초기화에 실패했습니다.");
        });
    }

    private void clearUserSession() {
        if (mAuth.getCurrentUser() != null) {
            mAuth.signOut(); // 사용자 로그아웃
        }

        // 성인 인증 상태 초기화
        getSharedPreferences("app_preferences", MODE_PRIVATE)
                .edit()
                .putBoolean("isAdult", false)
                .apply();
        Log.d(TAG, "성인인증 정보 및 사용자 세션 초기화 완료");
    }

    private void showSuccessMessage() {
        runOnUiThread(() -> {
            ImageView successImage = findViewById(R.id.payment_success_image);
            successImage.setImageResource(R.drawable.payment_success_image);

            TextView successMessage = findViewById(R.id.payment_success_message);
            successMessage.setText("결제가 성공적으로 완료되었습니다.");

            // 3초 후 메인 액티비티로 이동
            new Handler().postDelayed(this::finishActivity, 3000);
        });
    }

    private void showErrorMessage(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finishActivity();
        });
    }

    private void finishActivity() {
        Intent intent = new Intent(PaymentSuccessActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishActivity();
    }
}
