package com.example.myapplication2222;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import kr.co.bootpay.android.Bootpay;
import kr.co.bootpay.android.events.BootpayEventListener;
import kr.co.bootpay.android.models.BootItem;
import kr.co.bootpay.android.models.BootUser;
import kr.co.bootpay.android.models.Payload;

public class PaymentActivity extends AppCompatActivity {
    private static final String TAG = "PaymentActivity";
    private FirebaseFirestore firestore;
    private CollectionReference cartCollectionRef;
    private CollectionReference inventoryCollectionRef;
    private boolean isPaymentSuccessful = false;
    private List<Kartrider> cartItems;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);
        firestore = FirebaseFirestore.getInstance();
        cartCollectionRef = firestore.collection("kartrider");
        inventoryCollectionRef = firestore.collection("inventory");
        cartItems = new ArrayList<>();

        calculateTotalAndInitiatePayment();
    }

    private void calculateTotalAndInitiatePayment() {
        cartCollectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                double totalAmount = 0.0;

                for (DocumentSnapshot document : task.getResult().getDocuments()) {
                    Kartrider cartProduct = document.toObject(Kartrider.class);
                    if (cartProduct != null) {
                        cartProduct.setId(document.getId());
                        totalAmount += cartProduct.getPrice() * cartProduct.getQuantity();
                        cartItems.add(cartProduct);
                    } else {
                        Log.e(TAG, "Failed to convert document to Kartrider: " + document.getId());
                    }
                }

                if (cartItems.isEmpty()) {
                    Log.e(TAG, "장바구니가 비어 있습니다. 결제를 진행할 수 없습니다.");
                    finishActivity();
                    return;
                }

                initiatePayment(totalAmount, cartItems);
            } else {
                Log.e(TAG, "장바구니 데이터를 가져오는 데 실패했습니다.", task.getException());
                finishActivity();
            }
        });
    }

    private void initiatePayment(double totalAmount, List<Kartrider> cartItems) {
        Log.d(TAG, "Initiating payment with total amount: " + totalAmount);
        BootUser user = new BootUser().setPhone("010-9176-5674");
        List<BootItem> items = new ArrayList<>();

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (Kartrider cartItem : cartItems) {
            tasks.add(inventoryCollectionRef.document(cartItem.getId()).get());
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener(taskList -> {
            for (int i = 0; i < tasks.size(); i++) {
                Task<DocumentSnapshot> task = tasks.get(i);
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        BootItem bootpayItem = new BootItem()
                                .setName(document.getString("name"))
                                .setId(cartItems.get(i).getId())
                                .setQty(cartItems.get(i).getQuantity())
                                .setPrice(document.getDouble("price"));
                        items.add(bootpayItem);
                    } else {
                        Log.e(TAG, "상품 정보가 존재하지 않습니다: " + cartItems.get(i).getId());
                        finishActivity();
                        return;
                    }
                } else {
                    Log.e(TAG, "상품 정보를 가져오는 데 실패했습니다.", task.getException());
                    finishActivity();
                    return;
                }
            }

            sendPaymentRequest(totalAmount, user, items);
        });
    }

    private void sendPaymentRequest(double totalAmount, BootUser user, List<BootItem> items) {
        if (totalAmount <= 0) {
            Log.e(TAG, "Invalid total amount: " + totalAmount);
            finishActivity();
            return;
        }

        if (items.isEmpty()) {
            Log.e(TAG, "No items in the payment request.");
            finishActivity();
            return;
        }

        Payload payload = new Payload();
        payload.setApplicationId("66f67f77a3175898bd6e4bfe")
                .setOrderName("장바구니 결제")
                .setOrderId("order_" + System.currentTimeMillis())
                .setPrice(totalAmount)
                .setUser(user)
                .setItems(items);

        Bootpay.init(getSupportFragmentManager(), getApplicationContext())
                .setPayload(payload)
                .setEventListener(new BootpayEventListener() {
                    @Override
                    public void onCancel(String data) {
                        Log.d(TAG, "Payment Cancelled: " + data);
                        finishActivity();
                    }

                    @Override
                    public void onError(String data) {
                        Log.e(TAG, "Payment Error: " + data);
                        finishActivity();
                    }

                    @Override
                    public void onClose() {
                        Log.d(TAG, "Payment window closed");
                        if (!isPaymentSuccessful) {
                            finishActivity();
                        }
                    }

                    @Override
                    public void onIssued(String data) {
                        Log.d(TAG, "Payment Issued: " + data);
                    }

                    @Override
                    public boolean onConfirm(String data) {
                        Log.d(TAG, "Payment Confirm: " + data);
                        return true;
                    }

                    @Override
                    public void onDone(String data) {
                        Log.d(TAG, "Payment Done: " + data);
                        isPaymentSuccessful = true;

                        Intent intent = new Intent(PaymentActivity.this, PaymentSuccessActivity.class);
                        intent.putExtra("paymentData", data);
                        intent.putParcelableArrayListExtra("cartItems", new ArrayList<>(cartItems));
                        startActivity(intent);
                        finish();
                    }
                }).requestPayment();
    }

    private void finishActivity() {
        Intent intent = new Intent(PaymentActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}