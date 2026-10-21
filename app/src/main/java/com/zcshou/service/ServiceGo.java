package com.zcshou.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.elvishew.xlog.XLog;
import com.zcshou.gogogo.MainActivity;
import com.zcshou.gogogo.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ServiceGo extends Service {
    // 定位相关变量
    public static final double DEFAULT_LAT = 35.3574634; // 默认位置: 山东理工职业学院操场(wgs84)
    public static final double DEFAULT_LNG = 116.5691804; // 默认位置: 山东理工职业学院操场(wgs84)
    public static final double DEFAULT_ALT = 55.0D;
    public static final float DEFAULT_BEA = 0.0F;
    private double mCurLat = DEFAULT_LAT;
    private double mCurLng = DEFAULT_LNG;
    private double mCurAlt = DEFAULT_ALT;
    private float mCurBea = DEFAULT_BEA;
    private double mSpeed = 1.2;        /* 默认的速度，单位 m/s */
    // 防检测/轨迹自然化相关
    private boolean mAntiDetect = true;          // 是否开启轨迹自然化（GPS噪声/速度波动）
    private final Random mRand = new Random();   // 用于生成随机噪声
    // 路线跟随相关
    private final List<double[]> mRoutePoints = new ArrayList<>();  // 每个元素为 {lng, lat}
    private int mRouteIndex = 0;                                     // 当前正在前往的路径点下标
    private boolean mRouteMode = false;                              // 是否开启路线跟随
    private boolean mRouteLoop = false;
    private boolean mPaused = false;                              // 是否循环走路线
    private static final double ROUTE_ARRIVE_THRESHOLD = 0.5D;      // 距离路径点多近算“到达”，单位 m
    private static final int HANDLER_MSG_ID = 0;
    private static final String SERVICE_GO_HANDLER_NAME = "ServiceGoLocation";
    private LocationManager mLocManager;
    private HandlerThread mLocHandlerThread;
    private Handler mLocHandler;
    private boolean isStop = false;
    // 通知栏消息
    private static final int SERVICE_GO_NOTE_ID = 1;
    private static final String SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOTE";
    private static final String SERVICE_GO_NOTE_CHANNEL_NAME = "SERVICE_GO_NOTE";
    private NoteActionReceiver mActReceiver;
    // 摇杆相关

    private final ServiceGoBinder mBinder = new ServiceGoBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        removeTestProviderNetwork();
        addTestProviderNetwork();

        removeTestProviderGPS();
        addTestProviderGPS();

        initGoLocation();

        initNotification();


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mCurLng = intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG);
        mCurLat = intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT);
        mCurAlt = intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT);


        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        isStop = true;
        mLocHandler.removeMessages(HANDLER_MSG_ID);
        mLocHandlerThread.quit();


        removeTestProviderNetwork();
        removeTestProviderGPS();

        unregisterReceiver(mActReceiver);
        stopForeground(STOP_FOREGROUND_REMOVE);

        super.onDestroy();
    }

    private void initNotification() {
        mActReceiver = new NoteActionReceiver();
        IntentFilter filter = new IntentFilter();
        registerReceiver(mActReceiver, filter);

        NotificationChannel mChannel = new NotificationChannel(SERVICE_GO_NOTE_CHANNEL_ID, SERVICE_GO_NOTE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(mChannel);
        }

        //准备intent
        Intent clickIntent = new Intent(this, MainActivity.class);
        PendingIntent clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
                .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.app_service_tips))
                .setContentIntent(clickPI)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(SERVICE_GO_NOTE_ID, notification);
    }

    private void initGoLocation() {
        // 创建 HandlerThread 实例，第一个参数是线程的名字
        mLocHandlerThread = new HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND);
        // 启动 HandlerThread 线程
        mLocHandlerThread.start();
        // Handler 对象与 HandlerThread 的 Looper 对象的绑定
        mLocHandler = new Handler(mLocHandlerThread.getLooper()) {
            // 这里的Handler对象可以看作是绑定在HandlerThread子线程中，所以handlerMessage里的操作是在子线程中运行的
            @Override
            public void handleMessage(@NonNull Message msg) {
                try {
                    Thread.sleep(100);

                    if (!isStop) {
                        // 若开启了路线跟随，则沿路径点移动位置
                        if (mRouteMode && !mPaused) {
                            stepAlongRoute();
                        }
                        setLocationNetwork();
                        setLocationGPS();

                        sendEmptyMessage(HANDLER_MSG_ID);
                    }
                } catch (InterruptedException e) {
                    XLog.e("SERVICEGO: ERROR - handleMessage");
                    Thread.currentThread().interrupt();
                }
            }
        };

        mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
    }

    /**
     * 设置路线跟随：沿给定路径点序列自动移动位置。
     * @param route 路径点序列，每个元素为 {lng, lat}，坐标系为 WGS84（与内部定位一致）
     * @param speed 移动速度，单位 m/s
     * @param loop  是否走完一圈后循环
     */
    public void setRoute(List<double[]> route, double speed, boolean loop) {
        mRoutePoints.clear();
        if (route != null) {
            mRoutePoints.addAll(route);
        }
        mRouteIndex = 0;
        mSpeed = speed;
        mRouteLoop = loop;
        // 若路线非空且当前还没有开始移动，则启动路线跟随；否则视为仅更新参数
        if (!mRoutePoints.isEmpty() && !isRouteRunning()) {
            mRouteMode = true;
        }
    }

    /**
     * 仅更新路线移动速度（运行中切换速度立即生效，不清空路线）。
     */
    public void updateRouteSpeed(double speed) {
        mSpeed = speed;
    }

    /**
     * 停止路线跟随并清空路线点。
     */
    public void stopRoute() {
        mRouteMode = false;
        mRouteIndex = 0;
        mRoutePoints.clear();
        mPaused = false;
    }

    /**
     * 暂停/继续路线移动（位置停留，不发起额外请求）。
     */
    public void setPaused(boolean paused) {
        mPaused = paused;
    }

    /**
     * 当前是否处于暂停状态。
     */
    public boolean isPaused() {
        return mPaused;
    }

    /**
     * 路线跟随是否正在运行。
     */
    public boolean isRouteRunning() {
        return mRouteMode && !mRoutePoints.isEmpty()
                && (mRouteLoop || mRouteIndex < mRoutePoints.size());
    }

    /**
     * 当前路径点下标（用于界面显示进度）。
     */
    public int getRouteIndex() {
        return mRouteIndex;
    }

    /**
     * 路线点总数。
     */
    public int getRouteSize() {
        return mRoutePoints.size();
    }

    /**
     * 整条路线的总距离（米）。
     */
    public double getRouteTotalDistance() {
        if (mRoutePoints.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        double[] prev = mRoutePoints.get(0);
        for (int i = 1; i < mRoutePoints.size(); i++) {
            double[] cur = mRoutePoints.get(i);
            total += haversineMeters(prev[1], prev[0], cur[1], cur[0]);
            prev = cur;
        }
        return total;
    }

    /**
     * 从当前位置到终点的剩余距离（米）。
     */
    public double getRouteRemainingDistance() {
        if (mRoutePoints.isEmpty()) {
            return 0.0;
        }
        // 走到终点且不循环时，视为走完
        if (mRouteIndex >= mRoutePoints.size() && !mRouteLoop) {
            return 0.0;
        }
        int idx = (mRouteIndex >= mRoutePoints.size()) ? 0 : mRouteIndex;
        double remain = 0.0;
        // 当前位置到当前目标点
        double[] target = mRoutePoints.get(idx);
        remain += haversineMeters(mCurLat, mCurLng, target[1], target[0]);
        // 当前目标点到终点各段
        for (int i = idx + 1; i < mRoutePoints.size(); i++) {
            double[] prev = mRoutePoints.get(i - 1);
            double[] cur = mRoutePoints.get(i);
            remain += haversineMeters(prev[1], prev[0], cur[1], cur[0]);
        }
        return remain;
    }

    // 两经纬度点间距离（米），使用 haversine 公式
    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS = 6371000.0;   // 地球平均半径，米
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    // 每 100ms 调用一次，把当前位置朝下一个路径点推进一个速度步长
    private void stepAlongRoute() {
        if (mRoutePoints.isEmpty()) {
            mRouteMode = false;
            return;
        }

        // 走到最后一个点：若需循环则回到起点，否则结束
        if (mRouteIndex >= mRoutePoints.size()) {
            if (mRouteLoop) {
                mRouteIndex = 0;
            } else {
                mRouteMode = false;
                return;
            }
        }

        double[] target = mRoutePoints.get(mRouteIndex);
        double targetLng = target[0];
        double targetLat = target[1];

        // 计算到目标点的经度/纬度差
        double dLng = targetLng - mCurLng;
        double dLat = targetLat - mCurLat;
        // 换算成米（每度纬度≈110574米，每度经度≈111320*cos(lat)米）
        double dLngMeter = dLng * 111320.0 * Math.cos(Math.abs(mCurLat) * Math.PI / 180);
        double dLatMeter = dLat * 110574.0;
        double dist = Math.sqrt(dLngMeter * dLngMeter + dLatMeter * dLatMeter);

        // 每次 tick 移动的距离 = 速度(m/s) * 100ms
        double step = mSpeed * 0.1;
        if (dist <= ROUTE_ARRIVE_THRESHOLD) {
            // 已到达目标点，吸附到该点并切换到下一个
            mCurLng = targetLng;
            mCurLat = targetLat;
            mRouteIndex++;
            // 到达终点后若循环，从头再来
            if (mRouteLoop && mRouteIndex >= mRoutePoints.size()) {
                mRouteIndex = 0;
            }
        } else if (step >= dist) {
            // 一步就能跨过目标点：直接落点，避免来回震荡
            mCurLng = targetLng;
            mCurLat = targetLat;
            mRouteIndex++;
            if (mRouteLoop && mRouteIndex >= mRoutePoints.size()) {
                mRouteIndex = 0;
            }
        } else {
            // 沿目标方向推进一个速度步长（米转回度）
            double ratio = step / dist;
            mCurLng += dLngMeter * ratio / (111320.0 * Math.cos(Math.abs(mCurLat) * Math.PI / 180));
            mCurLat += dLatMeter * ratio / 110574.0;
            // 朝向目标方向
            mCurBea = (float) Math.toDegrees(Math.atan2(dLatMeter, dLngMeter));
            if (mCurBea < 0) {
                mCurBea += 360;
            }
        }
    }

    private void removeTestProviderGPS() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderGPS");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderGPS() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
            } else {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderGPS");
        }
    }

    // 防检测：是否开启轨迹自然化
    public void setAntiDetect(boolean enabled) {
        mAntiDetect = enabled;
    }

    public boolean isAntiDetectEnabled() {
        return mAntiDetect;
    }

    // 防检测：上报速度在设定值 ±12% 内随机浮动，避免恒定速度特征
    private float noiseSpeed(float baseSpeed) {
        if (!mAntiDetect) {
            return baseSpeed;
        }
        float factor = 0.88f + mRand.nextFloat() * 0.24f;   // 0.88 ~ 1.12
        return baseSpeed * factor;
    }

    // 防检测：上报时间戳加一个小的随机毫秒偏移，避免完全规整
    private long noiseTime() {
        if (!mAntiDetect) {
            return System.currentTimeMillis();
        }
        return System.currentTimeMillis() + (long) (mRand.nextInt(21) - 10);   // -10 ~ +10 ms
    }

    private void setLocationGPS() {
        try {
            // 尽可能模拟真实的 GPS 数据
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_FINE);    // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(noiseTime());                    // 本地时间 + 抖动
            loc.setSpeed(noiseSpeed((float) mSpeed));
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            Bundle bundle = new Bundle();
            // 卫星数在 6~10 之间随机，更贴近真实 GPS
            bundle.putInt("satellites", 6 + mRand.nextInt(5));
            loc.setExtras(bundle);

            mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationGPS");
        }
    }

    private void removeTestProviderNetwork() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderNetwork");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderNetwork() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE);
            } else {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
            }
        } catch (SecurityException e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderNetwork");
        }
    }

    private void setLocationNetwork() {
        try {
            // 尽可能模拟真实的 NETWORK 数据
            Location loc = new Location(LocationManager.NETWORK_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_COARSE);  // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(noiseTime());                    // 本地时间 + 抖动
            loc.setSpeed(noiseSpeed((float) mSpeed));
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationNetwork");
        }
    }

    public class NoteActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 预留：通知栏按钮事件处理
        }
    }

    public class ServiceGoBinder extends Binder {
        public void setPosition(double lng, double lat, double alt) {
            mLocHandler.removeMessages(HANDLER_MSG_ID);
            mCurLng = lng;
            mCurLat = lat;
            mCurAlt = alt;
            mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
            }

        // 设置路线跟随
        public void setRoute(List<double[]> route, double speed, boolean loop) {
            ServiceGo.this.setRoute(route, speed, loop);
        }

        // 运行中更新速度（立即生效）
        public void updateRouteSpeed(double speed) {
            ServiceGo.this.updateRouteSpeed(speed);
        }

        // 停止路线跟随
        public void stopRoute() {
            ServiceGo.this.stopRoute();
        }

        // 暂停/继续路线移动
        public void setPaused(boolean paused) {
            ServiceGo.this.setPaused(paused);
        }

        // 查询是否暂停
        public boolean isPaused() {
            return ServiceGo.this.isPaused();
        }

        // 查询路线跟随状态
        public boolean isRouteRunning() {
            return ServiceGo.this.isRouteRunning();
        }

        // 查询当前进度
        public int getRouteIndex() {
            return ServiceGo.this.getRouteIndex();
        }

        public int getRouteSize() {
            return ServiceGo.this.getRouteSize();
        }

        // 路线总距离（米）
        public double getRouteTotalDistance() {
            return ServiceGo.this.getRouteTotalDistance();
        }

        // 当前位置到终点的剩余距离（米）
        public double getRouteRemainingDistance() {
            return ServiceGo.this.getRouteRemainingDistance();
        }

        // 防检测开关
        public void setAntiDetect(boolean enabled) {
            ServiceGo.this.setAntiDetect(enabled);
        }

        public boolean isAntiDetectEnabled() {
            return ServiceGo.this.isAntiDetectEnabled();
        }

        // 获取当前模拟位置的经纬度（WGS84）
        public double[] getCurrentPosition() {
            return new double[]{ServiceGo.this.mCurLng, ServiceGo.this.mCurLat};
        }
    }
}


