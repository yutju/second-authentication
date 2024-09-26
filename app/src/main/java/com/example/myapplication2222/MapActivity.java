package com.example.myapplication2222;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ExponentialMovingAverage {
    private double alpha;
    private Double oldValue;

    public ExponentialMovingAverage(double alpha) {
        this.alpha = alpha;
    }

    public double average(double value) {
        if (oldValue == null) {
            oldValue = value;
            return value;
        }
        double newValue = oldValue + alpha * (value - oldValue);
        oldValue = newValue;
        return newValue;
    }

    public void reset() {
        oldValue = null;
    }
}

public class MapActivity extends AppCompatActivity implements BeaconConsumer {

    private static final String TAG = "Beacontest";
    private BeaconManager beaconManager;
    private List<Beacon> beaconList = new ArrayList<>();
    private CustomView customView;
    private Map<String, ExponentialMovingAverage> rssiAverages = new HashMap<>();
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int TARGET_MAJOR_VALUE = 10011;
    private static final double A = -70; // RSSI 상수
    private static final double N = 2.0; // 거리 감쇠 지수
    private static final double ALPHA = 0.2; // 지수평활법의 알파 값

    private static final double MART_WIDTH = 3.0;
    private static final double MART_HEIGHT = 3.0;
    private static final double MAX_DISTANCE = 3.0; // 최대 허용 거리

    private Button runButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        customView = findViewById(R.id.custom_view);

        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
            }
        }

        runButton = findViewById(R.id.button);
        runButton.setOnClickListener(v -> handler.sendEmptyMessage(0));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier((beacons, region) -> {
            if (beacons.size() > 0) {
                beaconList.clear();
                beaconList.addAll(beacons);
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting ranging", e);
        }
    }

    private double calculateDistance(double rssi) {
        return Math.min(Math.pow(10, (A - rssi) / (10 * N)), MAX_DISTANCE);
    }

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            StringBuilder sb = new StringBuilder();
            Map<Integer, Double> beaconDistances = new HashMap<>();
            int closestBeacon = -1;
            double minDistance = Double.MAX_VALUE;

            for (Beacon beacon : beaconList) {
                int major = beacon.getId2().toInt();
                int minor = beacon.getId3().toInt();
                String address = beacon.getBluetoothAddress();

                if (major == TARGET_MAJOR_VALUE && (minor == 1 || minor == 2 || minor == 3)) {
                    rssiAverages.putIfAbsent(address, new ExponentialMovingAverage(ALPHA));
                    double smoothedRssi = rssiAverages.get(address).average(beacon.getRssi());
                    double distance = calculateDistance(smoothedRssi);

                    beaconDistances.put(minor, distance);

                    sb.append("비콘 ").append(minor).append(": ")
                            .append(String.format("%.2f", distance)).append("m\n");

                    if (distance < minDistance) {
                        minDistance = distance;
                        closestBeacon = minor;
                    }

                    double beaconX, beaconY;
                    int color;

                    switch (minor) {
                        case 1:
                            beaconX = 0;
                            beaconY = MART_HEIGHT;
                            color = Color.RED;
                            break;
                        case 2:
                            beaconX = MART_WIDTH / 2;
                            beaconY = 0;
                            color = Color.YELLOW;
                            break;
                        case 3:
                            beaconX = MART_WIDTH;
                            beaconY = MART_HEIGHT;
                            color = Color.GREEN;
                            break;
                        default:
                            continue;
                    }

                    float beaconXScreen = (float) (beaconX / MART_WIDTH * customView.getWidth());
                    float beaconYScreen = (float) ((MART_HEIGHT - beaconY) / MART_HEIGHT * customView.getHeight());
                    float radius = (float) (distance / Math.max(MART_WIDTH, MART_HEIGHT) * Math.max(customView.getWidth(), customView.getHeight()));

                    customView.updateBeaconPosition(minor - 1, beaconXScreen, beaconYScreen, radius, color);
                }
            }

            if (beaconDistances.size() == 3 && closestBeacon != -1) {
                double[] userPosition = calculateUserPosition(beaconDistances, closestBeacon);
                if (userPosition != null) {
                    float userXScreen = (float) (userPosition[0] / MART_WIDTH * customView.getWidth());
                    float userYScreen = (float) ((MART_HEIGHT - userPosition[1]) / MART_HEIGHT * customView.getHeight());

                    // Limit user position to mart boundaries
                    userXScreen = Math.max(0, Math.min(userXScreen, customView.getWidth()));
                    userYScreen = Math.max(0, Math.min(userYScreen, customView.getHeight()));

                    customView.setUserPosition(userXScreen, userYScreen);

                    sb.append("사용자 위치: (")
                            .append(String.format("%.2f", userPosition[0])).append(", ")
                            .append(String.format("%.2f", userPosition[1])).append(")\n");
                }
            }

            TextView textView = findViewById(R.id.TextView);
            if (textView != null) {
                textView.setText(sb.toString());
            }

            customView.invalidate();
            sendEmptyMessageDelayed(0, 1000);
        }
    };

    private double[] calculateUserPosition(Map<Integer, Double> distances, int closestBeacon) {
        double[][] beacons = {{0, MART_HEIGHT}, {MART_WIDTH / 2, 0}, {MART_WIDTH, MART_HEIGHT}};
        double[] closestBeaconPos = beacons[closestBeacon - 1];
        double closestDistance = distances.get(closestBeacon);

        // Calculate the range of possible positions based on the closest beacon
        double minX = Math.max(0, closestBeaconPos[0] - closestDistance);
        double maxX = Math.min(MART_WIDTH, closestBeaconPos[0] + closestDistance);
        double minY = Math.max(0, closestBeaconPos[1] - closestDistance);
        double maxY = Math.min(MART_HEIGHT, closestBeaconPos[1] + closestDistance);

        // Use the other two beacons to refine the position
        for (int i = 1; i <= 3; i++) {
            if (i != closestBeacon) {
                double[] beaconPos = beacons[i - 1];
                double distance = distances.get(i);

                minX = Math.max(minX, beaconPos[0] - distance);
                maxX = Math.min(maxX, beaconPos[0] + distance);
                minY = Math.max(minY, beaconPos[1] - distance);
                maxY = Math.min(maxY, beaconPos[1] + distance);
            }
        }

        // Return the center of the possible position range
        return new double[]{(minX + maxX) / 2, (minY + maxY) / 2};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle("Functionality limited")
                        .setMessage("Since location access has not been granted, this app will not be able to discover beacons.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }
    }






    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Functionality limited")
                .setMessage("Since location access has not been granted, this app will not be able to discover beacons or use location services.")
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(dialog -> {})
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // handler.sendEmptyMessage(0); // 이제 버튼으로 탐지를 시작하므로, 이 부분은 삭제합니다.
    }

    @Override
    protected void onPause() {
        super.onPause();
        beaconManager.unbind(this);
    }

    // 비콘과 거리 정보를 담는 클래스
    private static class BeaconDistance {
        private double x;
        private double y;
        private double distance;

        BeaconDistance(double x, double y, double distance) {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getDistance() {
            return distance;
        }
    }
}