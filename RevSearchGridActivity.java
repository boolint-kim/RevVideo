package com.boolint.camlocation;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import com.boolint.camlocation.bean.CctvItemVo;
import com.boolint.camlocation.helper.ADHelper;
import com.boolint.camlocation.helper.CctvApiHelper;
import com.boolint.camlocation.helper.CctvVideoManager;
import com.boolint.camlocation.helper.DaeguCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.DeviceHelper;
import com.boolint.camlocation.helper.GgCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.SeoulCctvVideoOpenApiHelper;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public class RevSearchGridActivity extends AppCompatActivity {

    private static final String TAG = "RevSearchGridActivity";
    private static final int MESSAGE_CCTV_PLAY = 101;

    private LinearLayout llProgress;
    private ZoomGridView grGridView;

    private ArrayList<CctvItemVo> mList = new ArrayList<>();
    private ArrayList<CctvItemVo> searchList = new ArrayList<>();

    private int mThreadCount = -1;
    private int videoQty = 6;

    private ImageView ivHome;
    private TextView tvTitle;
    private CctvApiHelper apiHelper;

    // 각 아이템의 ExoPlayer 관리
    private SparseArray<ExoPlayer> exoPlayerMap = new SparseArray<>();
    private SparseArray<Surface> surfaceMap = new SparseArray<>();

    // UTIC URL 추출용 CctvVideoManager 관리
    private SparseArray<CctvVideoManager> videoManagerMap = new SparseArray<>();

    // 아이템 클릭 리스너
    private OnGridItemClickListener gridItemClickListener;

    public interface OnGridItemClickListener {
        void onItemClick(CctvItemVo item, int position);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rev_search_grid);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        DeviceHelper.setOrientationPhoneToPortrait(this);

        apiHelper = new CctvApiHelper();

        initializeViews();
        setupGridView();
        setupGridItemClickListener();
        loadNearbyCctvList();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_title);
        ivHome = findViewById(R.id.iv_home);
        llProgress = findViewById(R.id.ll_progress);
        grGridView = findViewById(R.id.gr_grid_view);

        tvTitle.setText(getString(R.string.ab_cctv_nearby_title));
        ivHome.setOnClickListener(v -> onBackPressed());

        llProgress.setVisibility(View.VISIBLE);

        ADHelper.settingAdEx(this);
        ADHelper.loadAdMobInterstitialAd(this);
    }

    private void setupGridView() {
        int numColumns = getGridColumnCount();
        grGridView.setNumColumns(numColumns);
    }

    /**
     * 그리드 아이템 클릭 리스너 설정
     */
    private void setupGridItemClickListener() {
        gridItemClickListener = (item, position) -> {
            Log.d(TAG, "Grid item clicked: " + item.getCctvName() + ", position=" + position);

            // TODO: 클릭 시 동작 구현
            // 예: RevVideoActivity로 이동
            // MainData.mCurrentCctvItemVo = item;
            // Intent intent = new Intent(this, RevVideoActivity.class);
            // startActivity(intent);

            Toast.makeText(this, item.getCctvName() + " 선택됨", Toast.LENGTH_SHORT).show();
        };
    }

    private void loadNearbyCctvList() {
        for (CctvItemVo vo : MainData.mCctvList) {
            if ("busan".equals(vo.getRoadType())) continue;
            if ("daegu".equals(vo.getRoadType())) continue;
            if ("gg".equals(vo.getRoadType())) continue;
            mList.add(vo);
        }

        double x = MainData.mX;
        double y = MainData.mY;

        for (CctvItemVo vo : mList) {
            vo.distance = Math.sqrt((x - vo.coordX) * (x - vo.coordX) +
                    (y - vo.coordY) * (y - vo.coordY));
        }

        Collections.sort(mList, (s, t1) -> {
            if (s.distance > t1.distance) return 1;
            else if (s.distance < t1.distance) return -1;
            else return 0;
        });

        boolean stopPlaying = false;
        int loopCount = Math.min(videoQty, mList.size());
        Double lastDistance = null;

        for (int i = 0; i < mList.size(); i++) {
            if (searchList.size() >= loopCount) {
                break;
            }

            String roadType = mList.get(i).getRoadType();
            if ("jeju".equals(roadType)) {
                stopPlaying = true;
                break;
            }

            double currentDistance = mList.get(i).distance;

            if (lastDistance != null && Math.abs(lastDistance - currentDistance) < 0.00001) {
                continue;
            }

            searchList.add(mList.get(i));
            lastDistance = currentDistance;
        }

        if (stopPlaying) {
            llProgress.setVisibility(View.GONE);
            showJejuWarningDialog();
        } else {
            loadAllCctvVideos();
        }
    }

    private void showJejuWarningDialog() {
        androidx.appcompat.app.AlertDialog.Builder dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this);
        dialog.setTitle("알림");
        dialog.setMessage(getString(R.string.msg_can_not_play_video));
        dialog.setNeutralButton(getString(R.string.msg_close), (dialogInterface, which) -> finish());
        dialog.show();
    }

    private void loadAllCctvVideos() {
        mThreadCount = searchList.size();

        for (int i = 0; i < searchList.size(); i++) {
            CctvItemVo vo = searchList.get(i);
            if (vo != null) {
                loadCctvVideoByType(vo, i);
            }
        }
    }

    private void loadCctvVideoByType(CctvItemVo vo, int position) {
        String roadType = vo.getRoadType();

        switch (roadType) {
            case "seoul":
                loadSeoulCctv(vo);
                break;
            case "jeju":
                loadJejuCctv(vo);
                break;
            case "gg":
                loadGgCctv(vo);
                break;
            case "daegu":
                loadDaeguCctv(vo);
                break;
            case "utic":
                loadUticCctv(vo, position);
                break;
            default:
                loadDefaultCctv(vo);
                break;
        }
    }

    private void loadDefaultCctv(CctvItemVo vo) {
        new Thread(() -> {
            try {
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void loadSeoulCctv(CctvItemVo vo) {
        new Thread(() -> {
            try {
                vo.cctvUrl = SeoulCctvVideoOpenApiHelper.getSeoulCctvUrl(vo.roadSectionId);
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void loadJejuCctv(CctvItemVo vo) {
        new Thread(() -> {
            try {
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void loadGgCctv(CctvItemVo vo) {
        new Thread(() -> {
            try {
                String tempUrl = GgCctvVideoOpenApiHelper.getUrl1(vo.roadSectionId);
                vo.cctvUrl = GgCctvVideoOpenApiHelper.getUrl2(tempUrl);
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void loadDaeguCctv(CctvItemVo vo) {
        new Thread(() -> {
            try {
                vo.cctvUrl = DaeguCctvVideoOpenApiHelper.getUrl(vo.roadSectionId);
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void loadUticCctv(CctvItemVo vo, int position) {
        Log.d(TAG, "🚀 UTIC CCTV 로드: " + vo.roadSectionId + ", position=" + position);

        apiHelper.getCctvInfo(vo.roadSectionId, new CctvApiHelper.CctvResponseListener() {
            @Override
            public void onSuccess(CctvApiHelper.CctvInfo cctvInfo) {
                if (isFinishing() || isDestroyed()) {
                    sendErrorMessage();
                    return;
                }

                Log.d(TAG, "✅ CCTV 정보 받음: " + cctvInfo.toString());
                String pageUrl = cctvInfo.getStreamPageUrl();
                Log.d(TAG, "✅ Stream Page URL: " + pageUrl);

                // CctvVideoManager로 실제 스트림 URL 추출
                CctvVideoManager manager = new CctvVideoManager(getApplicationContext());
                videoManagerMap.put(position, manager);

                manager.extract(pageUrl, new CctvVideoManager.OnVideoReadyListener() {
                    @Override
                    public void onVideoReady(String videoUrl, CctvVideoManager.SourceType sourceType) {
                        Log.d(TAG, "✅ onVideoReady: " + videoUrl + ", position=" + position);
                        vo.cctvUrl = videoUrl;

                        // 즉시 매니저 해제
                        //videoManagerMap.get(position).destroy();
                        //videoManagerMap.remove(position);
                        // ⭐ 즉시 매니저 해제 (WebView 리소스 정리)
                        releaseVideoManager(position);

                        sendSuccessMessage();
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "❌ 영상 추출 실패: " + message);

                        // ⭐ 즉시 매니저 해제 (WebView 리소스 정리)
                        releaseVideoManager(position);

                        sendErrorMessage();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "❌ CCTV 정보 로드 실패: " + error);
                sendErrorMessage();
            }
        });
    }

    private void releaseVideoManager(int position) {
        CctvVideoManager manager = videoManagerMap.get(position);
        if (manager != null) {
            manager.destroy();
            videoManagerMap.remove(position);
            Log.d(TAG, "🗑️ VideoManager 해제: position=" + position);
        }
    }

    private void sendSuccessMessage() {
        handler.obtainMessage(MESSAGE_CCTV_PLAY).sendToTarget();
    }

    private void sendErrorMessage() {
        handler.obtainMessage(-1).sendToTarget();
    }

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            mThreadCount--;

            if (mThreadCount == 0) {
                grGridView.setAdapter(new SearchAdapter(
                        RevSearchGridActivity.this, 0, searchList));
                llProgress.setVisibility(View.GONE);
            }
        }
    };

    private int getGridColumnCount() {
        boolean isTablet = DeviceHelper.isTabletDevice(this);
        boolean isLandscape = DeviceHelper.isLandscapeOrientation(this);

        if (isTablet && isLandscape) {
            return 3;
        } else {
            return 2;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "🔄 onConfigurationChanged: " + newConfig.orientation);

        DeviceHelper.setOrientationPhoneToPortrait(this);

        // 태블릿에서 회전 시 컬럼 수가 변경됨 (2열 ↔ 3열)
        int numColumns = getGridColumnCount();
        grGridView.setNumColumns(numColumns);

        // 회전 시 TextureView의 Surface가 무효화되므로 ExoPlayer 재초기화 필요
        releaseAllExoPlayers();

        // Adapter 재설정하여 플레이어 다시 초기화
        if (!searchList.isEmpty()) {
            grGridView.setAdapter(new SearchAdapter(this, 0, searchList));
        }
    }

    @Override
    public void onBackPressed() {
        releaseAllResources();

        searchList.clear();
        mList.clear();

        if (!BuildConfig.DEBUG) {
            ADHelper.displayInterstitial(this);
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseAllResources();
    }

    private void releaseAllResources() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        releaseAllVideoManagers();
        releaseAllExoPlayers();
    }

    private void releaseAllVideoManagers() {
        for (int i = 0; i < videoManagerMap.size(); i++) {
            CctvVideoManager manager = videoManagerMap.valueAt(i);
            if (manager != null) {
                try {
                    manager.destroy();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing CctvVideoManager", e);
                }
            }
        }
        videoManagerMap.clear();
    }

    private void releaseAllExoPlayers() {
        for (int i = 0; i < exoPlayerMap.size(); i++) {
            ExoPlayer player = exoPlayerMap.valueAt(i);
            if (player != null) {
                try {
                    player.setPlayWhenReady(false);
                    player.stop();
                    player.clearVideoSurface();
                    player.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing ExoPlayer", e);
                }
            }
        }
        exoPlayerMap.clear();

        for (int i = 0; i < surfaceMap.size(); i++) {
            Surface surface = surfaceMap.valueAt(i);
            if (surface != null) {
                try {
                    surface.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing Surface", e);
                }
            }
        }
        surfaceMap.clear();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
        }

        return super.onOptionsItemSelected(item);
    }

    // ============================================================================
    // 한강 SSL 우회 관련 메서드
    // ============================================================================

    @OptIn(markerClass = UnstableApi.class)
    private MediaSource createMediaSource(String url) {
        DataSource.Factory dataSourceFactory = createDataSourceFactory(url);
        MediaItem mediaItem = MediaItem.fromUri(url);
        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".m3u")) {
            Log.d(TAG, "🎬 HLS 모드");
            return new HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem);
        } else {
            Log.d(TAG, "🎬 Progressive 모드 (MP4)");
            return new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private DataSource.Factory createDataSourceFactory(String url) {
        if (url.contains("hrfco.go.kr")) {
            Log.d(TAG, "🌊 한강 모드 (SSL 우회 + Origin 헤더)");
            return createHrfcoDataSourceFactory();
        } else {
            Log.d(TAG, "📡 일반 모드");
            return createDefaultDataSourceFactory();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private OkHttpDataSource.Factory createHrfcoDataSourceFactory() {
        OkHttpClient client = createUnsafeOkHttpClient();
        OkHttpDataSource.Factory factory = new OkHttpDataSource.Factory(client);

        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "http://hrfco.go.kr");
        headers.put("Referer", "http://hrfco.go.kr/");
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36");
        factory.setDefaultRequestProperties(headers);

        return factory;
    }

    @OptIn(markerClass = UnstableApi.class)
    private DefaultHttpDataSource.Factory createDefaultDataSourceFactory() {
        DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory();
        factory.setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36");
        factory.setAllowCrossProtocolRedirects(true);
        factory.setConnectTimeoutMs(15000);
        factory.setReadTimeoutMs(15000);
        return factory;
    }

    private OkHttpClient createUnsafeOkHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================================
    // SearchAdapter
    // ============================================================================

    private class SearchAdapter extends ArrayAdapter<CctvItemVo> {

        public ArrayList<CctvItemVo> items;
        private SparseBooleanArray initializedPositions = new SparseBooleanArray();

        // 클릭 감지를 위한 변수
        private static final int CLICK_THRESHOLD = 200; // ms
        private static final int MOVE_THRESHOLD = 20; // pixels

        public SearchAdapter(Context context, int textViewResourceId, ArrayList<CctvItemVo> objects) {
            super(context, textViewResourceId, objects);
            this.items = objects;
        }

        @Override
        public CctvItemVo getItem(int position) {
            try {
                return items.get(position);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.items_search_grid, parent, false);

                holder = new ViewHolder();
                holder.llBase = convertView.findViewById(R.id.ll_base);
                holder.frameVideoContainer = convertView.findViewById(R.id.frame_video_container);
                holder.textureView = convertView.findViewById(R.id.textureView);
                holder.tvTitle = convertView.findViewById(R.id.tvTitle);
                holder.imgCctvType = convertView.findViewById(R.id.imgCctvType);
                holder.llProgress = convertView.findViewById(R.id.ll_progress);
                holder.llError = convertView.findViewById(R.id.ll_error);
                holder.tvErrorMessage = convertView.findViewById(R.id.tv_error_message);
                holder.llFavor = convertView.findViewById(R.id.llFavor);
                holder.imgFavor = convertView.findViewById(R.id.imgFavor);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();

                if (holder.position == position && initializedPositions.get(position, false)) {
                    return convertView;
                }

                if (holder.position != position) {
                    cleanupViewHolder(holder);
                }
            }

            CctvItemVo vo = items.get(position);
            holder.position = position;

            if (initializedPositions.get(position, false)) {
                updateUIOnly(holder, vo);
                return convertView;
            }

            // 초기 상태
            holder.llProgress.setAlpha(1f);
            holder.llProgress.setVisibility(View.VISIBLE);
            holder.llError.setVisibility(View.GONE);
            holder.textureView.setVisibility(View.GONE);

            // UI 설정
            updateUIOnly(holder, vo);

            // 클릭 이벤트 설정 (핀치줌/팬과 공존)
            setupClickListener(holder, vo, position);

            // 즐겨찾기 클릭
            holder.llFavor.setOnClickListener(v -> {
                boolean currentFavor = Utils.existFavor(RevSearchGridActivity.this, vo.getRoadType(), vo.getCctvName());
                String msg;

                if (currentFavor) {
                    Utils.removeFavor(getApplicationContext(), vo.getRoadType(), vo.getCctvName());
                    holder.imgFavor.setImageResource(R.drawable.favor_off);
                    msg = vo.getCctvName() + getString(R.string.msg_delete_favor);
                } else {
                    Utils.addFavor(getApplicationContext(), vo);
                    holder.imgFavor.setImageResource(R.drawable.favor_on);
                    msg = vo.getCctvName() + getString(R.string.msg_add_favor);
                }

                Toast.makeText(RevSearchGridActivity.this, msg, Toast.LENGTH_SHORT).show();
            });

            // URL 체크
            if (vo.getCctvUrl() == null || vo.getCctvUrl().isEmpty()) {
                showError(holder, "URL 없음");
                initializedPositions.put(position, true);
                return convertView;
            }

            initializedPositions.put(position, true);

            // 순차적 로딩
            int delay = position * 300;

            Log.d(TAG, "Scheduling player init: position=" + position + ", delay=" + delay);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (holder.position != position) {
                    return;
                }
                setupExoPlayer(holder, vo, position);
            }, delay);

            return convertView;
        }

        /**
         * 클릭 리스너 설정 (핀치줌/팬과 공존)
         */
        private void setupClickListener(ViewHolder holder, CctvItemVo vo, int position) {
            holder.frameVideoContainer.setOnTouchListener(new View.OnTouchListener() {
                private long touchStartTime;
                private float touchStartX, touchStartY;
                private boolean isMoved = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            touchStartTime = System.currentTimeMillis();
                            touchStartX = event.getX();
                            touchStartY = event.getY();
                            isMoved = false;
                            // 터치 이벤트를 부모(ZoomGridView)에게도 전달
                            return false;

                        case MotionEvent.ACTION_MOVE:
                            float dx = Math.abs(event.getX() - touchStartX);
                            float dy = Math.abs(event.getY() - touchStartY);
                            if (dx > MOVE_THRESHOLD || dy > MOVE_THRESHOLD) {
                                isMoved = true;
                            }
                            return false;

                        case MotionEvent.ACTION_UP:
                            long touchDuration = System.currentTimeMillis() - touchStartTime;

                            // 짧은 터치 + 이동 없음 = 클릭
                            if (touchDuration < CLICK_THRESHOLD && !isMoved) {
                                if (gridItemClickListener != null) {
                                    gridItemClickListener.onItemClick(vo, position);
                                }
                                return true;
                            }
                            return false;

                        default:
                            return false;
                    }
                }
            });
        }

        private void updateUIOnly(ViewHolder holder, CctvItemVo vo) {
            if ("ex".equals(vo.getRoadType())) {
                holder.imgCctvType.setImageResource(R.drawable.cctvex32);
            } else if ("its".equals(vo.getRoadType())) {
                holder.imgCctvType.setImageResource(R.drawable.cctvits32);
            } else {
                holder.imgCctvType.setImageResource(R.drawable.cctvits32);
            }

            holder.tvTitle.setText(vo.cctvName);

            boolean isFavor = Utils.existFavor(RevSearchGridActivity.this, vo.getRoadType(), vo.getCctvName());
            holder.imgFavor.setImageResource(isFavor ? R.drawable.favor_on : R.drawable.favor_off);
        }

        private void cleanupViewHolder(ViewHolder holder) {
            try {
                int oldPosition = holder.position;

                ExoPlayer oldPlayer = exoPlayerMap.get(oldPosition);
                if (oldPlayer != null) {
                    oldPlayer.stop();
                    oldPlayer.clearVideoSurface();
                    oldPlayer.release();
                    exoPlayerMap.remove(oldPosition);
                }

                Surface oldSurface = surfaceMap.get(oldPosition);
                if (oldSurface != null) {
                    oldSurface.release();
                    surfaceMap.remove(oldPosition);
                }

                initializedPositions.delete(oldPosition);

            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up ViewHolder", e);
            }
        }

        @OptIn(markerClass = UnstableApi.class)
        private void setupExoPlayer(ViewHolder holder, CctvItemVo vo, int position) {
            Log.d(TAG, "setupExoPlayer: position=" + position);

            holder.textureView.setVisibility(View.VISIBLE);

            ExoPlayer exoPlayer = new ExoPlayer.Builder(RevSearchGridActivity.this).build();
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            exoPlayer.setVolume(0f);

            exoPlayerMap.put(position, exoPlayer);

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    fitVideoToTextureView(holder.textureView, position);
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        holder.textureView.setVisibility(View.VISIBLE);
                        hideProgress(holder);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        hideProgress(holder);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "onPlayerError: " + error.getMessage());
                    showError(holder, "연결 오류");
                }
            });

            holder.textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                    Surface surface = new Surface(surfaceTexture);
                    surfaceMap.put(position, surface);

                    ExoPlayer player = exoPlayerMap.get(position);
                    if (player != null) {
                        player.setVideoSurface(surface);
                        prepareAndPlay(player, vo);
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    fitVideoToTextureView(holder.textureView, position);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });

            if (holder.textureView.isAvailable()) {
                SurfaceTexture surfaceTexture = holder.textureView.getSurfaceTexture();
                if (surfaceTexture != null) {
                    Surface surface = new Surface(surfaceTexture);
                    surfaceMap.put(position, surface);
                    exoPlayer.setVideoSurface(surface);
                    prepareAndPlay(exoPlayer, vo);
                }
            }
        }

        @OptIn(markerClass = UnstableApi.class)
        private void prepareAndPlay(ExoPlayer player, CctvItemVo vo) {
            try {
                String url = vo.getCctvUrl();
                Log.d(TAG, "prepareAndPlay: url=" + url);

                MediaSource mediaSource = createMediaSource(url);
                player.setMediaSource(mediaSource);
                player.prepare();
                player.play();

            } catch (Exception e) {
                Log.e(TAG, "prepareAndPlay error", e);
            }
        }

        private void fitVideoToTextureView(TextureView textureView, int position) {
            ExoPlayer player = exoPlayerMap.get(position);
            if (player == null) return;

            VideoSize videoSize = player.getVideoSize();
            if (videoSize.width == 0 || videoSize.height == 0) return;

            int viewWidth = textureView.getWidth();
            int viewHeight = textureView.getHeight();

            if (viewWidth <= 0 || viewHeight <= 0) return;

            float videoAspect = (float) videoSize.width / videoSize.height;
            float viewAspect = (float) viewWidth / viewHeight;

            Matrix matrix = new Matrix();
            float scaleX, scaleY;
            float cx = viewWidth / 2f;
            float cy = viewHeight / 2f;

            if (videoAspect > viewAspect) {
                scaleX = 1.0f;
                scaleY = viewAspect / videoAspect;
            } else {
                scaleX = videoAspect / viewAspect;
                scaleY = 1.0f;
            }
            matrix.setScale(scaleX, scaleY, cx, cy);

            textureView.setTransform(matrix);
        }

        private void hideProgress(ViewHolder holder) {
            if (holder.llProgress.getVisibility() == View.VISIBLE) {
                holder.llProgress.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            holder.llProgress.setVisibility(View.GONE);
                            holder.llProgress.setAlpha(1f);
                        })
                        .start();
            }
        }

        private void showError(ViewHolder holder, String message) {
            holder.llProgress.clearAnimation();
            holder.llProgress.setVisibility(View.GONE);

            holder.tvErrorMessage.setText(message);
            holder.llError.setVisibility(View.VISIBLE);
        }

        class ViewHolder {
            int position = -1;
            LinearLayout llBase;
            FrameLayout frameVideoContainer;
            TextureView textureView;
            TextView tvTitle;
            ImageView imgCctvType;
            LinearLayout llProgress;
            LinearLayout llError;
            TextView tvErrorMessage;
            LinearLayout llFavor;
            ImageView imgFavor;
        }
    }
}
