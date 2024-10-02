/*package com.example.myapplication2222;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication2222.Kartrider;
import com.example.myapplication2222.MainActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.bootpay.android.Bootpay; // 부트페이 SDK import
import com.bootpay.android.model.Payment; // 필요한 모델 import
import com.bootpay.android.listener.OnBootpayCallback;

import java.util.ArrayList;
import java.util.List;

public class PaymentSuccessActivity extends AppCompatActivity {

    private static final int DELAY_MILLIS = 2000; // 2초 지연
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_IS_ADULT = "is_adult";

    private FirebaseFirestore firestore;
    private CollectionReference cartCollectionRef;
    private CollectionReference inventoryCollectionRef;
    private boolean isProcessing = true; // 처리 중인지 여부

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_success);

        firestore = FirebaseFirestore.getInstance();
        cartCollectionRef = firestore.collection("kartrider");
        inventoryCollectionRef = firestore.collection("inventory");

        // 장바구니의 총 금액 계산 후 결제 시작
        calculateTotalAndInitiatePayment();
        // 성인 인증 상태 초기화
        resetAdultStatus();
    }

    private void calculateTotalAndInitiatePayment() {
        cartCollectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                double totalAmount = 0.0;
                List<Task<Void>> updateTasks = new ArrayList<>();
                WriteBatch batch = firestore.batch();

                // 장바구니의 총 금액 계산
                for (DocumentSnapshot document : task.getResult().getDocuments()) {
                    Kartrider cartProduct = document.toObject(Kartrider.class);
                    if (cartProduct != null) {
                        totalAmount += cartProduct.getPrice() * cartProduct.getQuantity(); // 가격 * 수량
                    }
                }

                // 결제 요청
                initiatePayment(totalAmount, batch);
            } else {
                Log.e("PaymentSuccess", "Failed to retrieve cart data", task.getException());
            }
        });
    }

    private void initiatePayment(double totalAmount, WriteBatch batch) {
        Bootpay.init("66f67f77a3175898bd6e4bfe") // 부트페이 애플리케이션 ID
                .setContext(this)
                .setOrderName("장바구니 결제") // 주문 이름
                .setPrice(totalAmount) // 결제 금액
                .setPg("test") // 결제 PG 설정
                .setOnSuccessListener(new OnBootpayCallback() {
                    @Override
                    public void onSuccess(String response) {
                        Log.d("Payment", "Payment Success: " + response);
                        // 재고 업데이트 및 장바구니 초기화
                        updateInventoryAndClearCart(batch);
                    }
                })
                .setOnErrorListener(error -> {
                    Log.e("Payment", "Payment Error: " + error);
                })
                .setOnCancelListener(response -> {
                    Log.d("Payment", "Payment Cancelled: " + response);
                })
                .requestPayment();
    }

    private void updateInventoryAndClearCart(WriteBatch batch) {
        cartCollectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("PaymentSuccess", "Retrieved cart data successfully.");

                for (DocumentSnapshot document : task.getResult().getDocuments()) {
                    Kartrider cartProduct = document.toObject(Kartrider.class);
                    if (cartProduct != null && cartProduct.getId() != null) {
                        String productId = cartProduct.getId();
                        int quantityInCart = cartProduct.getQuantity();

                        Log.d("PaymentSuccess", "Processing product ID: " + productId + " with quantity: " + quantityInCart);

                        DocumentReference inventoryDocRef = inventoryCollectionRef.document(productId);
                        inventoryDocRef.get().addOnCompleteListener(inventoryTask -> {
                            if (inventoryTask.isSuccessful()) {
                                DocumentSnapshot inventoryDoc = inventoryTask.getResult();
                                if (inventoryDoc.exists()) {
                                    Long currentStockLong = inventoryDoc.getLong("stock");
                                    if (currentStockLong != null) {
                                        int currentStock = currentStockLong.intValue();
                                        if (currentStock >= quantityInCart) {
                                            long updatedStock = currentStock - quantityInCart;
                                            batch.update(inventoryDocRef, "stock", updatedStock);
                                            Log.d("PaymentSuccess", "Stock updated for product ID: " + productId);
                                        } else {
                                            Log.w("PaymentSuccess", "Insufficient stock for product ID: " + productId);
                                        }
                                    } else {
                                        Log.w("PaymentSuccess", "Current stock is null for product ID: " + productId);
                                    }
                                } else {
                                    Log.w("PaymentSuccess", "Inventory document does not exist for product ID: " + productId);
                                }
                            } else {
                                Log.e("PaymentSuccess", "Failed to get inventory document for product ID: " + productId, inventoryTask.getException());
                            }
                        });

                        // Add cart item deletion to batch
                        batch.delete(cartCollectionRef.document(document.getId()));
                        Log.d("PaymentSuccess", "Cart item scheduled for deletion with document ID: " + document.getId());
                    } else {
                        Log.w("PaymentSuccess", "Cart product is null or has an invalid ID.");
                    }
                }

                // Commit the batch write after all updates are scheduled
                batch.commit().addOnCompleteListener(batchCommitTask -> {
                    if (batchCommitTask.isSuccessful()) {
                        Log.d("PaymentSuccess", "Batch commit successful.");
                        isProcessing = false; // Processing is complete
                        // Delay before starting MainActivity
                        new Handler().postDelayed(() -> {
                            Intent intent = new Intent(PaymentSuccessActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        }, DELAY_MILLIS);
                    } else {
                        Log.e("PaymentSuccess", "Failed to commit batch", batchCommitTask.getException());
                        isProcessing = false; // Processing is complete even on failure
                    }
                });
            } else {
                Log.e("PaymentSuccess", "Failed to retrieve cart data", task.getException());
                isProcessing = false; // Processing is complete even on failure
            }
        });
    }

    private void resetAdultStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_ADULT, false); // Reset adult status
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isProcessing) {
            // Additional logic to handle activity pausing during processing
            Log.d("PaymentSuccess", "Activity paused during processing.");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isProcessing) {
            // Additional logic to handle activity stopping during processing
            Log.d("PaymentSuccess", "Activity stopped during processing.");
        }
    }
}
*/