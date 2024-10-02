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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

class MedianFilter {
    private LinkedList<Double> window = new LinkedList<>();
    private int size;

    public MedianFilter(int size) {
        this.size = size;
    }

    public double addSample(double sample) {
        window.addLast(sample);
        if (window.size() > size) {
            window.removeFirst();
        }
        LinkedList<Double> sortedWindow = new LinkedList<>(window);
        Collections.sort(sortedWindow);
        return sortedWindow.get(sortedWindow.size() / 2);
    }
}

class SimpleMovingAverage {
    private LinkedList<Double> window = new LinkedList<>();
    private int period;
    private double sum;

    public SimpleMovingAverage(int period) {
        this.period = period;
    }

    public double addSample(double sample) {
        sum += sample;
        window.addLast(sample);
        if (window.size() > period) {
            sum -= window.removeFirst();
        }
        return sum / window.size();
    }
}

class KalmanFilter {
    private double q; // 프로세스 노이즈 공분산
    private double r; // 측정 노이즈 공분산
    private double x; // 상태 (위치)
    private double p; // 추정 오차 공분산
    private double k; // 칼만 이득

    public KalmanFilter(double q, double r) {
        this.q = q;
        this.r = r;
        this.x = 0; // 초기 상태
        this.p = 1; // 초기 오차 공분산
    }

    public double update(double measurement) {
        // 예측 단계
        p += q;

        // 업데이트 단계
        k = p / (p + r);
        x += k * (measurement - x);
        p *= (1 - k);
        return x;
    }
}

class Particle {
    double x, y, weight;

    public Particle(double x, double y, double weight) {
        this.x = x;
        this.y = y;
        this.weight = weight;
    }
}

class ParticleFilter {
    private List<Particle> particles;
    private int numParticles;
    private Random random;
    private double width, height;

    public ParticleFilter(int numParticles, double width, double height) {
        this.numParticles = numParticles;
        this.width = width;
        this.height = height;
        this.random = new Random();
        initializeParticles();
    }

    private void initializeParticles() {
        particles = new ArrayList<>(numParticles);
        for (int i = 0; i < numParticles; i++) {
            particles.add(new Particle(random.nextDouble() * width, random.nextDouble() * height, 1.0 / numParticles));
        }
    }

    public double[] update(Map<Integer, Double> beaconDistances) {
        // 파티클 이동 (간단한 랜덤 이동)
        for (Particle p : particles) {
            p.x += random.nextGaussian() * 0.1;
            p.y += random.nextGaussian() * 0.1;
            p.x = Math.max(0, Math.min(p.x, width));
            p.y = Math.max(0, Math.min(p.y, height));
        }

        // 가중치 업데이트
        double totalWeight = 0;
        for (Particle p : particles) {
            double likelihood = 1.0;
            for (Map.Entry<Integer, Double> entry : beaconDistances.entrySet()) {
                int beaconId = entry.getKey();
                double measuredDistance = entry.getValue();
                double[] beaconPos = getBeaconPosition(beaconId);
                double calculatedDistance = Math.sqrt(Math.pow(p.x - beaconPos[0], 2) + Math.pow(p.y - beaconPos[1], 2));
                likelihood *= Math.exp(-Math.abs(measuredDistance - calculatedDistance));
            }
            p.weight *= likelihood;
            totalWeight += p.weight;
        }

        // 정규화
        for (Particle p : particles) {
            p.weight /= totalWeight;
        }

        // 리샘플링
        List<Particle> newParticles = new ArrayList<>(numParticles);
        for (int i = 0; i < numParticles; i++) {
            double r = random.nextDouble();
            double sum = 0;
            for (Particle p : particles) {
                sum += p.weight;
                if (sum >= r) {
                    newParticles.add(new Particle(p.x, p.y, 1.0 / numParticles));
                    break;
                }
            }
        }
        particles = newParticles;

        // 평균 위치 계산
        double sumX = 0, sumY = 0;
        for (Particle p : particles) {
            sumX += p.x;
            sumY += p.y;
        }
        return new double[]{sumX / numParticles, sumY / numParticles};
    }

    private double[] getBeaconPosition(int beaconId) {
        switch (beaconId) {
            case 1: return new double[]{0, MapActivity.MART_HEIGHT};
            case 2: return new double[]{MapActivity.MART_WIDTH / 2, 0};
            case 3: return new double[]{MapActivity.MART_WIDTH, MapActivity.MART_HEIGHT};
            default: return new double[]{0, 0};
        }
    }
}

public class MapActivity extends AppCompatActivity implements BeaconConsumer {

    private static final String TAG = "Beacontest";
    private BeaconManager beaconManager;
    private List<Beacon> beaconList = new ArrayList<>();
    private CustomView customView;
    private Map<String, SimpleMovingAverage> rssiAverages = new HashMap<>();
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int TARGET_MAJOR_VALUE = 10011;
    private static final double A = -70; // RSSI 상수
    private static final double N = 2.0; // 거리 감쇠 지수
    public static final double MART_WIDTH = 3.0;
    public static final double MART_HEIGHT = 3.0;
    private static final double MAX_DISTANCE = 3.0; // 최대 허용 거리
    private KalmanFilter kalmanFilterX;
    private KalmanFilter kalmanFilterY;
    private Button runButton;
    private Map<String, MedianFilter> rssiMedianFilters = new HashMap<>();
    private Map<String, SimpleMovingAverage> rssiMovingAverages = new HashMap<>();
    private static final int MEDIAN_FILTER_SIZE = 5;
    private static final int MOVING_AVERAGE_PERIOD = 10;
    private static final double POSITION_UPDATE_THRESHOLD = 0.1; // meters
    private double[] lastPosition = null;
    private ParticleFilter particleFilter;
    private Map<String, Double> rssiCalibration = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        // 칼만 필터 초기화 - 파라미터 최적화
        kalmanFilterX = new KalmanFilter(0.001, 0.1);
        kalmanFilterY = new KalmanFilter(0.001, 0.1);
        particleFilter = new ParticleFilter(1000, MART_WIDTH, MART_HEIGHT);
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

        // RSSI 보정값 초기화
        rssiCalibration.put("beacon1", -2.0);
        rssiCalibration.put("beacon2", 1.5);
        rssiCalibration.put("beacon3", 0.5);
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

    private double calculateDistance(double rssi, String beaconId) {
        // RSSI 보정 적용
        double calibratedRssi = rssi + rssiCalibration.getOrDefault(beaconId, 0.0);
        return Math.min(Math.pow(10, (A - calibratedRssi) / (10 * N)), MAX_DISTANCE);
    }
    private double calculateDistance(double[] p1, double[] p2) {
        return Math.sqrt(Math.pow(p1[0] - p2[0], 2) + Math.pow(p1[1] - p2[1], 2));
    }

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            StringBuilder sb = new StringBuilder();
            Map<Integer, Double> beaconDistances = new HashMap<>();

            for (Beacon beacon : beaconList) {
                int major = beacon.getId2().toInt();
                int minor = beacon.getId3().toInt();
                String address = beacon.getBluetoothAddress();

                if (major == TARGET_MAJOR_VALUE && (minor == 1 || minor == 2 || minor == 3)) {
                    rssiMedianFilters.putIfAbsent(address, new MedianFilter(MEDIAN_FILTER_SIZE));
                    rssiMovingAverages.putIfAbsent(address, new SimpleMovingAverage(MOVING_AVERAGE_PERIOD));

                    double filteredRssi = rssiMedianFilters.get(address).addSample(beacon.getRssi());
                    double smoothedRssi = rssiMovingAverages.get(address).addSample(filteredRssi);
                    double distance = calculateDistance(smoothedRssi, "beacon" + minor);

                    beaconDistances.put(minor, distance);

                    sb.append("비콘 ").append(minor).append(": ")
                            .append(String.format("%.2f", distance)).append("m\n");

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

            if (beaconDistances.size() == 3) {
                double[] userPosition = calculateUserPosition(beaconDistances);
                if (userPosition != null) {
                    if (lastPosition == null || calculateDistance(lastPosition, userPosition) > POSITION_UPDATE_THRESHOLD) {
                        lastPosition = userPosition;
                        float userXScreen = (float) (userPosition[0] / MART_WIDTH * customView.getWidth());
                        float userYScreen = (float) ((MART_HEIGHT - userPosition[1]) / MART_HEIGHT * customView.getHeight());

                        userXScreen = Math.max(0, Math.min(userXScreen, customView.getWidth()));
                        userYScreen = Math.max(0, Math.min(userYScreen, customView.getHeight()));

                        customView.setUserPosition(userXScreen, userYScreen);

                        sb.append("사용자 위치: (")
                                .append(String.format("%.2f", userPosition[0])).append(", ")
                                .append(String.format("%.2f", userPosition[1])).append(")\n");
                    }
                }
            }

            TextView textView = findViewById(R.id.TextView);
            if (textView != null) {
                textView.setText(sb.toString());
            }

            customView.invalidate();
            sendEmptyMessageDelayed(0, 200);
        }
    };

    // 사용자 위치 계산
    private double[] calculateUserPosition(Map<Integer, Double> distances) {
        double[][] beacons = {{0, MART_HEIGHT}, {MART_WIDTH / 2, 0}, {MART_WIDTH, MART_HEIGHT}};

        // 파티클 필터를 사용한 위치 추정
        double[] particlePosition = particleFilter.update(distances);

        // 삼변측량법을 사용한 위치 추정
        double[] trilaterationPosition = trilaterate(distances);

        // 칼만 필터를 사용하여 두 추정치를 결합
        double kalmanX = kalmanFilterX.update(trilaterationPosition[0]);
        double kalmanY = kalmanFilterY.update(trilaterationPosition[1]);

        // 최종 위치를 마트 경계 내로 제한
        kalmanX = Math.max(0, Math.min(kalmanX, MART_WIDTH));
        kalmanY = Math.max(0, Math.min(kalmanY, MART_HEIGHT));

        // 거리 기반 가중치 적용
        if (distances.size() == 3) {
            double totalX = 0;
            double totalY = 0;
            double totalWeight = 0;

            for (Map.Entry<Integer, Double> entry : distances.entrySet()) {
                int minor = entry.getKey();
                double distance = entry.getValue();
                double[] beaconPos = beacons[minor - 1];

                if (distance <= 0.5) {
                    // 비콘 쪽으로 가중치를 주기
                    totalX += beaconPos[0] / Math.pow(distance, 2); // 거리에 대한 가중치 (역제곱)
                    totalY += beaconPos[1] / Math.pow(distance, 2);
                    totalWeight += 1 / Math.pow(distance, 2);
                } else {
                    // 중앙으로 가중치를 조금 주기
                    totalX += MART_WIDTH / 2 * 0.1; // 중앙 가중치의 10%
                    totalY += MART_HEIGHT / 2 * 0.1; // 중앙 가중치의 10%
                    totalWeight += 0.1; // 중앙 가중치의 10%
                }
            }

            if (totalWeight > 0) {
                kalmanX = totalX / totalWeight;
                kalmanY = totalY / totalWeight;
            }
        }

        return new double[]{kalmanX, kalmanY};
    }



    private double[] trilaterate(Map<Integer, Double> distances) {
        double[][] beacons = {{0, MART_HEIGHT}, {MART_WIDTH / 2, 0}, {MART_WIDTH, MART_HEIGHT}};

        // 삼변측량 계산
        double[] centroid = {0, 0};
        double totalWeight = 0;

        for (int i = 0; i < 3; i++) {
            // 각 비콘의 거리에 대해 가중치를 설정
            double distance = distances.get(i + 1);
            if (distance < MAX_DISTANCE) {
                double weight = 1.0 / Math.pow(distance, 2); // 거리에 대한 가중치 (역제곱)

                centroid[0] += beacons[i][0] * weight;
                centroid[1] += beacons[i][1] * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight > 0) {
            centroid[0] /= totalWeight;
            centroid[1] /= totalWeight;
        } else {
            // 교점이 없는 경우 비콘 위치의 평균을 사용
            centroid[0] = MART_WIDTH / 2; // 중앙 X 좌표
            centroid[1] = MART_HEIGHT / 2; // 중앙 Y 좌표
        }

        return centroid;
    }


    private double[] calculateIntersection(double x1, double y1, double r1, double x2, double y2, double r2) {
        double d = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));

        if (d > r1 + r2 || d < Math.abs(r1 - r2)) {
            return null; // 교점 없음
        }

        double a = (r1 * r1 - r2 * r2 + d * d) / (2 * d);
        double h = Math.sqrt(r1 * r1 - a * a);

        double x3 = x1 + a * (x2 - x1) / d;
        double y3 = y1 + a * (y2 - y1) / d;

        double x4 = x3 + h * (y2 - y1) / d;
        double y4 = y3 - h * (x2 - x1) / d;

        return new double[]{x4, y4};
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

    @Override
    protected void onResume() {
        super.onResume();
        if (!beaconManager.isBound(this)) beaconManager.bind(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (beaconManager.isBound(this)) beaconManager.unbind(this);
    }

}