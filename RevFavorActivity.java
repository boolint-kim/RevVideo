package com.boolint.camlocation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.boolint.camlocation.bean.CctvItemVo;
import com.boolint.camlocation.bean.FavorItemVo;
import com.boolint.camlocation.helper.ADHelper;
import com.boolint.camlocation.helper.CctvApiHelper;
import com.boolint.camlocation.helper.DaeguCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.DeviceHelper;
import com.boolint.camlocation.helper.GgCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.JejuCctvVideoOpenApiHelper;
import com.boolint.camlocation.helper.SeoulCctvVideoOpenApiHelper;

import java.util.ArrayList;

public class RevFavorActivity extends AppCompatActivity {

    private static final String TAG = "TestFavorActivity";
    private static final int MESSAGE_CCTV_EXOPLAYER = 101;
    private static final int MESSAGE_CCTV_WEBVIEW = 102;

    // Player 타입 정의
    private enum PlayerType {
        EXOPLAYER,
        WEBVIEW
    }

    private PlayerType currentPlayerType = PlayerType.EXOPLAYER;

    // Views
    private LinearLayout llVideo;
    private TextureView textureView;
    private WebView webView;
    private View webviewOverlay;
    private ExoPlayer exoPlayer;
    private Surface videoSurface;

    private ListView lvFavor;
    private LinearLayout llRegFavor;
    private TextView tvCopyRight;
    private LinearLayout llProgress;
    private LinearLayout llError;
    private TextView tvErrorMessage;
    private TextView tvErrorDetail;
    private TextView tvSelectItem;
    private ImageView ivHome;
    private TextView tvTitle;

    // 비디오 크기 저장용
    private int currentVideoWidth = 0;
    private int currentVideoHeight = 0;

    // Data
    private ArrayList<FavorItemVo> favorList;
    private CctvApiHelper apiHelper;
    private CctvItemVo mCurrentCctvItem;

    // Handlers
    private Handler webViewTimeoutHandler;
    private Runnable webViewTimeoutRunnable;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    private boolean isVideoPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rev_favor);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        DeviceHelper.setOrientationPhoneToPortrait(this);

        ADHelper.settingAdEx(this);
        ADHelper.loadAdMobInterstitialAd(this);

        apiHelper = new CctvApiHelper();

        initializeViews();
        setupExoPlayer();
        setupWebView();
        setupListView();
    }

    private void initializeViews() {
        llVideo = findViewById(R.id.llVideo);
        llRegFavor = findViewById(R.id.llRegFavor);
        tvCopyRight = findViewById(R.id.tvCopyRight);
        textureView = findViewById(R.id.textureView);
        webView = findViewById(R.id.webview);
        webviewOverlay = findViewById(R.id.webviewOverlay);
        lvFavor = findViewById(R.id.lvFavor);
        llProgress = findViewById(R.id.ll_progress);
        llError = findViewById(R.id.ll_error);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        tvErrorDetail = findViewById(R.id.tv_error_detail);
        tvSelectItem = findViewById(R.id.tv_select_item);
        tvTitle = findViewById(R.id.tv_title);
        ivHome = findViewById(R.id.iv_home);

        tvTitle.setText(getString(R.string.title_favorite));
        ivHome.setOnClickListener(v -> onBackPressed());

        lvFavor.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        // 초기 상태 설정
        textureView.setVisibility(View.INVISIBLE);
        webView.setVisibility(View.GONE);
        webviewOverlay.setVisibility(View.GONE);
        tvSelectItem.setVisibility(View.VISIBLE);
    }

    private void setupExoPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (videoSurface != null) {
                    videoSurface.release();
                }
                videoSurface = new Surface(surface);
                exoPlayer.setVideoSurface(videoSurface);
                applyVideoFit();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                applyVideoFit();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                try {
                    if (exoPlayer != null) {
                        exoPlayer.clearVideoSurface();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing video surface", e);
                }

                if (videoSurface != null) {
                    try {
                        videoSurface.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing surface", e);
                    } finally {
                        videoSurface = null;
                    }
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        if (textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null) {
                videoSurface = new Surface(surfaceTexture);
                exoPlayer.setVideoSurface(videoSurface);
            }
        }

        setupExoPlayerListeners();
    }

    private void setupExoPlayerListeners() {
        final int TIMEOUT_MS = 10000;
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutRunnable = () -> {
            if (exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
                llProgress.setVisibility(View.GONE);
                showError("재생 시간 초과", "네트워크 연결을 확인해주세요");
                exoPlayer.stop();
            }
        };

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                currentVideoWidth = videoSize.width;
                currentVideoHeight = videoSize.height;
                fitVideoToView(videoSize);
                textureView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
                } else {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                }

                if (state == Player.STATE_READY) {
                    isVideoPlaying = true;
                    textureView.setVisibility(View.VISIBLE);
                    hideProgressWithAnimation();
                    hideError();
                } else if (state == Player.STATE_ENDED) {
                    Log.d(TAG, "STATE_ENDED");
                } else {
                    textureView.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    isVideoPlaying = true;
                    hideProgressWithAnimation();
                    hideError();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                llProgress.setVisibility(View.GONE);
                showError("영상 연결 오류", "CCTV를 불러올 수 없습니다");
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webViewTimeoutHandler = new Handler(Looper.getMainLooper());

        WebSettings ws = webView.getSettings();

        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setDatabaseEnabled(true);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
        }

        ws.setLoadsImagesAutomatically(true);
        ws.setBlockNetworkImage(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setBackgroundColor(0xFF000000);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public View getVideoLoadingProgressView() {
                return new View(RevFavorActivity.this);
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress <= 1) {
                    injectAllScripts(view);
                }
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                result.confirm();
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.equals(view.getUrl())) {
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                cancelWebViewTimeout();
                injectBaseCSSImmediately(view);
                injectAllScripts(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectAllScripts(view);
                view.postDelayed(() -> injectAllScripts(view), 300);
                startWebViewTimeout();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    llProgress.setVisibility(View.GONE);
                    showError("페이지 로드 실패", "WebView 로딩 오류");
                }
            }
        });
    }

    private void startWebViewTimeout() {
        cancelWebViewTimeout();

        webViewTimeoutRunnable = () -> {
            if (isVideoPlaying) {
                return;
            }
            if (llProgress.getVisibility() == View.VISIBLE ||
                    (webviewOverlay != null && webviewOverlay.getVisibility() == View.VISIBLE)) {
                showError("영상 로드 시간 초과", "스트리밍 서버 응답 없음");
            }
        };

        webViewTimeoutHandler.postDelayed(webViewTimeoutRunnable, 10000);
    }

    private void cancelWebViewTimeout() {
        if (webViewTimeoutHandler != null && webViewTimeoutRunnable != null) {
            webViewTimeoutHandler.removeCallbacks(webViewTimeoutRunnable);
        }
    }

    public class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void onVideoPlaying() {
            runOnUiThread(() -> {
                Log.d(TAG, "✅ WebView: Video actually playing");
                isVideoPlaying = true;
                cancelWebViewTimeout();
                hideError();
                hideProgressWithAnimation();
                hideWebViewOverlay();
            });
        }
    }

    private void hideWebViewOverlay() {
        if (webviewOverlay != null && webviewOverlay.getVisibility() == View.VISIBLE) {
            webviewOverlay.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        webviewOverlay.setVisibility(View.GONE);
                        webviewOverlay.setAlpha(1f);
                    })
                    .start();
        }
    }

    private void injectBaseCSSImmediately(WebView view) {
        String js =
                "javascript:(function(){ " +
                        "if(!document.getElementById('cctv-base-style')){ " +
                        "var s=document.createElement('style');" +
                        "s.id='cctv-base-style';" +
                        "s.innerHTML=`" +
                        "html,body{margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important;width:100vw!important;height:100vh!important;}" +
                        "video{" +
                        "display:block!important;" +
                        "width:100vw!important;" +
                        "height:100vh!important;" +
                        "object-fit:contain!important;" +
                        "background:black!important;" +
                        "position:fixed!important;" +
                        "top:0!important;left:0!important;" +
                        "margin:0!important;padding:0!important;" +
                        "transform:none!important;" +
                        "z-index:9999!important;" +
                        "pointer-events:none!important;" +
                        "opacity:1!important;" +
                        "visibility:visible!important;" +
                        "}" +
                        "video::-webkit-media-controls-panel,video::-webkit-media-controls-play-button,video::-webkit-media-controls-start-playback-button,video::-webkit-media-controls-overlay-play-button,video::-webkit-media-controls-enclosure,video::-webkit-media-controls{display:none!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;}" +
                        "*[poster]{background:transparent!important;}" +
                        "video[poster]{background:black!important;}" +
                        "`;" +
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
                        "s.innerHTML=`" +
                        "html,body{margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important;}" +
                        "video{" +
                        "display:block!important;" +
                        "width:100vw!important;" +
                        "height:100vh!important;" +
                        "object-fit:contain!important;" +
                        "background:black!important;" +
                        "position:fixed!important;" +
                        "top:0!important;left:0!important;" +
                        "z-index:9999!important;" +
                        "}" +
                        "video::-webkit-media-controls{display:none!important;-webkit-appearance:none!important;}" +
                        "video::-webkit-media-controls-panel{display:none!important;-webkit-appearance:none!important;}" +
                        "video::-webkit-media-controls-play-button{display:none!important;-webkit-appearance:none!important;}" +
                        "video::-webkit-media-controls-start-playback-button{display:none!important;-webkit-appearance:none!important;opacity:0!important;pointer-events:none!important;width:0!important;height:0!important;}" +
                        "video::-webkit-media-controls-overlay-play-button{display:none!important;-webkit-appearance:none!important;opacity:0!important;pointer-events:none!important;width:0!important;height:0!important;}" +
                        "video::-webkit-media-controls-enclosure{display:none!important;-webkit-appearance:none!important;}" +
                        "video::-webkit-media-controls-overlay-enclosure{display:none!important;-webkit-appearance:none!important;opacity:0!important;}" +
                        "*[poster],video[poster]{background:black!important;}" +
                        "`;" +
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
                        "v.play().catch(function(e){console.log('play error:',e);});" +
                        "if(!v.hasPlayingListener){" +
                        "v.hasPlayingListener=true;" +
                        "v.addEventListener('playing', function(){" +
                        "window.AndroidBridge && window.AndroidBridge.onVideoPlaying();" +
                        "}, {once: true});" +
                        "}" +
                        "});" +
                        "})();";

        view.evaluateJavascript(js, null);
    }

    private void setupListView() {
        favorList = Utils.readItemsFromFile(this);

        if (favorList.size() == 0) {
            llVideo.setVisibility(View.GONE);
            lvFavor.setVisibility(View.GONE);
            llRegFavor.setVisibility(View.VISIBLE);
        } else {
            llRegFavor.setVisibility(View.GONE);
        }

        lvFavor.setAdapter(new FavorAdapter(RevFavorActivity.this, 0, favorList));

        lvFavor.setOnItemClickListener((parent, view, position, id) -> {
            FavorItemVo favorItemVo = favorList.get(position);
            if (favorItemVo == null || MainData.mCctvList == null) {
                Toast.makeText(getApplicationContext(), getString(R.string.msg_error_null), Toast.LENGTH_SHORT).show();
                return;
            }

            tvSelectItem.setVisibility(View.GONE);

            for (CctvItemVo vo : MainData.mCctvList) {
                if (favorItemVo.getType().equals(vo.roadType) &&
                        favorItemVo.getName().equals(vo.cctvName)) {
                    mCurrentCctvItem = vo;
                    MainData.mCurrentCctvItemVo = vo;
                    loadCctvVideo(vo);
                    break;
                }
            }
        });

        // 첫 번째 아이템 자동 선택 및 재생
        if (favorList.size() > 0) {
            lvFavor.post(() -> {
                lvFavor.setItemChecked(0, true);
                View firstItem = lvFavor.getChildAt(0);
                if (firstItem != null) {
                    lvFavor.performItemClick(firstItem, 0, lvFavor.getAdapter().getItemId(0));
                }
            });
        }
    }

    private void loadCctvVideo(CctvItemVo cctvItem) {
        isVideoPlaying = false;
        hideError();

        PlayerType newPlayerType = "utic".equals(cctvItem.getRoadType())
                ? PlayerType.WEBVIEW
                : PlayerType.EXOPLAYER;

        stopCurrentPlayer();

        currentPlayerType = newPlayerType;
        switchPlayerVisibility(currentPlayerType);

        llProgress.setAlpha(1f);
        llProgress.setVisibility(View.VISIBLE);

        loadCctvVideoByType(cctvItem);
    }

    private void stopCurrentPlayer() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearVideoSurface();
        }

        if (webView != null) {
            webView.stopLoading();
            cancelWebViewTimeout();
        }

        hideError();
    }

    private void switchPlayerVisibility(PlayerType playerType) {
        if (playerType == PlayerType.WEBVIEW) {
            textureView.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webviewOverlay.setAlpha(1f);
            webviewOverlay.setVisibility(View.VISIBLE);
        } else {
            textureView.setVisibility(View.INVISIBLE);
            webView.setVisibility(View.GONE);
            webviewOverlay.setVisibility(View.GONE);
        }
    }

    private void loadCctvVideoByType(CctvItemVo cctvItem) {
        String roadType = cctvItem.getRoadType();

        switch (roadType) {
            case "seoul":
                startSeoulCctvVideo();
                break;
            case "jeju":
                startJejuCctvVideo();
                break;
            case "gg":
                startGgCctvVideo();
                break;
            case "daegu":
                startDaeguCctvVideo();
                break;
            case "utic":
                startUticCctvVideoWithApi();
                break;
            default:
                startCctvVideo();
                break;
        }
    }

    private void startCctvVideo() {
        new Thread(() -> {
            try {
                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startSeoulCctvVideo() {
        new Thread(() -> {
            try {
                mCurrentCctvItem.cctvUrl = SeoulCctvVideoOpenApiHelper.getSeoulCctvUrl(
                        mCurrentCctvItem.roadSectionId);

                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startJejuCctvVideo() {
        new Thread(() -> {
            try {
                String url1 = JejuCctvVideoOpenApiHelper.getCctvInfoAndSetCookie(
                        mCurrentCctvItem.roadSectionId);
                mCurrentCctvItem.cctvUrl = JejuCctvVideoOpenApiHelper.getCctvStreamUrl(url1);

                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startGgCctvVideo() {
        new Thread(() -> {
            try {
                String tempUrl = GgCctvVideoOpenApiHelper.getUrl1(
                        mCurrentCctvItem.roadSectionId);
                mCurrentCctvItem.cctvUrl = GgCctvVideoOpenApiHelper.getUrl2(tempUrl);

                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startDaeguCctvVideo() {
        new Thread(() -> {
            try {
                mCurrentCctvItem.cctvUrl = DaeguCctvVideoOpenApiHelper.getUrl(
                        mCurrentCctvItem.roadSectionId);

                Message msg = handler.obtainMessage();
                msg.what = MESSAGE_CCTV_EXOPLAYER;
                handler.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorMessage();
            }
        }).start();
    }

    private void startUticCctvVideoWithApi() {
        Log.d(TAG, "🚀 UTIC CCTV 로드: " + mCurrentCctvItem.roadSectionId);

        apiHelper.getCctvInfo(mCurrentCctvItem.roadSectionId,
                new CctvApiHelper.CctvResponseListener() {
                    @Override
                    public void onSuccess(CctvApiHelper.CctvInfo cctvInfo) {
                        Log.d(TAG, "✅ CCTV 정보 받음: " + cctvInfo.toString());

                        runOnUiThread(() -> {
                            Message msg = handler.obtainMessage();
                            msg.what = MESSAGE_CCTV_WEBVIEW;
                            msg.obj = cctvInfo.getStreamPageUrl();
                            handler.sendMessage(msg);
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(TAG, "❌ CCTV 정보 로드 실패: " + error);
                        runOnUiThread(() -> sendErrorMessage());
                    }
                });
    }

    private void sendErrorMessage() {
        Message msg = handler.obtainMessage();
        msg.what = -1;
        handler.sendMessage(msg);
    }

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_CCTV_EXOPLAYER) {
                handleExoPlayerMessage();
            } else if (msg.what == MESSAGE_CCTV_WEBVIEW) {
                handleWebViewMessage(msg);
            } else {
                handleErrorMessage();
            }
        }
    };

    private void handleExoPlayerMessage() {
        try {
            tvCopyRight.setText(getString(R.string.copyright_land));

            if (videoSurface != null) {
                exoPlayer.setVideoSurface(videoSurface);
            }

            Uri videoUri = Uri.parse(mCurrentCctvItem.getCctvUrl());
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

            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();

        } catch (Exception e) {
            e.printStackTrace();
            handleErrorMessage();
        }
    }

    private void handleWebViewMessage(Message msg) {
        try {
            tvCopyRight.setText(getString(R.string.copyright_land));

            String streamUrl = (String) msg.obj;
            if (streamUrl != null) {
                Log.d(TAG, "🌐 WebView 재생: " + streamUrl);

                webView.loadUrl(streamUrl);
                webView.setVisibility(View.VISIBLE);
            } else {
                handleErrorMessage();
            }

        } catch (Exception e) {
            e.printStackTrace();
            handleErrorMessage();
        }
    }

    private void handleErrorMessage() {
        llProgress.setVisibility(View.GONE);
        showError("영상 로드 실패", "비디오를 불러올 수 없습니다");
    }

    private void showError(String message, String detail) {
        llProgress.clearAnimation();
        llProgress.setVisibility(View.GONE);

        if (webviewOverlay != null) {
            webviewOverlay.clearAnimation();
            webviewOverlay.setVisibility(View.GONE);
        }

        tvErrorMessage.setText(message);
        tvErrorDetail.setText(detail);

        llError.setAlpha(0f);
        llError.setVisibility(View.VISIBLE);
        llError.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
    }

    private void hideError() {
        if (llError.getVisibility() == View.VISIBLE) {
            llError.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> llError.setVisibility(View.GONE))
                    .start();
        }
    }

    private void hideProgressWithAnimation() {
        if (llProgress.getVisibility() == View.VISIBLE) {
            llProgress.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        llProgress.setVisibility(View.GONE);
                        llProgress.setAlpha(1f);
                    })
                    .start();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void applyVideoFit() {
        if (currentVideoWidth > 0 && currentVideoHeight > 0) {
            VideoSize videoSize = new VideoSize(currentVideoWidth, currentVideoHeight);
            fitVideoToView(videoSize);
        }
    }

    private void fitVideoToView(VideoSize videoSize) {
        int videoWidth = videoSize.width;
        int videoHeight = videoSize.height;
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();

        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }

        Matrix matrix = new Matrix();
        float scaleX, scaleY;
        float cx = viewWidth / 2f;
        float cy = viewHeight / 2f;

        float videoAspect = (float) videoWidth / videoHeight;
        float viewAspect = (float) viewWidth / viewHeight;

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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        DeviceHelper.setOrientationPhoneToPortrait(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (currentPlayerType == PlayerType.WEBVIEW && webView != null) {
            webView.onResume();
            webView.postDelayed(() -> injectAllScripts(webView), 300);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (currentPlayerType == PlayerType.WEBVIEW && webView != null) {
            webView.onPause();
        }
    }

    @Override
    public void onBackPressed() {
        Utils.writeItemToFile(getApplicationContext(), favorList);
        releaseAllResources();

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

        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }

        cancelWebViewTimeout();
        releaseExoPlayer();
        releaseWebView();
        releaseSurface();
    }

    private void releaseExoPlayer() {
        if (exoPlayer != null) {
            try {
                exoPlayer.setPlayWhenReady(false);
                exoPlayer.stop();
                exoPlayer.clearVideoSurface();
                exoPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing ExoPlayer", e);
            } finally {
                exoPlayer = null;
            }
        }
    }

    private void releaseWebView() {
        if (webView != null) {
            try {
                webView.onPause();
                webView.loadUrl("about:blank");
                webView.stopLoading();
                webView.clearHistory();
                webView.clearCache(true);
                webView.removeAllViews();
                webView.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing WebView", e);
            } finally {
                webView = null;
            }
        }
    }

    private void releaseSurface() {
        if (videoSurface != null) {
            try {
                videoSurface.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing surface", e);
            } finally {
                videoSurface = null;
            }
        }
    }

    // ============================================================================
    // FavorAdapter
    // ============================================================================

    private class FavorAdapter extends ArrayAdapter<FavorItemVo> {

        private ArrayList<FavorItemVo> items;

        public FavorAdapter(@NonNull Context context, int resource, ArrayList<FavorItemVo> objects) {
            super(context, resource, objects);
            items = objects;
        }

        @NonNull
        @Override
        public View getView(final int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.items_favor, null);
            }
            final FavorItemVo vo = items.get(position);

            ImageView imgType = v.findViewById(R.id.imgType);
            TextView tvName = v.findViewById(R.id.tv_name);
            tvName.setText(vo.getName());

            TextView tvType = v.findViewById(R.id.tv_Type);
            if ("ex".equals(vo.getType())) {
                imgType.setImageResource(R.drawable.cctvex32);
                tvType.setText("고속도로");
            } else if ("its".equals(vo.getType())) {
                imgType.setImageResource(R.drawable.cctvits32);
                tvType.setText("국도");
            } else {
                imgType.setImageResource(R.drawable.cctvits32);
                tvType.setText("도로");
            }

            LinearLayout llDel = v.findViewById(R.id.llDel);
            llDel.setOnClickListener(view -> {
                String msg = favorList.get(position).getName() + getString(R.string.msg_delete_favor);
                Toast.makeText(RevFavorActivity.this, msg, Toast.LENGTH_SHORT).show();
                favorList.remove(position);

                FavorAdapter adapter = (FavorAdapter) lvFavor.getAdapter();
                adapter.notifyDataSetChanged();

                if (favorList.size() == 0) {
                    stopCurrentPlayer();
                    llVideo.setVisibility(View.GONE);
                    lvFavor.setVisibility(View.GONE);
                    llRegFavor.setVisibility(View.VISIBLE);
                }
            });

            LinearLayout llFullScreen = v.findViewById(R.id.ll_full_screen);
            llFullScreen.setOnClickListener(view -> {
                if (MainData.mCurrentCctvItemVo != null) {
                    Intent intent = new Intent(RevFavorActivity.this, TestVideoActivity.class);
                    startActivity(intent);
                }
            });

            return v;
        }
    }
}

