package com.boolint.camlocation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import com.boolint.camlocation.bean.CctvItemVo;
import com.boolint.camlocation.helper.ADHelper;
import com.boolint.camlocation.helper.CctvApiHelper;
import com.boolint.camlocation.helper.DaeguCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.DeviceHelper;
import com.boolint.camlocation.helper.GgCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.SeoulCctvVideoOpenApiHelper;

import java.util.ArrayList;
import java.util.Collections;

public class RevSearchGridActivity extends AppCompatActivity {

    private static final String TAG = "TestSearchGridActivity";

    private static final int MESSAGE_CCTV_EXOPLAYER = 101;
    private static final int MESSAGE_CCTV_WEBVIEW = 102;

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

    private void loadNearbyCctvList() {
        for (CctvItemVo vo : MainData.mCctvList) {
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

        for (CctvItemVo vo : searchList) {
            if (vo != null) {
                loadCctvVideoByType(vo);
            }
        }
    }

    private void loadCctvVideoByType(CctvItemVo vo) {
        String roadType = vo.getRoadType();

        switch (roadType) {
            case "seoul":
                startSeoulCctvVideo(vo);
                break;
            case "jeju":
                startJejuCctvVideo(vo);
                break;
            case "gg":
                startGgCctvVideo(vo);
                break;
            case "daegu":
                startDaeguCctvVideo(vo);
                break;
            case "utic":
                startUticCctvVideoWithApi(vo);
                break;
            default:
                startCctvVideo(vo);
                break;
        }
    }

    private void startCctvVideo(CctvItemVo vo) {
        new Thread(() -> {
            try {
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startSeoulCctvVideo(CctvItemVo vo) {
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

    private void startJejuCctvVideo(CctvItemVo vo) {
        new Thread(() -> {
            try {
                sendSuccessMessage();
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startGgCctvVideo(CctvItemVo vo) {
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

    private void startDaeguCctvVideo(CctvItemVo vo) {
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

    private void startUticCctvVideoWithApi(CctvItemVo vo) {
        apiHelper.getCctvInfo(vo.roadSectionId, new CctvApiHelper.CctvResponseListener() {
            @Override
            public void onSuccess(CctvApiHelper.CctvInfo cctvInfo) {
                vo.cctvUrl = cctvInfo.getStreamPageUrl();
                vo.isWebViewPlayer = true;
                sendSuccessMessage();
            }

            @Override
            public void onFailure(String error) {
                sendErrorMessage();
            }
        });
    }

    private void sendSuccessMessage() {
        Message msg = handler.obtainMessage();
        msg.what = 100;
        handler.sendMessage(msg);
    }

    private void sendErrorMessage() {
        Message msg = handler.obtainMessage();
        msg.what = -100;
        handler.sendMessage(msg);
    }

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 100 || msg.what == -100) {
                mThreadCount--;
            }

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
        DeviceHelper.setOrientationPhoneToPortrait(this);

        int numColumns = getGridColumnCount();
        grGridView.setNumColumns(numColumns);
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

        releaseAllExoPlayers();
        releaseGridViewResources();
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

    private void releaseGridViewResources() {
        if (grGridView == null) return;

        try {
            int childCount = grGridView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View itemView = grGridView.getChildAt(i);
                if (itemView == null) continue;

                WebView webView = itemView.findViewById(R.id.webview);
                if (webView != null) {
                    webView.onPause();
                    webView.loadUrl("about:blank");
                    webView.stopLoading();
                    webView.clearHistory();
                    webView.clearCache(true);
                    webView.removeAllViews();
                    webView.destroy();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing GridView resources", e);
        }
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
    // SearchAdapter
    // ============================================================================

    private class SearchAdapter extends ArrayAdapter<CctvItemVo> {

        public ArrayList<CctvItemVo> items;

        // ✅ 각 position별 초기화 완료 여부 추적
        private SparseBooleanArray initializedPositions = new SparseBooleanArray();

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
                holder.webView = convertView.findViewById(R.id.webview);
                holder.webviewOverlay = convertView.findViewById(R.id.webviewOverlay);
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

                // ✅ 같은 position이면 재초기화하지 않음
                if (holder.position == position && initializedPositions.get(position, false)) {
                    Log.d(TAG, "Skip re-init: position=" + position);
                    return convertView;
                }

                // 다른 position이면 이전 리소스 정리
                if (holder.position != position) {
                    cleanupViewHolder(holder);
                }
            }

            CctvItemVo vo = items.get(position);
            holder.position = position;

            // ✅ 이미 초기화된 position이면 스킵
            if (initializedPositions.get(position, false)) {
                Log.d(TAG, "Already initialized: position=" + position);
                updateUIOnly(holder, vo);
                return convertView;
            }

            // 터치 이벤트 부모로 전달
            holder.frameVideoContainer.setClickable(false);
            holder.frameVideoContainer.setFocusable(false);
            holder.frameVideoContainer.setOnTouchListener((v, event) -> false);

            // 초기 상태
            holder.llProgress.setAlpha(1f);
            holder.llProgress.setVisibility(View.VISIBLE);
            holder.llError.setVisibility(View.GONE);
            holder.textureView.setVisibility(View.GONE);
            holder.webView.setVisibility(View.GONE);
            holder.webviewOverlay.setVisibility(View.GONE);

            // UI 설정
            updateUIOnly(holder, vo);

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

            // ✅ 초기화 완료 표시 (플레이어 설정 전에)
            initializedPositions.put(position, true);

            // ✅ 순차적 로딩
            int delay = position * 300;

            boolean useWebView = "utic".equals(vo.getRoadType()) || vo.isWebViewPlayer;

            Log.d(TAG, "Scheduling player init: position=" + position + ", delay=" + delay + ", useWebView=" + useWebView);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // ✅ View가 아직 유효한지 확인
                if (holder.position != position) {
                    Log.d(TAG, "Position changed, skip: " + position);
                    return;
                }

                if (useWebView) {
                    setupWebViewPlayer(holder, vo, position);
                } else {
                    setupExoPlayer(holder, vo, position);
                }
            }, delay);

            return convertView;
        }

        /**
         * UI만 업데이트 (플레이어 재초기화 없이)
         */
        private void updateUIOnly(ViewHolder holder, CctvItemVo vo) {
            // CCTV 타입 아이콘
            if ("ex".equals(vo.getRoadType())) {
                holder.imgCctvType.setImageResource(R.drawable.cctvex32);
            } else if ("its".equals(vo.getRoadType())) {
                holder.imgCctvType.setImageResource(R.drawable.cctvits32);
            } else {
                holder.imgCctvType.setImageResource(R.drawable.cctvits32);
            }

            holder.tvTitle.setText(vo.cctvName);

            // 즐겨찾기 상태
            boolean isFavor = Utils.existFavor(RevSearchGridActivity.this, vo.getRoadType(), vo.getCctvName());
            holder.imgFavor.setImageResource(isFavor ? R.drawable.favor_on : R.drawable.favor_off);
        }

        private void cleanupViewHolder(ViewHolder holder) {
            try {
                int oldPosition = holder.position;

                // 이전 ExoPlayer 정리
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

                // WebView 정리
                if (holder.webView != null) {
                    holder.webView.onPause();
                    holder.webView.stopLoading();
                    holder.webView.loadUrl("about:blank");
                }

                // 초기화 상태 제거
                initializedPositions.delete(oldPosition);

            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up ViewHolder", e);
            }
        }

        private void setupExoPlayer(ViewHolder holder, CctvItemVo vo, int position) {
            Log.d(TAG, "setupExoPlayer: position=" + position);

            holder.textureView.setVisibility(View.VISIBLE);
            holder.webView.setVisibility(View.GONE);
            holder.webviewOverlay.setVisibility(View.GONE);

            holder.textureView.setClickable(false);
            holder.textureView.setFocusable(false);
            holder.textureView.setOnTouchListener((v, event) -> false);

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
                    Log.d(TAG, "onPlaybackStateChanged: state=" + state + ", position=" + position);

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
                    Log.d(TAG, "onSurfaceTextureAvailable: position=" + position);

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

        private void prepareAndPlay(ExoPlayer player, CctvItemVo vo) {
            try {
                Log.d(TAG, "prepareAndPlay: url=" + vo.getCctvUrl());

                Uri videoUri = Uri.parse(vo.getCctvUrl());
                MediaItem mediaItem;

                if (videoUri.getLastPathSegment() != null &&
                        (videoUri.getLastPathSegment().contains(".m3u") ||
                                videoUri.getLastPathSegment().contains(".m3u8"))) {
                    mediaItem = new MediaItem.Builder()
                            .setUri(videoUri)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build();
                } else {
                    mediaItem = MediaItem.fromUri(videoUri);
                }

                player.setMediaItem(mediaItem);
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

        @SuppressLint("SetJavaScriptEnabled")
        private void setupWebViewPlayer(ViewHolder holder, CctvItemVo vo, int position) {
            Log.d(TAG, "setupWebViewPlayer: position=" + position);

            holder.textureView.setVisibility(View.GONE);
            holder.webView.setVisibility(View.VISIBLE);
            holder.webviewOverlay.setAlpha(1f);
            holder.webviewOverlay.setVisibility(View.VISIBLE);

            WebSettings ws = holder.webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            //ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
            //ws.setDatabaseEnabled(true);

            // ✅ 캐시 사용으로 변경 (빠른 로딩)
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);
            ws.setDatabaseEnabled(true);

            holder.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
            }

            ws.setLoadsImagesAutomatically(true);
            ws.setBlockNetworkImage(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }

            ws.setSupportZoom(false);
            ws.setBuiltInZoomControls(false);
            ws.setDisplayZoomControls(false);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);

            holder.webView.setVerticalScrollBarEnabled(false);
            holder.webView.setHorizontalScrollBarEnabled(false);
            holder.webView.setClickable(false);
            holder.webView.setFocusable(false);
            holder.webView.setFocusableInTouchMode(false);
            holder.webView.setOnTouchListener((v, event) -> false);
            holder.webView.setBackgroundColor(0xFF000000);

            holder.webView.addJavascriptInterface(new WebAppInterface(holder, position), "AndroidBridge");

            holder.webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public View getVideoLoadingProgressView() {
                    return new View(RevSearchGridActivity.this);
                }

                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (newProgress <= 10) {
                        injectAllScripts(view);
                    }
                }
            });

            holder.webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();

                    // 같은 페이지 리로드 차단
                    if (url.equals(view.getUrl())) {
                        Log.d("ttt", "자동 새로고침 차단: " + url);
                        return true; // 리로드 방지
                    }
                    return false;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    // 같은 URL 리로드 차단
                    if (url.equals(view.getUrl())) {
                        Log.d("ttt", "자동 새로고침 차단: " + url);
                        return true;
                    }
                    return false;
                }
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    Log.d(TAG, "WebView onPageStarted: position=" + position);
                    injectBaseCSSImmediately(view);
                    injectAllScripts(view);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    Log.d(TAG, "WebView onPageFinished: position=" + position);
                    injectAllScripts(view);

                    view.postDelayed(() -> injectAllScripts(view), 300);
                    view.postDelayed(() -> injectAllScripts(view), 800);

                    // 타임아웃
                    view.postDelayed(() -> {
                        if (holder.llProgress.getVisibility() == View.VISIBLE) {
                            hideProgress(holder);
                        }
                    }, 8000);
                }

                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                    handler.proceed();
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    if (request.isForMainFrame()) {
                        showError(holder, "로드 실패");
                    }
                }
            });

            holder.webView.loadUrl(vo.getCctvUrl());
        }

        private void injectBaseCSSImmediately(WebView view) {
            String js =
                    "javascript:(function(){ " +
                            "if(!document.getElementById('cctv-base-style')){ " +
                            "var s=document.createElement('style');" +
                            "s.id='cctv-base-style';" +
                            "s.innerHTML='" +
                            "* { margin:0; padding:0; box-sizing:border-box; }" +
                            "html, body { width:100%; height:100%; margin:0; padding:0; background:#000!important; overflow:hidden!important; touch-action:none!important; }" +
                            "video { position:absolute!important; top:50%!important; left:50%!important; transform:translate(-50%,-50%)!important; max-width:100%!important; max-height:100%!important; width:100%!important; height:100%!important; object-fit:contain!important; background:black!important; pointer-events:none!important; touch-action:none!important; }" +
                            "video::-webkit-media-controls { display:none!important; }" +
                            "video::-webkit-media-controls-panel { display:none!important; }" +
                            "video::-webkit-media-controls-play-button { display:none!important; }" +
                            "video::-webkit-media-controls-start-playback-button { display:none!important; opacity:0!important; }" +
                            "video::-webkit-media-controls-overlay-play-button { display:none!important; opacity:0!important; }" +
                            "';" +
                            "document.head.appendChild(s);" +
                            "}})();";

            view.evaluateJavascript(js, null);
        }

        private void injectAllScripts(WebView view) {
            String js =
                    "javascript:(function(){ " +
                            "if(!document.getElementById('cctv-base-style')){ " +
                            "var s=document.createElement('style');" +
                            "s.id='cctv-base-style';" +
                            "s.innerHTML='" +
                            "* { margin:0; padding:0; box-sizing:border-box; }" +
                            "html, body { width:100%; height:100%; margin:0; padding:0; background:#000!important; overflow:hidden!important; touch-action:none!important; }" +
                            "video { position:absolute!important; top:50%!important; left:50%!important; transform:translate(-50%,-50%)!important; max-width:100%!important; max-height:100%!important; width:100%!important; height:100%!important; object-fit:contain!important; background:black!important; pointer-events:none!important; touch-action:none!important; }" +
                            "video::-webkit-media-controls { display:none!important; }" +
                            "video::-webkit-media-controls-panel { display:none!important; }" +
                            "video::-webkit-media-controls-play-button { display:none!important; }" +
                            "video::-webkit-media-controls-start-playback-button { display:none!important; opacity:0!important; }" +
                            "video::-webkit-media-controls-overlay-play-button { display:none!important; opacity:0!important; }" +
                            "';" +
                            "(document.head||document.documentElement).appendChild(s);" +
                            "}" +

                            "document.querySelectorAll('video').forEach(function(v){ " +
                            "v.controls=false;" +
                            "v.removeAttribute('controls');" +
                            "v.removeAttribute('poster');" +
                            "v.poster='';" +
                            "v.autoplay=true;" +
                            "v.muted=true;" +
                            "v.playsInline=true;" +
                            "v.setAttribute('playsinline','');" +
                            "v.setAttribute('webkit-playsinline','');" +
                            "v.style.pointerEvents='none';" +
                            "v.style.touchAction='none';" +
                            "v.play().catch(function(e){});" +

                            "if(!v.hasPlayingListener){" +
                            "v.hasPlayingListener=true;" +

                            "v.addEventListener('playing', function(){ " +
                            "if(window.AndroidBridge) window.AndroidBridge.onVideoPlaying(); " +
                            "}, {once: true});" +

                            "v.addEventListener('canplay', function(){ " +
                            "if(window.AndroidBridge) window.AndroidBridge.onVideoPlaying(); " +
                            "}, {once: true});" +

                            "v.addEventListener('loadeddata', function(){ " +
                            "if(window.AndroidBridge) window.AndroidBridge.onVideoPlaying(); " +
                            "}, {once: true});" +

                            "}" +
                            "});" +
                            "})();";

            view.evaluateJavascript(js, null);
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

            if (holder.webviewOverlay != null && holder.webviewOverlay.getVisibility() == View.VISIBLE) {
                holder.webviewOverlay.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            holder.webviewOverlay.setVisibility(View.GONE);
                            holder.webviewOverlay.setAlpha(1f);
                        })
                        .start();
            }
        }

        private void showError(ViewHolder holder, String message) {
            holder.llProgress.clearAnimation();
            holder.llProgress.setVisibility(View.GONE);

            if (holder.webviewOverlay != null) {
                holder.webviewOverlay.clearAnimation();
                holder.webviewOverlay.setVisibility(View.GONE);
            }

            holder.tvErrorMessage.setText(message);
            holder.llError.setVisibility(View.VISIBLE);
        }

        class WebAppInterface {
            private ViewHolder holder;
            private int position;

            WebAppInterface(ViewHolder holder, int position) {
                this.holder = holder;
                this.position = position;
            }

            @android.webkit.JavascriptInterface
            public void onVideoPlaying() {
                Log.d(TAG, "WebAppInterface.onVideoPlaying: position=" + position);
                runOnUiThread(() -> hideProgress(holder));
            }
        }

        class ViewHolder {
            int position = -1;
            LinearLayout llBase;
            FrameLayout frameVideoContainer;
            TextureView textureView;
            WebView webView;
            View webviewOverlay;
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
