package com.zcshou.gogogo;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Poi;
import com.amap.api.maps.model.PolylineOptions;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zcshou.service.ServiceGo;
import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.database.DataBaseHistorySearch;
import com.zcshou.utils.ShareUtils;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.MapUtils;

import com.elvishew.xlog.XLog;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity implements SensorEventListener {
    /* 对外 */
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /*============================== 主界面地图 相关 ==============================*/
    /************** 地图 *****************/
    public final static BitmapDescriptor mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
    public static String mCurrentCity = null;
    private MapView mMapView;
    private static AMap mAMap = null;
    private static LatLng mMarkLatLngMap = new LatLng(35.3569754, 116.5744746); // 当前标记的地图点(默认山东理工职业学院操场, GCJ02)
    private static String mMarkName = null;
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private float[] mAccValues = new float[3];//加速度传感器数据
    private float[] mMagValues = new float[3];//地磁传感器数据
    private final float[] mR = new float[9];//旋转矩阵，用来保存磁场和加速度的数据
    private final float[] mDirectionValues = new float[3];//模拟方向传感器的数据（原始数据为弧度）
    /************** 定位 *****************/
    private AMapLocationClient mLocClient = null;
    private double mCurrentLat = 0.0;       // 当前位置的纬度(GCJ02)
    private double mCurrentLon = 0.0;       // 当前位置的经度(GCJ02)
    private float mCurrentDirection = 0.0f;
    private boolean isFirstLoc = true; // 是否首次定位
    private boolean isMockServStart = false;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private static BitmapDescriptor mLocIcon = null;   // 小尺寸定位蓝点图标
    private static Marker mPosMarker = null;             // 当前位置蓝点Marker
    private static Marker mMarkInfoMarker = null;         // 当前标记POI的marker（用于信息气泡）
    private View mPoiView = null;                         // POI信息气泡视图
    private TextView mPoiAddress = null;
    private TextView mPoiLongitude = null;
    private TextView mPoiLatitude = null;
    private ServiceConnection mConnection;
    private ImageButton mButtonStart;
    private View mBottomBar;   // 底部三按钮工具条
    private View mRoutePanel;              // 科技感信息面板
    private boolean mPermissionRequested = false;  // 定位权限是否已请求
    private TextView mPanelSpeed, mPanelWalked, mPanelRemain, mPanelPct;
    private TextView mPanelTime, mPanelPace;
    private ProgressBar mPanelProgress;
    private long mRouteStartTime = 0;        // 路线开始运行时间戳
    private long mPauseAccumMs = 0;          // 累计暂停时长
    private long mLastPauseStart = 0;        // 本次进入暂停的时间点
    private Button mPanelBtnWalk, mPanelBtnRun, mPanelBtnBike;
    private ImageButton mButtonRoute;
    private TextView mRouteProgressView;
    private final Handler mRouteProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRouteProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateRouteProgress();
            mRouteProgressHandler.postDelayed(this, 1000);
        }
    };
    private final List<LatLng> mRouteLatLngList = new ArrayList<>();  // 采集到的路线点（gcj02）
    private boolean mRouteEditMode = false;                          // 是否处于路线采集模式
    private boolean mRoutePendingStart = false;                      // 服务连接后需启动路线跟随
    private static final int ROUTE_MAX_POINTS = 300;                 // 路线点上限
    private static final String ROUTE_SP_KEY = "saved_routes";         // 已保存路线的 SharedPreferences key
    private ImageButton mButtonLoadRoute;
    private TextView mRouteCollectInfo;   // 采集模式下实时显示点数和距离
    private LinearLayout mRouteEditActions; // 采集模式下的撤销/清空按钮组
    private String mRouteSpeedMode = "walk";  // 当前路线速度档位: walk/run/bike
    /*============================== 历史记录 相关 ==============================*/
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;
    /*============================== SearchView 相关 ==============================*/
    private SearchView searchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;
    /*============================== 更新 相关 ==============================*/
    private DownloadManager mDownloadManager = null;
    private long mDownloadId;
    private BroadcastReceiver mDownloadBdRcv;
    private String mUpdateFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();

        // 高德 MapView 生命周期：必须调用 onCreate，否则地图引擎不初始化、画面白屏
        mMapView = findViewById(R.id.bdMapView);
        mMapView.onCreate(savedInstanceState);

        initMap();

        initMapLocation();

        initMapButton();

        initGoBtn();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder)service;
                // 应用防检测开关（轨迹自然化）
                boolean antiDetect = sharedPreferences.getBoolean("setting_anti_detect", true);
                mServiceBinder.setAntiDetect(antiDetect);
                // 若启动时已采集好路线，则服务连接后立即开始路线跟随
                if (mRoutePendingStart && !mRouteLatLngList.isEmpty()) {
                    startRouteOnService();
                    mRoutePendingStart = false;
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        initStoreHistory();

        initSearchView();

        // 更新检查已移除（用户要求删除更新弹窗）
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        mMapView.onPause();
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        if (mMapView != null) {
            mMapView.onResume();
        }
        if (mSensorManager != null) {
            mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
        }
        // 首次进入且未授权时请求定位权限（界面可见，最可靠）
        if (!mPermissionRequested
                && (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            mPermissionRequested = true;
            requestPermissions(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        XLog.i("MainActivity: onStop");
        //取消注册传感器监听
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");

        if (isMockServStart) {
            unbindService(mConnection); // 解绑服务，服务要记得解绑，不要造成内存泄漏
            Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
            stopService(serviceGoIntent);
        }
        mSensorManager.unregisterListener(this);

        // 退出时销毁定位
        if (mLocClient != null) {
            mLocClient.stopLocation();
            mLocClient.onDestroy();
            mLocClient = null;
        }
        if (mMapView != null) {
            mMapView.onDestroy();
            mMapView = null;
        }

        //close db
        mLocationHistoryDB.close();
        mSearchHistoryDB.close();

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mMapView != null) {
            mMapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        //找到searchView
        searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(new  MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                mHistoryLayout.setVisibility(View.INVISIBLE);
                return true;  // Return true to collapse action view
            }
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                //展示搜索历史
                List<Map<String, Object>> data = getSearchHistory();

                if (!data.isEmpty()) {
                    SimpleAdapter simAdapt = new SimpleAdapter(
                            MainActivity.this,
                            data,
                            R.layout.search_item,
                            new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                    DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                    DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                    DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                    DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                    DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                            new int[] {R.id.search_key,
                                    R.id.search_description,
                                    R.id.search_timestamp,
                                    R.id.search_isLoc,
                                    R.id.search_longitude,
                                    R.id.search_latitude});
                    mSearchHistoryList.setAdapter(simAdapt);
                    mHistoryLayout.setVisibility(View.VISIBLE);
                }

                return true;  // Return true to expand action view
            }
        });

        searchView = (SearchView) searchItem.getActionView();
        searchView.setIconified(true); // 默认折叠，点击右上角搜索图标才展开
        searchView.setIconifiedByDefault(true);//默认为true在框内，设置false则在框外
        searchView.setSubmitButtonEnabled(false);//显示提交按钮
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                //搜索功能已停用（搜索入口已移除），不再发起高德Web请求
                try {
                    //搜索历史 插表参数
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, query);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                    DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
                    clearMap();
                    mSearchLayout.setVisibility(View.INVISIBLE);
                } catch (Exception e) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                    XLog.d(getResources().getString(R.string.app_error_search));
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //当输入框内容改变的时候回调
                //搜索历史置为不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);

                if (newText != null && !newText.isEmpty()) {
                    //搜索已停用，不发起高德Web请求
                }

                return true;
            }
        });

        // 搜索框的清除按钮(该按钮属于安卓系统图标)
        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            EditText et = findViewById(androidx.appcompat.R.id.search_src_text);
            et.setText("");
            searchView.setQuery("", false);
            mSearchLayout.setVisibility(View.INVISIBLE);
            mHistoryLayout.setVisibility(View.VISIBLE);
        });

        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            mAccValues = sensorEvent.values;
        }
        else if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            mMagValues = sensorEvent.values;
        }

        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);
        mCurrentDirection = (float) Math.toDegrees(mDirectionValues[0]);    // 弧度转角度
        if (mCurrentDirection < 0) {    // 由 -180 ~ + 180 转为 0 ~ 360
            mCurrentDirection += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /*============================== NavigationView 相关 ==============================*/
    private void initNavigationView() {
        /*============================== NavigationView 相关 ==============================*/
        NavigationView mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_history) {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);

                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_dev) {
                // 直接跳转到手机系统的开发者选项设置页
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    startActivity(intent);
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, "无法打开开发者选项，请到系统设置中手动开启");
                }
            } else if (id == R.id.nav_update) {
                GoUtils.DisplayToast(this, "当前已是最新版本");
            }

            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);

            return true;
        });

        // 直接获取第 0 个头部视图
        View headerView = mNavigationView.getHeaderView(0);
        TextView app_version = headerView.findViewById(R.id.app_version);
        app_version.setText(GoUtils.getVersionName(this));

        // 分组标题（subheader）在深色背景上默认发灰，手动改为浅蓝白，保证清晰可读
        Menu navMenu = mNavigationView.getMenu();
        for (int i = 0; i < navMenu.size(); i++) {
            MenuItem item = navMenu.getItem(i);
            if (item.hasSubMenu() && item.getTitle() != null) {
                SpannableString title = new SpannableString(item.getTitle().toString());
                title.setSpan(new ForegroundColorSpan(0xFFB0E0F7), 0, title.length(), 0);
                item.setTitle(title);
            }
        }
    }

    /*============================== 主界面地图 相关 ==============================*/
    private void initMap() {
        GoApplication.writeInitLog("initMap start");
        // 地图初始化
        mMapView = findViewById(R.id.bdMapView);
        GoApplication.writeInitLog("initMap: MapView found, mMapView=" + (mMapView == null ? "null" : "ok"));
        mAMap = mMapView.getMap();
        GoApplication.writeInitLog("initMap: getMap, mAMap=" + (mAMap == null ? "null" : "ok"));
        mAMap.setMapType(AMap.MAP_TYPE_NORMAL);
        GoApplication.writeInitLog("initMap: setMapType OK");
        // 隐藏右下角缩放控件
        mAMap.getUiSettings().setZoomControlsEnabled(false);
        GoApplication.writeInitLog("initMap: uiSettings OK");
        // 地图初始定位到默认位置（山东理工职业学院操场）
        CameraUpdate initCu = CameraUpdateFactory.newCameraPosition(
                new CameraPosition(mMarkLatLngMap, 18f, 0, 0));
        mAMap.moveCamera(initCu);
        GoApplication.writeInitLog("initMap: moveCamera OK");
        // 打开即显示当前位置蓝点（真实定位成功后自动更新）
        if (mLocIcon == null) {
            mLocIcon = createLocIcon();
        }
        mPosMarker = mAMap.addMarker(new MarkerOptions()
                .position(mMarkLatLngMap)
                .icon(mLocIcon)
                .anchor(0.5f, 0.5f));
        GoApplication.writeInitLog("initMap: addMarker(pos) OK");
        mAMap.setOnMapTouchListener(motionEvent -> {
        });
        mAMap.setOnMapClickListener(new AMap.OnMapClickListener() {
            /**
             * 单击地图
             */
            @Override
            public void onMapClick(LatLng point) {
                // 路线采集模式下，点击地图依次添加途经点
                if (mRouteEditMode) {
                    addRoutePoint(point);
                    return;
                }
                mMarkLatLngMap = point;
                markMap();
            }
        });
        // 单击地图中的POI点
        mAMap.setOnPOIClickListener(new AMap.OnPOIClickListener() {
            @Override
            public void onPOIClick(Poi poi) {
                mMarkLatLngMap = poi.getCoordinate();
                markMap();
            }
        });
        mAMap.setOnMapLongClickListener(new AMap.OnMapLongClickListener() {
            /**
             * 长按地图
             */
            @Override
            public void onMapLongClick(LatLng point) {
                mMarkLatLngMap = point;
                markMap();
            }
        });
        mPoiView = View.inflate(MainActivity.this, R.layout.location_poi_info, null);
        mPoiAddress = mPoiView.findViewById(R.id.poi_address);
        mPoiLongitude = mPoiView.findViewById(R.id.poi_longitude);
        mPoiLatitude = mPoiView.findViewById(R.id.poi_latitude);
        // 自定义信息气泡：统一返回 poi 气泡视图（高德通过 InfoWindowAdapter 实现）
        GoApplication.writeInitLog("initMap: poiView inflated, setting InfoWindowAdapter");
        mAMap.setInfoWindowAdapter(new AMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return mPoiView;
            }

            @Override
            public View getInfoContents(Marker marker) {
                return null;
            }
        });
        ImageButton ibSave = mPoiView.findViewById(R.id.poi_save);
        ibSave.setOnClickListener(v -> {
            recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_location_save));
        });
        ImageButton ibCopy = mPoiView.findViewById(R.id.poi_copy);
        ibCopy.setOnClickListener(v -> {
            //获取剪贴板管理器：
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建普通字符型ClipData
            ClipData mClipData = ClipData.newPlainText("Label", mMarkLatLngMap.toString());
            // 将 ClipData内容放到系统剪贴板里。
            cm.setPrimaryClip(mClipData);

            GoUtils.DisplayToast(this,  getResources().getString(R.string.app_location_copy));
        });
        ImageButton ibShare = mPoiView.findViewById(R.id.poi_share);
        ibShare.setOnClickListener(v -> ShareUtils.shareText(MainActivity.this, "分享位置", mPoiLongitude.getText()+","+mPoiLatitude.getText()));
        ImageButton ibFly = mPoiView.findViewById(R.id.poi_fly);
        ibFly.setOnClickListener(this::doGoLocation);
        GoApplication.writeInitLog("initMap: all listeners OK");
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);// 获取传感器管理服务
        if (mSensorManager != null) {
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    // 生成小尺寸定位蓝点图标（比百度默认图标小，路线运行时更协调）
    private BitmapDescriptor createLocIcon() {
        int size = 52;   // px
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x3388D1F1);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(0xFF0288D1);
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint);
        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    // 更新当前位置蓝点（Marker 方案，高德定位图层不开启）
    private static void updatePosMarker(double lat, double lng) {
        if (mAMap == null) {
            return;
        }
        if (mLocIcon == null) {
            mLocIcon = createLocIconStatic();
        }
        LatLng pos = new LatLng(lat, lng);
        if (mPosMarker == null) {
            mPosMarker = mAMap.addMarker(new MarkerOptions()
                    .position(pos).icon(mLocIcon).anchor(0.5f, 0.5f));
        } else {
            mPosMarker.setPosition(pos);
        }
    }

    // 清空地图上的覆盖物，同时保留当前位置蓝点
    private static void clearMap() {
        if (mAMap == null) {
            return;
        }
        LatLng keepPos = (mPosMarker != null) ? mPosMarker.getPosition() : null;
        mAMap.clear();
        if (keepPos != null) {
            if (mLocIcon == null) {
                mLocIcon = createLocIconStatic();
            }
            mPosMarker = mAMap.addMarker(new MarkerOptions()
                    .position(keepPos).icon(mLocIcon).anchor(0.5f, 0.5f));
        } else {
            mPosMarker = null;
        }
    }

    // 生成小尺寸定位蓝点图标（静态版，供静态方法使用）
    private static BitmapDescriptor createLocIconStatic() {
        int size = 52;   // px
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x3388D1F1);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(0xFF0288D1);
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint);
        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    //开启地图的定位
    private void initMapLocation() {
        try {
            // 小尺寸定位图标（蓝点）
            if (mLocIcon == null) {
                mLocIcon = createLocIcon();
            }
            // 定位初始化
            mLocClient = new AMapLocationClient(this);
            mLocClient.setLocationListener(new AMapLocationListener() {
                @Override
                public void onLocationChanged(AMapLocation aMapLocation) {
                    if (aMapLocation == null || mMapView == null) {// mapview 销毁后不在处理新接收的位置
                        return;
                    }
                    // 过滤无效定位：errorCode!=0 为失败，经纬度为0会跳到非洲赤道(0,0)
                    if (aMapLocation.getErrorCode() != 0) {
                        return;
                    }
                    double locLat = aMapLocation.getLatitude();
                    double locLon = aMapLocation.getLongitude();
                    if (locLat == 0.0 && locLon == 0.0) {
                        return;
                    }

                    mCurrentCity = aMapLocation.getCity();
                    mCurrentLat = locLat;
                    mCurrentLon = locLon;
                    // 路线模拟运行时，不把真实定位写入地图蓝点，避免和模拟位置冲突导致图标晃动
                    boolean routeRunning = (mServiceBinder != null && mServiceBinder.isRouteRunning());
                    if (!routeRunning) {
                        updatePosMarker(locLat, locLon);
                    }

                    if (isFirstLoc) {
                        isFirstLoc = false;
                        // 这里记录高德返回的位置
                        mMarkLatLngMap = new LatLng(locLat, locLon);
                        mAMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMarkLatLngMap, 18f));

                        XLog.i("First AMap LatLng: " + mMarkLatLngMap);
                    }
                }
            });
            AMapLocationClientOption locationOption = getLocationClientOption();
            //需将配置好的LocationClientOption对象，通过setLocationOption方法传递给LocationClient对象使用
            mLocClient.setLocationOption(locationOption);
            //开始定位
            mLocClient.startLocation();
        } catch (Exception e) {
            XLog.e("ERROR: initMapLocation");
        }
    }

    @NonNull
    private static AMapLocationClientOption getLocationClientOption() {
        AMapLocationClientOption locationOption = new AMapLocationClientOption();
        //高精度定位模式
        locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        // 设置定位间隔(毫秒)，连续定位
        locationOption.setInterval(1000);
        // 设置是否需要地址信息
        locationOption.setNeedAddress(true);
        // 设置是否优先使用GPS
        locationOption.setGpsFirst(true);
        // 关闭缓存，避免拿到旧位置
        locationOption.setLocationCacheEnable(false);
        return locationOption;
    }

    //地图上各按键的监听
    private void initMapButton() {
        RadioGroup mGroupMapType = this.findViewById(R.id.RadioGroupMapType);
        mGroupMapType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.mapNormal) {
                mAMap.setMapType(AMap.MAP_TYPE_NORMAL);
            }

            if (checkedId == R.id.mapSatellite) {
                mAMap.setMapType(AMap.MAP_TYPE_SATELLITE);
            }
        });

        ImageButton curPosBtn = this.findViewById(R.id.cur_position);
        curPosBtn.setOnClickListener(v -> {
            // 仅点击"当前位置"时提示开启GPS
            if (!GoUtils.isGpsOpened(this)) {
                GoUtils.showEnableGpsDialog(this);
                return;
            }
            // 强制请求一次定位，再复位地图
            if (mLocClient != null) {
                mLocClient.startLocation();
            }
            resetMapSafe();
        });

        ImageButton zoomInBtn = this.findViewById(R.id.zoom_in);
        zoomInBtn.setOnClickListener(v -> mAMap.animateCamera(CameraUpdateFactory.zoomIn()));

        ImageButton zoomOutBtn = this.findViewById(R.id.zoom_out);
        zoomOutBtn.setOnClickListener(v -> mAMap.animateCamera(CameraUpdateFactory.zoomOut()));

        ImageButton inputPosBtn = this.findViewById(R.id.input_pos);
        inputPosBtn.setOnClickListener(v -> {
            AlertDialog dialog;
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("请输入经度和纬度");
            View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.location_input, null);
            builder.setView(view);
            dialog = builder.show();

            EditText dialog_lng = view.findViewById(R.id.joystick_longitude);
            EditText dialog_lat = view.findViewById(R.id.joystick_latitude);
            RadioButton rbBD = view.findViewById(R.id.pos_type_bd);

            Button btnGo = view.findViewById(R.id.input_position_ok);
            btnGo.setOnClickListener(v2 -> {
                String dialog_lng_str = dialog_lng.getText().toString();
                String dialog_lat_str = dialog_lat.getText().toString();

                if (TextUtils.isEmpty(dialog_lng_str) || TextUtils.isEmpty(dialog_lat_str)) {
                    GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.app_error_input));
                } else {
                    double dialog_lng_double = Double.parseDouble(dialog_lng_str);
                    double dialog_lat_double = Double.parseDouble(dialog_lat_str);

                    if (dialog_lng_double > 180.0 || dialog_lng_double < -180.0) {
                        GoUtils.DisplayToast(MainActivity.this,  getResources().getString(R.string.app_error_longitude));
                    } else {
                        if (dialog_lat_double > 90.0 || dialog_lat_double < -90.0) {
                            GoUtils.DisplayToast(MainActivity.this,  getResources().getString(R.string.app_error_latitude));
                        } else {
                            if (rbBD.isChecked()) {
                                // 高德坐标系(GCJ02)，直接使用
                                mMarkLatLngMap = new LatLng(dialog_lat_double, dialog_lng_double);
                            } else {
                                // GPS坐标系(WGS84)，转换为高德GCJ02
                                double[] gcjLonLat = MapUtils.wgs2gcj02(dialog_lng_double, dialog_lat_double);
                                mMarkLatLngMap = new LatLng(gcjLonLat[1], gcjLonLat[0]);
                            }
                            mMarkName = "手动输入的坐标";

                            markMap();

                            mAMap.moveCamera(CameraUpdateFactory.newLatLng(mMarkLatLngMap));

                            dialog.dismiss();
                        }
                    }
                }
            });

            Button btnCancel = view.findViewById(R.id.input_position_cancel);
            btnCancel.setOnClickListener(v1 -> dialog.dismiss());
        });
    }

    //标定选择的位置
    private void markMap() {
        if (mMarkLatLngMap != null) {
            MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
            clearMap();
            mMarkInfoMarker = mAMap.addMarker(ooA);
        }
    }

    private void resetMap() {
        // 优先定位到当前模拟位置（服务运行中）；否则用高德真实定位；都没有则回退到默认位置（山东理工操场）
        double targetLat, targetLng;
        if (mServiceBinder != null && mServiceBinder.isRouteRunning()) {
            double[] cur = mServiceBinder.getCurrentPosition();   // {lng, lat} wgs84
            double[] gcj = MapUtils.wgs2gcj02(cur[0], cur[1]);
            targetLng = gcj[0];
            targetLat = gcj[1];
        } else if (mCurrentLat != 0.0 && mCurrentLon != 0.0) {
            targetLat = mCurrentLat;
            targetLng = mCurrentLon;
        } else {
            double[] gcj = MapUtils.wgs2gcj02(ServiceGo.DEFAULT_LNG, ServiceGo.DEFAULT_LAT);
            targetLng = gcj[0];
            targetLat = gcj[1];
        }

        clearMap();
        mMarkLatLngMap = new LatLng(targetLat, targetLng);
        updatePosMarker(targetLat, targetLng);

        mAMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMarkLatLngMap, 18f));
    }

    // 复位：兜底异常保护（保证当前位置按钮必响应）
    private void resetMapSafe() {
        try {
            resetMap();
        } catch (Exception e) {
            XLog.e("resetMap error: " + e.getMessage());
            GoUtils.DisplayToast(this, "定位失败，已回退到默认位置");
            double[] gcj = MapUtils.wgs2gcj02(ServiceGo.DEFAULT_LNG, ServiceGo.DEFAULT_LAT);
            clearMap();
            mMarkLatLngMap = new LatLng(gcj[1], gcj[0]);
            updatePosMarker(gcj[1], gcj[0]);
            mAMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMarkLatLngMap, 18f));
        }
    }

    // 在地图上显示位置（坐标为高德GCJ02）
    public static boolean showLocation(String name, String gcjLongitude, String gcjLatitude) {
        boolean ret = true;

        try {
            if (!gcjLongitude.isEmpty() && !gcjLatitude.isEmpty()) {
                mMarkName = name;
                mMarkLatLngMap = new LatLng(Double.parseDouble(gcjLatitude), Double.parseDouble(gcjLongitude));
                MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
                clearMap();
                mMarkInfoMarker = mAMap.addMarker(ooA);
                mAMap.moveCamera(CameraUpdateFactory.newLatLng(mMarkLatLngMap));
            }
        } catch (Exception e) {
            ret = false;
            XLog.e("ERROR: showHistoryLocation");
        }

        return ret;
    }

    private void initGoBtn() {
        mBottomBar = findViewById(R.id.bottom_bar);
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);

        mButtonRoute = findViewById(R.id.faBtnRoute);
        mRouteProgressView = findViewById(R.id.route_progress);
        mButtonRoute.setOnClickListener(v -> toggleRouteEditMode());
        mButtonRoute.setOnLongClickListener(v -> {
            if (mRouteEditMode) {
                undoRoutePoint();    // 采集模式下长按 = 撤销上一步
            } else {
                clearRoute();
            }
            return true;
        });

        mButtonLoadRoute = findViewById(R.id.faBtnLoadRoute);
        mButtonLoadRoute.setOnClickListener(v -> showLoadRouteDialog());
        mButtonLoadRoute.setOnLongClickListener(v -> {
            importRouteFromClipboard();   // 长按 = 导入分享的路线
            return true;
        });

        mRouteCollectInfo = findViewById(R.id.route_collect_info);
        mRouteEditActions = findViewById(R.id.route_edit_actions);
        findViewById(R.id.btnRouteUndo).setOnClickListener(v -> undoRoutePoint());
        findViewById(R.id.btnRouteClear).setOnClickListener(v -> clearRoute());
        // 保存路线：退出采集模式并弹出保存（含分享）对话框
        findViewById(R.id.btnRouteSave).setOnClickListener(v -> {
            if (mRouteLatLngList.size() < 2) {
                GoUtils.DisplayToast(this, getString(R.string.route_save_empty));
                return;
            }
            mRouteEditMode = false;
            drawRouteOverlay();
            updateCollectInfo();
            showSaveRouteDialog();
        });

        initRoutePanel();
    }

    // 初始化科技感信息面板并接线
    private void initRoutePanel() {
        mRoutePanel = findViewById(R.id.route_panel);
        mPanelSpeed = findViewById(R.id.panel_speed);
        mPanelWalked = findViewById(R.id.panel_walked);
        mPanelRemain = findViewById(R.id.panel_remain);
        mPanelPct = findViewById(R.id.panel_progress_pct);
        mPanelProgress = findViewById(R.id.panel_progress_bar);
        mPanelBtnWalk = findViewById(R.id.panel_btn_walk);
        mPanelBtnRun = findViewById(R.id.panel_btn_run);
        mPanelBtnBike = findViewById(R.id.panel_btn_bike);
        mPanelTime = findViewById(R.id.panel_time);
        mPanelPace = findViewById(R.id.panel_pace);
        findViewById(R.id.panel_btn_stop).setOnClickListener(v -> doGoLocation(mButtonStart));
        // 暂停/继续：切换路线移动状态，位置停留不发起请求
        findViewById(R.id.panel_btn_pause).setOnClickListener(v -> {
            Button pauseBtn = (Button) v;
            boolean toPause = !pauseBtn.isSelected();
            pauseBtn.setSelected(toPause);
            pauseBtn.setText(toPause ? "继续" : "暂停");
            // 暂停/继续时累计暂停时长（暂停期间不计入用时）
            if (toPause) {
                mLastPauseStart = System.currentTimeMillis();
            } else if (mLastPauseStart > 0) {
                mPauseAccumMs += System.currentTimeMillis() - mLastPauseStart;
                mLastPauseStart = 0;
            }
            if (mServiceBinder != null) {
                mServiceBinder.setPaused(toPause);
            } else {
                GoUtils.DisplayToast(this, "路线未在运行");
            }
        });

        // 三档速度切换（面板内直接生效）
        mPanelBtnWalk.setOnClickListener(v -> {
            setSpeedMode("walk");
            updatePanelSpeedButtons();
        });
        mPanelBtnRun.setOnClickListener(v -> {
            setSpeedMode("run");
            updatePanelSpeedButtons();
        });
        mPanelBtnBike.setOnClickListener(v -> {
            setSpeedMode("bike");
            updatePanelSpeedButtons();
        });
        updatePanelSpeedButtons();
    }

    // 设置速度档位并立即应用到运行中的服务
    private void setSpeedMode(String mode) {
        mRouteSpeedMode = mode;
        if (mServiceBinder != null && mServiceBinder.isRouteRunning()) {
            mServiceBinder.updateRouteSpeed(getCurrentRouteSpeed());
        }
        updatePanelSpeedText();
    }

    // 刷新面板速度文本
    private void updatePanelSpeedText() {
        if (mPanelSpeed != null) {
            mPanelSpeed.setText(String.format("%.1f", getCurrentRouteSpeed()));
        }
    }

    // 高亮当前速度档位按钮
    private void updatePanelSpeedButtons() {
        mPanelBtnWalk.setSelected("walk".equals(mRouteSpeedMode));
        mPanelBtnRun.setSelected("run".equals(mRouteSpeedMode));
        mPanelBtnBike.setSelected("bike".equals(mRouteSpeedMode));
        updatePanelSpeedText();
    }

    // 控制底部工具条显隐：路线运行时隐藏，空闲时显示
    private void setBottomBarVisible(boolean visible) {
        if (mBottomBar != null) {
            mBottomBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    // 获取当前路线运行速度（三档：步行1.2 / 跑步3.6 / 骑车10.5）
    private double getCurrentRouteSpeed() {
        switch (mRouteSpeedMode) {
            case "run":
                return Double.parseDouble(sharedPreferences.getString("setting_run", "3.6"));
            case "bike":
                return Double.parseDouble(sharedPreferences.getString("setting_bike", "10.5"));
            default:
                return Double.parseDouble(sharedPreferences.getString("setting_walk", "1.2"));
        }
    }

    // 弹出三档速度选择对话框（步行1.2 / 跑步3.6 / 骑车10.5）
    private void showSpeedSelectDialog() {
        final String[] modes = {"walk", "run", "bike"};
        final String[] labels = {
                "步行  " + sharedPreferences.getString("setting_walk", "1.2") + " 米/秒",
                "跑步  " + sharedPreferences.getString("setting_run", "3.6") + " 米/秒",
                "骑车  " + sharedPreferences.getString("setting_bike", "10.5") + " 米/秒"
        };
        int check = 0;
        switch (mRouteSpeedMode) {
            case "run": check = 1; break;
            case "bike": check = 2; break;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择移动速度")
                .setSingleChoiceItems(labels, check, (dialog, which) -> {
                    mRouteSpeedMode = modes[which];
                    dialog.dismiss();
                    GoUtils.DisplayToast(this, "速度已设为：" + labels[which]);
                    // 若路线正在运行，立即更新服务端速度，无需重启
                    if (mServiceBinder != null && mServiceBinder.isRouteRunning()) {
                        mServiceBinder.updateRouteSpeed(getCurrentRouteSpeed());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 把米数格式化为可读的“xx 公里 / xx 米”
    private static String formatDistance(double meters) {
        if (meters >= 1000) {
            return String.format("%.2f 公里", meters / 1000.0);
        }
        return String.format("%.0f 米", meters);
    }

    // 计算采集到的路线点连线的总距离（米）
    private double routeTotalMeters() {
        if (mRouteLatLngList.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        LatLng prev = mRouteLatLngList.get(0);
        for (int i = 1; i < mRouteLatLngList.size(); i++) {
            LatLng cur = mRouteLatLngList.get(i);
            total += haversineMeters(prev.latitude, prev.longitude, cur.latitude, cur.longitude);
            prev = cur;
        }
        return total;
    }

    // 两经纬度点间距离（米），与 ServiceGo 中一致
    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    // 实时更新采集信息条（点数 + 累计距离）
    private void updateCollectInfo() {
        if (mRouteCollectInfo == null) return;
        if (mRouteEditMode) {
            if (mRouteLatLngList.isEmpty()) {
                mRouteCollectInfo.setText("路线采集中：点击地图添加途经点");
            } else {
                mRouteCollectInfo.setText("路线采集中：" + mRouteLatLngList.size() + " 个点 · "
                        + formatDistance(routeTotalMeters()) + " · 点💾保存");
            }
            mRouteCollectInfo.setVisibility(View.VISIBLE);
            if (mRouteEditActions != null) {
                mRouteEditActions.setVisibility(View.VISIBLE);
            }
        } else {
            mRouteCollectInfo.setVisibility(View.GONE);
            if (mRouteEditActions != null) {
                mRouteEditActions.setVisibility(View.GONE);
            }
        }
    }

    // 切换路线采集模式：开启后点击地图依次添加途经点
    private void toggleRouteEditMode() {
        mRouteEditMode = !mRouteEditMode;
        if (mRouteEditMode) {
            mRouteLatLngList.clear();
            clearMap();
            updateCollectInfo();
        } else {
            drawRouteOverlay();
            updateCollectInfo();
            // 画完路线自动弹出保存（含分享）
            if (!mRouteLatLngList.isEmpty()) {
                showSaveRouteDialog();
            }
        }
    }

    // 撤销上一步：移除最后一个途经点并重绘
    private void undoRoutePoint() {
        if (mRouteLatLngList.isEmpty()) {
            GoUtils.DisplayToast(this, "没有可撤销的点");
            return;
        }
        mRouteLatLngList.remove(mRouteLatLngList.size() - 1);
        drawRouteOverlay();
        updateCollectInfo();
        GoUtils.DisplayToast(this, "已撤销上一步，剩余 " + mRouteLatLngList.size() + " 点");
    }

    // 清空路线采集
    private void clearRoute() {
        mRouteEditMode = false;
        mRouteLatLngList.clear();
        clearMap();
        updateCollectInfo();
    }

    /* ============================ 路线持久化存储 ============================ */

    // 读取所有已保存路线：Map<名称, 点列表JSON字符串>
    private org.json.JSONObject getSavedRoutes() {
        String raw = sharedPreferences.getString(ROUTE_SP_KEY, "{}");
        try {
            return new org.json.JSONObject(raw);
        } catch (Exception e) {
            return new org.json.JSONObject();
        }
    }

    // 保存路线：弹出输入框让用户命名
    private void showSaveRouteDialog() {
        if (mRouteLatLngList.size() < 2) {
            GoUtils.DisplayToast(this, getString(R.string.route_save_empty));
            return;
        }
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(getString(R.string.route_save_hint));
        input.setSingleLine();
        // 默认不聚焦，避免软键盘弹起盖住底部的"分享路线"按钮；点输入框时才弹键盘
        input.setFocusable(false);
        input.setFocusableInTouchMode(true);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.route_save))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "路线" + System.currentTimeMillis() % 10000;
                    }
                    saveRoute(name);
                })
                .setNeutralButton("分享路线", (dialog, which) -> shareRouteToClipboard())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // 分享路线：把当前路线点编码为JSON文本，复制到剪贴板
    private void shareRouteToClipboard() {
        if (mRouteLatLngList.size() < 2) {
            GoUtils.DisplayToast(this, getString(R.string.route_save_empty));
            return;
        }
        try {
            org.json.JSONArray points = new org.json.JSONArray();
            for (LatLng p : mRouteLatLngList) {
                org.json.JSONObject pt = new org.json.JSONObject();
                pt.put("lng", p.longitude);
                pt.put("lat", p.latitude);
                points.put(pt);
            }
            org.json.JSONObject wrap = new org.json.JSONObject();
            wrap.put("type", "zhifeiji_route");
            wrap.put("points", points);
            String data = wrap.toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("route", data));
            GoUtils.DisplayToast(this, "路线已复制，正在调起分享...");
            // 调起系统分享面板，可通过微信/QQ/短信等发送
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, data);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "纸飞机路线分享");
            startActivity(Intent.createChooser(shareIntent, "分享路线到"));
        } catch (Exception e) {
            XLog.e("shareRoute error: " + e.getMessage());
        }
    }

    // 导入路线：弹输入框，预填剪贴板内容，用户可粘贴/编辑后确认
    private void importRouteFromClipboard() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("粘贴或输入好友分享的路线数据（JSON格式）");
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setGravity(android.view.Gravity.TOP);
        input.setPadding(40, 30, 40, 30);
        input.setTextSize(13);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("导入分享路线")
                .setMessage("复制好友分享的路线文本后，长按输入框粘贴到此处：")
                .setView(input)
                .setPositiveButton("导入", (d, w) -> doImportRoute(input.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    // 实际执行路线导入
    private void doImportRoute(String data) {
        try {
            if (data == null || data.trim().isEmpty()) {
                GoUtils.DisplayToast(this, "路线数据为空");
                return;
            }
            org.json.JSONObject wrap = new org.json.JSONObject(data.trim());
            if (!"zhifeiji_route".equals(wrap.optString("type"))) {
                GoUtils.DisplayToast(this, "不是有效的路线数据（缺少 type 标识）");
                return;
            }
            org.json.JSONArray points = wrap.getJSONArray("points");
            if (points.length() < 2) {
                GoUtils.DisplayToast(this, "路线点数不足（至少2个点）");
                return;
            }
            mRouteLatLngList.clear();
            for (int i = 0; i < points.length(); i++) {
                org.json.JSONObject pt = points.getJSONObject(i);
                mRouteLatLngList.add(new LatLng(pt.getDouble("lat"), pt.getDouble("lng")));
            }
            mRouteEditMode = false;
            drawRouteOverlay();
            updateCollectInfo();
            GoUtils.DisplayToast(this, "路线导入成功：" + mRouteLatLngList.size() + " 点，"
                    + formatDistance(routeTotalMeters()));
            showSaveRouteDialog();
        } catch (Exception e) {
            XLog.e("importRoute error: " + e.getMessage());
            GoUtils.DisplayToast(this, "路线导入失败：数据格式不正确");
        }
    }

    // 将当前采集的路线以指定名称保存到 SharedPreferences
    private void saveRoute(String name) {
        try {
            org.json.JSONObject routes = getSavedRoutes();
            org.json.JSONArray points = new org.json.JSONArray();
            for (LatLng p : mRouteLatLngList) {
                org.json.JSONObject pt = new org.json.JSONObject();
                pt.put("lng", p.longitude);
                pt.put("lat", p.latitude);
                points.put(pt);
            }
            routes.put(name, points);
            sharedPreferences.edit().putString(ROUTE_SP_KEY, routes.toString()).apply();
            // 保存成功后询问是否立即运行
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("路线已保存")
                    .setMessage("路线「" + name + "」已保存（" + mRouteLatLngList.size()
                            + "个点，" + formatDistance(routeTotalMeters()) + "）\n是否立即运行？")
                    .setPositiveButton("立即运行", (dialog, which) -> {
                        mRouteEditMode = false;
                        updateCollectInfo();
                        doGoLocation(mButtonStart);
                    })
                    .setNegativeButton("稍后", null)
                    .show();
        } catch (Exception e) {
            XLog.e("saveRoute error: " + e.getMessage());
        }
    }

    // 显示已保存路线列表，选择后加载到地图
    private void showLoadRouteDialog() {
        org.json.JSONObject routes = getSavedRoutes();
        if (routes.length() == 0) {
            // 无本地路线时，提示可导入分享的路线
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("暂无已保存路线")
                    .setMessage("可以点击「导入分享路线」粘贴好友分享的路线数据")
                    .setPositiveButton("导入分享路线", (d, w) -> importRouteFromClipboard())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.Iterator<String> it = routes.keys();
        while (it.hasNext()) {
            names.add(it.next());
        }
        java.util.Collections.sort(names);
        // 构建带点数和距离的显示列表
        String[] displayArray = new String[names.size()];
        for (int i = 0; i < names.size(); i++) {
            try {
                org.json.JSONArray pts = routes.getJSONArray(names.get(i));
                double dist = 0;
                for (int j = 1; j < pts.length(); j++) {
                    org.json.JSONObject a = pts.getJSONObject(j-1);
                    org.json.JSONObject b = pts.getJSONObject(j);
                    dist += haversineMeters(a.getDouble("lat"), a.getDouble("lng"),
                            b.getDouble("lat"), b.getDouble("lng"));
                }
                displayArray[i] = names.get(i) + "  (" + pts.length() + "点, " + formatDistance(dist) + ")";
            } catch (Exception e) {
                displayArray[i] = names.get(i);
            }
        }
        final java.util.List<String> finalNames = names;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择路线（点列表项=加载并运行）")
                .setItems(displayArray, (dialog, which) -> {
                    loadRoute(finalNames.get(which));
                    showRunRouteDialog(finalNames);
                })
                .setPositiveButton("导入路线", (dialog, which) -> importRouteFromClipboard())
                .setNeutralButton("删除路线", (dialog, which) -> showDeleteRouteDialog(finalNames))
                .setNegativeButton("取消", null)
                .show();
    }

    // 选择路线并直接启动运行
    private void showRunRouteDialog(java.util.List<String> names) {
        org.json.JSONObject routes = getSavedRoutes();
        String[] displayArray = new String[names.size()];
        for (int i = 0; i < names.size(); i++) {
            try {
                org.json.JSONArray pts = routes.getJSONArray(names.get(i));
                double dist = 0;
                for (int j = 1; j < pts.length(); j++) {
                    org.json.JSONObject a = pts.getJSONObject(j-1);
                    org.json.JSONObject b = pts.getJSONObject(j);
                    dist += haversineMeters(a.getDouble("lat"), a.getDouble("lng"),
                            b.getDouble("lat"), b.getDouble("lng"));
                }
                displayArray[i] = names.get(i) + "  (" + pts.length() + "点, " + formatDistance(dist) + ")";
            } catch (Exception e) {
                displayArray[i] = names.get(i);
            }
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择要运行的路线")
                .setItems(displayArray, (dialog, which) -> {
                    loadRoute(names.get(which));
                    if (mRouteLatLngList.size() >= 2) {
                        doGoLocation(mButtonStart);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 加载指定名称的路线到地图（退出采集模式，绘制折线）
    private void loadRoute(String name) {
        try {
            org.json.JSONObject routes = getSavedRoutes();
            org.json.JSONArray points = routes.getJSONArray(name);
            mRouteEditMode = false;
            mRouteLatLngList.clear();
            for (int i = 0; i < points.length(); i++) {
                org.json.JSONObject pt = points.getJSONObject(i);
                mRouteLatLngList.add(new LatLng(pt.getDouble("lat"), pt.getDouble("lng")));
            }
            drawRouteOverlay();
            GoUtils.DisplayToast(this, "路线已加载：" + name
                    + "（" + mRouteLatLngList.size() + "点，" + formatDistance(routeTotalMeters())
                    + "）\n点左下角启动按钮开始运行");
        } catch (Exception e) {
            XLog.e("loadRoute error: " + e.getMessage());
        }
    }

    // 删除路线对话框
    private void showDeleteRouteDialog(java.util.List<String> names) {
        String[] nameArray = names.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.route_delete))
                .setItems(nameArray, (dialog, which) -> deleteRoute(nameArray[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // 删除指定路线
    private void deleteRoute(String name) {
        try {
            org.json.JSONObject routes = getSavedRoutes();
            routes.remove(name);
            sharedPreferences.edit().putString(ROUTE_SP_KEY, routes.toString()).apply();
            GoUtils.DisplayToast(this, getString(R.string.route_deleted) + ": " + name);
        } catch (Exception e) {
            XLog.e("deleteRoute error: " + e.getMessage());
        }
    }

    // 往路线里添加一个途经点，并重绘折线
    private void addRoutePoint(LatLng point) {
        if (mRouteLatLngList.size() >= ROUTE_MAX_POINTS) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_max)
                    + " (" + ROUTE_MAX_POINTS + ")");
            return;
        }
        mRouteLatLngList.add(point);
        drawRouteOverlay();
        updateCollectInfo();   // 实时刷新，不用 Toast
    }

    // 用折线 + 途经点标记 在地图上绘制路线
    private void drawRouteOverlay() {
        clearMap();
        if (mRouteLatLngList.size() >= 2) {
            PolylineOptions polyline = new PolylineOptions()
                    .width(8)
                    .color(0xFF1E88E5)
                    .addAll(mRouteLatLngList);
            mAMap.addPolyline(polyline);
        }
        for (LatLng p : mRouteLatLngList) {
            mAMap.addMarker(new MarkerOptions().position(p).icon(mMapIndicator).anchor(0.5f, 0.5f));
        }
    }

    // 定时刷新路线进度显示（已走 / 剩余距离），并让地图蓝点跟随模拟位置
    private void updateRouteProgress() {
        if (mRouteProgressView == null || mServiceBinder == null) {
            return;
        }
        if (!mServiceBinder.isRouteRunning()) {
            mRouteProgressView.setVisibility(View.GONE);
            if (mRoutePanel != null) {
                mRoutePanel.setVisibility(View.GONE);
            }
            // 路线跑完后恢复底部三按钮
            setBottomBarVisible(true);
            // 重置计时
            mRouteStartTime = 0;
            mPauseAccumMs = 0;
            mLastPauseStart = 0;
            return;
        }
        // 首次检测到运行时记录开始时间
        if (mRouteStartTime == 0) {
            mRouteStartTime = System.currentTimeMillis();
        }
        double total = mServiceBinder.getRouteTotalDistance();
        double remain = mServiceBinder.getRouteRemainingDistance();
        double walked = total - remain;
        if (walked < 0) {
            walked = 0;
        }
        mRouteProgressView.setText("已走 " + formatDistance(walked)
                + " / 剩余 " + formatDistance(remain));
        mRouteProgressView.setVisibility(View.VISIBLE);

        // 更新科技感信息面板
        if (mRoutePanel != null && mRoutePanel.getVisibility() == View.VISIBLE) {
            mPanelWalked.setText(formatDistance(walked));
            mPanelRemain.setText(formatDistance(remain));
            mPanelSpeed.setText(String.format("%.1f", getCurrentRouteSpeed()));
            int pct = 0;
            if (total > 0) {
                pct = (int) Math.min(100, Math.max(0, walked * 100.0 / total));
            }
            mPanelPct.setText(pct + "%");
            mPanelProgress.setProgress(pct);

            // 用时（扣除暂停时长）
            long now = System.currentTimeMillis();
            long activeMs = mRouteStartTime > 0 ? (now - mRouteStartTime - mPauseAccumMs) : 0;
            if (activeMs < 0) activeMs = 0;
            long totalSec = activeMs / 1000;
            mPanelTime.setText(String.format("%02d:%02d", totalSec / 60, totalSec % 60));

            // 配速：分/公里
            if (walked > 0 && totalSec > 0) {
                double paceMinPerKm = (totalSec / 60.0) / (walked / 1000.0);
                int paceM = (int) paceMinPerKm;
                int paceS = (int) Math.round((paceMinPerKm - paceM) * 60);
                if (paceS >= 60) { paceM += 1; paceS = 0; }
                mPanelPace.setText(String.format("%d'%02d\"/km", paceM, paceS));
            } else {
                mPanelPace.setText("0'00\"/km");
            }
        }

        // 让地图蓝点跟随模拟位置移动：把服务的当前位置(wgs84)转成GCJ02更新到地图
        try {
            double[] cur = mServiceBinder.getCurrentPosition();  // {lng, lat} wgs84
            double[] gcj = MapUtils.wgs2gcj02(cur[0], cur[1]);
            updatePosMarker(gcj[1], gcj[0]);
            // 轻微移动地图视角（仅在距离足够远时，避免频繁跳动）
            LatLng curLatLng = new LatLng(gcj[1], gcj[0]);
            LatLng mapCenter = mAMap.getCameraPosition().target;
            double moveDist = haversineMeters(mapCenter.latitude, mapCenter.longitude,
                    curLatLng.latitude, curLatLng.longitude);
            if (moveDist > 30) {
                mAMap.animateCamera(CameraUpdateFactory.newLatLng(curLatLng));
            }
        } catch (Exception e) {
            XLog.e("updateRouteProgress map move error: " + e.getMessage());
        }
    }

    // 将路线点转为 WGS84 并交给服务做路线跟随
    private void startRouteOnService() {
        List<double[]> route = new ArrayList<>();
        for (LatLng p : mRouteLatLngList) {
            double[] latLng = MapUtils.gcj02towgs84(p.longitude, p.latitude);
            route.add(new double[]{latLng[0], latLng[1]});
        }
        double speed = getCurrentRouteSpeed();
        boolean loop = sharedPreferences.getBoolean("setting_route_loop", false);
        mServiceBinder.setRoute(route, speed, loop);
    }

    private void startGoLocation() {
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        bindService(serviceGoIntent, mConnection, BIND_AUTO_CREATE);    // 绑定服务和活动，之后活动就可以去调服务的方法了
        // 若已采集路线，则从路线第一个点开始；否则优先使用当前真实定位位置，定位未完成时用标记点
        if (mRouteLatLngList.isEmpty()) {
            double targetLat, targetLng;
            if (mCurrentLat != 0.0 && mCurrentLon != 0.0) {
                // 使用百度定位到的当前真实位置
                targetLat = mCurrentLat;
                targetLng = mCurrentLon;
                // 同步更新标记点为当前位置
                mMarkLatLngMap = new LatLng(mCurrentLat, mCurrentLon);
            } else {
                targetLat = mMarkLatLngMap.latitude;
                targetLng = mMarkLatLngMap.longitude;
            }
            double[] latLng = MapUtils.gcj02towgs84(targetLng, targetLat);
            serviceGoIntent.putExtra(LNG_MSG_ID, latLng[0]);
            serviceGoIntent.putExtra(LAT_MSG_ID, latLng[1]);
        } else {
            double[] latLng = MapUtils.gcj02towgs84(mRouteLatLngList.get(0).longitude, mRouteLatLngList.get(0).latitude);
            serviceGoIntent.putExtra(LNG_MSG_ID, latLng[0]);
            serviceGoIntent.putExtra(LAT_MSG_ID, latLng[1]);
            mRoutePendingStart = true;    // 服务连接后自动开启路线跟随
        }
        double alt = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
        serviceGoIntent.putExtra(ALT_MSG_ID, alt);

        startForegroundService(serviceGoIntent);
        XLog.d("startForegroundService: ServiceGo");

        isMockServStart = true;
    }

    private void stopGoLocation() {
        unbindService(mConnection); // 解绑服务，服务要记得解绑，不要造成内存泄漏
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        stopService(serviceGoIntent);
        isMockServStart = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean granted = true;
            for (int g : grantResults) {
                if (g != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                GoUtils.DisplayToast(this, "定位权限已授权");
                // 权限就绪后开启定位图层并重新定位
                if (mLocClient != null) {
                    mLocClient.startLocation();
                }
            } else {
                GoUtils.DisplayToast(this, "未授予定位权限，位置功能不可用");
            }
        }
    }

    private void doGoLocation(View v) {
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_network));
            return;
        }

        if (!Settings.canDrawOverlays(getApplicationContext())) {//悬浮窗权限判断
            GoUtils.showEnableFloatWindowDialog(this);
            XLog.e("无悬浮窗权限!");
            return;
        }

        if (isMockServStart) {
            // 停止路线跟随（若正在运行）
            if (mServiceBinder != null && mServiceBinder.isRouteRunning()) {
                mServiceBinder.stopRoute();
            }
            mRouteProgressHandler.removeCallbacks(mRouteProgressRunnable);
            if (mRouteProgressView != null) {
                mRouteProgressView.setVisibility(View.GONE);
            }
            if (mMarkLatLngMap == null) {
                stopGoLocation();
                Snackbar.make(v, "模拟位置已终止", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                mButtonStart.setImageResource(R.drawable.ic_position);
                // 结束路线后恢复显示底部三按钮
                setBottomBarVisible(true);
                // 隐藏科技感信息面板
                if (mRoutePanel != null) {
                    mRoutePanel.setVisibility(View.GONE);
                }
            } else {
                double[] latLng = MapUtils.gcj02towgs84(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                double alt = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
                mServiceBinder.setPosition(latLng[0], latLng[1], alt);
                Snackbar.make(v, "已传送到新位置", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                clearMap();
                mMarkLatLngMap = null;

                if (GoUtils.isWifiEnabled(MainActivity.this)) {
                    GoUtils.showDisableWifiDialog(MainActivity.this);
                }
            }
        } else {
            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this);
                XLog.e("无模拟位置权限!");
            } else {
                // 路线模式：无需标记单点，有路线点即可启动
                if (mRouteLatLngList.isEmpty() && mMarkLatLngMap == null) {
                    Snackbar.make(v, "请先点击地图位置或者采集路线", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else {
                    startGoLocation();
                    mButtonStart.setImageResource(R.drawable.ic_fly);
                    // 路线运行中隐藏底部三按钮
                    setBottomBarVisible(false);
                    // 显示科技感信息面板
                    if (mRoutePanel != null) {
                        mRoutePanel.setVisibility(View.VISIBLE);
                        updatePanelSpeedButtons();
                    }
                    if (mRouteLatLngList.isEmpty()) {
                            Snackbar.make(v, "模拟位置已启动", Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        } else {
                            Snackbar.make(v, "路线移动已启动，总路程 " + formatDistance(routeTotalMeters()),
                                    Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                            // 启动路线进度定时刷新
                            mRouteProgressHandler.removeCallbacks(mRouteProgressRunnable);
                            mRouteProgressHandler.post(mRouteProgressRunnable);
                        }

                    // 路线模式下 mMarkLatLngMap 可能为 null，需判空
                    if (mMarkLatLngMap != null) {
                        recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                        mMarkLatLngMap = null;
                    }
                    // 保留/重绘路线折线，使执行路线时路线可见
                    if (mRouteLatLngList.size() >= 2) {
                        drawRouteOverlay();
                    } else {
                        clearMap();
                    }
                    // 模拟蓝点由 updatePosMarker 维护，无需开启高德原生定位图层

                    if (GoUtils.isWifiEnabled(MainActivity.this)) {
                        GoUtils.showDisableWifiDialog(MainActivity.this);
                    }
                }
            }
        }
    }

    /*============================== 历史记录 相关 ==============================*/
    private void initStoreHistory() {
        try {
            // 定位历史
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
            // 搜索历史
            DataBaseHistorySearch dbHistory = new DataBaseHistorySearch(getApplicationContext());
            mSearchHistoryDB = dbHistory.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    //获取查询历史
    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> searchHistoryItem = new HashMap<>();
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_KEY, cursor.getString(1));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, cursor.getString(2));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + cursor.getInt(3));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + cursor.getInt(4));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, cursor.getString(7));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, cursor.getString(8));
                data.add(searchHistoryItem);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: getSearchHistory");
        }

        return data;
    }

    // 记录请求的位置信息
    private void recordCurrentLocation(double lng, double lat) {
        //参数坐标系：GCJ02（高德）
        final String ak = sharedPreferences.getString("setting_map_key", BuildConfig.MAPS_API_KEY);
        double[] latLng = MapUtils.gcj02towgs84(lng, lat);
        //高德逆地理编码 REST API
        String mapApiUrl = "https://restapi.amap.com/v3/geocode/regeo?key=" + ak
                + "&location=" + lng + "," + lat + "&output=json";

        okhttp3.Request request = new okhttp3.Request.Builder().url(mapApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                //http 请求失败
                XLog.e("HTTP: HTTP GET FAILED");
                //插表参数
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName);
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
                contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));

                DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String resp = responseBody.string();
                    try {
                        JSONObject getRetJson = new JSONObject(resp);

                        if (getRetJson.optString("status", "").equals("1")) { // 位置获取成功
                            JSONObject regeocodeJson = getRetJson.getJSONObject("regeocode");
                            String formatted_address = regeocodeJson.getString("formatted_address");
                            ContentValues contentValues = new ContentValues();
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, formatted_address);
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
                            DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
                        } else {
                            ContentValues contentValues = new ContentValues();
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName == null ? getRetJson.optString("info", "位置获取失败") : mMarkName);
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
                            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
                            DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
                        }
                    } catch (JSONException e) {
                        XLog.e("JSON: resolve json error");
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName == null ? getResources().getString(R.string.history_location_default_name) : mMarkName);
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName);
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
                        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
                        DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
                    }
                }
            }
        });
    }

    /*============================== SearchView 相关 ==============================*/
    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);

        mSearchList = findViewById(R.id.search_list_view);
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
            mAMap.moveCamera(CameraUpdateFactory.newLatLng(mMarkLatLngMap));

            markMap();

            double[] latLng = MapUtils.gcj02towgs84(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

            // mSearchList.setVisibility(View.GONE);
            //搜索历史 插表参数
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            mSearchLayout.setVisibility(View.INVISIBLE);
            searchItem.collapseActionView();
        });
        //搜索历史列表的点击监听
        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        mSearchHistoryList.setOnItemClickListener((parent, view, position, id) -> {
            String searchDescription = ((TextView) view.findViewById(R.id.search_description)).getText().toString();
            String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            String searchIsLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();

            //如果是定位搜索
            if (searchIsLoc.equals("1")) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                // mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
                mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
                mAMap.moveCamera(CameraUpdateFactory.newLatLng(mMarkLatLngMap));

                markMap();

                double[] latLng = MapUtils.gcj02towgs84(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                //设置列表不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);
                searchItem.collapseActionView();
                //更新表
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, searchKey);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, searchDescription);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            } else if (searchIsLoc.equals("0")) { //如果仅仅是搜索
                try {
                    searchView.setQuery(searchKey, true);
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
                    XLog.e(getResources().getString(R.string.app_error_search));
                }
            } else {
                XLog.e(getResources().getString(R.string.app_error_param));
            }
        });
        mSearchHistoryList.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("警告")//这里是表头的内容
                    .setMessage("确定要删除该项搜索记录吗?")//这里是中间显示的具体信息
                    .setPositiveButton("确定",(dialog, which) -> {
                        String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();

                        try {
                            mSearchHistoryDB.delete(DataBaseHistorySearch.TABLE_NAME, DataBaseHistorySearch.DB_COLUMN_KEY + " = ?", new String[] {searchKey});
                            //删除成功
                            //展示搜索历史
                            List<Map<String, Object>> data = getSearchHistory();

                            if (!data.isEmpty()) {
                                SimpleAdapter simAdapt = new SimpleAdapter(
                                        MainActivity.this,
                                        data,
                                        R.layout.search_item,
                                        new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                                DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                                DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                                DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                                DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                                DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM}, // 与下面数组元素要一一对应
                                        new int[] {R.id.search_key, R.id.search_description, R.id.search_timestamp, R.id.search_isLoc, R.id.search_longitude, R.id.search_latitude});
                                mSearchHistoryList.setAdapter(simAdapt);
                                mHistoryLayout.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception e) {
                            XLog.e("ERROR: delete database error");
                            GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.history_delete_error));
                        }
                    })
                    .setNegativeButton("取消",
                            (dialog, which) -> {
                            })
                    .show();
            return true;
        });
    }

    /*============================== 更新 相关 ==============================*/
    private void initUpdateVersion() {
        mDownloadManager =(DownloadManager) MainActivity.this.getSystemService(DOWNLOAD_SERVICE);

        // 用于监听下载完成后，转到安装界面
        mDownloadBdRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                installNewVersion();
            }
        };
        registerReceiver(mDownloadBdRcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void checkUpdateVersion(boolean result) {
        String mapApiUrl = "https://api.github.com/repos/zcshou/gogogo/releases/latest";

        okhttp3.Request request = new okhttp3.Request.Builder().url(mapApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.i("更新检测失败");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String resp = responseBody.string();
                    // 注意，该请求在子线程，不能直接操作界面
                    runOnUiThread(() -> {
                        try {
                            JSONObject getRetJson = new JSONObject(resp);
                            String curVersion = GoUtils.getVersionName(MainActivity.this);

                            if (curVersion != null
                                    && (!getRetJson.getString("name").contains(curVersion)
                                    || !getRetJson.getString("tag_name").contains(curVersion))) {
                                final android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(MainActivity.this).create();
                                alertDialog.show();
                                alertDialog.setCancelable(false);
                                Window window = alertDialog.getWindow();
                                if (window != null) {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);      // 防止出现闪屏
                                    window.setContentView(R.layout.update);
                                    window.setGravity(Gravity.CENTER);
                                    window.setWindowAnimations(R.style.DialogAnimFadeInFadeOut);

                                    TextView updateTitle = window.findViewById(R.id.update_title);
                                    updateTitle.setText(getRetJson.getString("name"));
                                    TextView updateTime = window.findViewById(R.id.update_time);
                                    updateTime.setText(getRetJson.getString("created_at"));
                                    TextView updateCommit = window.findViewById(R.id.update_commit);
                                    updateCommit.setText(getRetJson.getString("target_commitish"));

                                    TextView updateContent = window.findViewById(R.id.update_content);
                                    final Markwon markwon = Markwon.create(MainActivity.this);
                                    markwon.setMarkdown(updateContent, getRetJson.getString("body"));

                                    Button updateCancel = window.findViewById(R.id.update_ignore);
                                    updateCancel.setOnClickListener(v -> alertDialog.cancel());

                                    /* 这里用来保存下载地址 */
                                    JSONArray jsonArray = new JSONArray(getRetJson.getString("assets"));
                                    JSONObject jsonObject = jsonArray.getJSONObject(0);
                                    String download_url = jsonObject.getString("browser_download_url");
                                    mUpdateFilename = jsonObject.getString("name");

                                    Button updateAgree = window.findViewById(R.id.update_agree);
                                    updateAgree.setOnClickListener(v -> {
                                        alertDialog.cancel();
                                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_downloading));
                                        downloadNewVersion(download_url);
                                    });
                                }
                            } else {
                                if (result) {
                                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_last));
                                }
                            }
                        } catch (JSONException e) {
                            XLog.e("ERROR: resolve json");
                        }
                    });
                }
            }
        });
    }

    private void downloadNewVersion(String url) {
        if (mDownloadManager == null) {
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setAllowedOverRoaming(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(GoUtils.getAppName(this));
        request.setDescription("正在下载新版本...");
        request.setMimeType("application/vnd.android.package-archive");

        // DownloadManager不会覆盖已有的同名文件，需要自己来删除已存在的文件
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (file.exists()) {
            if(!file.delete()) {
                return;
            }
        }
        request.setDestinationUri(Uri.fromFile(file));

        mDownloadId = mDownloadManager.enqueue(request);
    }

    private void installNewVersion() {
        Intent install = new Intent(Intent.ACTION_VIEW);
        Uri downloadFileUri = mDownloadManager.getUriForDownloadedFile(mDownloadId);
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (downloadFileUri != null) {
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // 在Broadcast中启动活动需要添加Intent.FLAG_ACTIVITY_NEW_TASK
            install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    //添加这一句表示对目标应用临时授权该Uri所代表的文件
            install.addCategory("android.intent.category.DEFAULT");
            install.setDataAndType(ShareUtils.getUriFromFile(MainActivity.this, file), "application/vnd.android.package-archive");
            startActivity(install);
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            intent.addCategory("android.intent.category.DEFAULT");
            startActivity(intent);
        }
    }
}
