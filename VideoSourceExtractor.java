package com.boolint.camlocation.helper;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class VideoSourceExtractor {

    private static final String TAG = "ttt";
    private static final long TIMEOUT_MS = 5000;

    public interface OnVideoFoundListener {
        void onVideoFound(String videoUrl);
        void onError(String message);
    }

    private Context context;
    private WebView headlessWebView;
    private OnVideoFoundListener listener;
    private ViewGroup parentView;
    private Handler mainHandler;
    private Runnable timeoutRunnable;
    private volatile boolean isFound = false;
    private volatile boolean isDestroyed = false;

    public VideoSourceExtractor(Context context) {
        this.context = context.getApplicationContext(); // ⭐️ 메모리 누수 방지
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void extract(String targetPageUrl, OnVideoFoundListener listener) {
        this.listener = listener;
        this.parentView = null;
        this.isFound = false;
        this.isDestroyed = false;

        mainHandler.post(() -> {
            if (isDestroyed) return;

            headlessWebView = new WebView(context);
            setupWebView();

            // ⭐ addView 하지 않음 ⭐
            Log.d(TAG, "Headless WebView load: " + targetPageUrl);
            headlessWebView.loadUrl(targetPageUrl);

            startTimeout();
        });
    }

    public void extract(Activity activity, String targetPageUrl, OnVideoFoundListener listener) {
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        FrameLayout autoRoot = new FrameLayout(activity);
        autoRoot.setLayoutParams(new ViewGroup.LayoutParams(1, 1));

        decorView.addView(autoRoot);

        extract(targetPageUrl, autoRoot, listener);
    }

    public void extract(String targetPageUrl, ViewGroup rootLayout, OnVideoFoundListener listener) {
        this.listener = listener;
        this.parentView = rootLayout;
        this.isFound = false;
        this.isDestroyed = false;

        mainHandler.post(() -> {
            if (isDestroyed) return;

            headlessWebView = new WebView(context);
            setupWebView();

            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(1, 1);
            headlessWebView.setLayoutParams(params);
            headlessWebView.setVisibility(View.INVISIBLE);
            parentView.addView(headlessWebView);

            headlessWebView.loadUrl(targetPageUrl);
            Log.d(TAG, "페이지 로드 시작: " + targetPageUrl);

            // ⭐️ 타임아웃 설정
            startTimeout();
        });
    }

    private void setupWebView() {
        WebSettings settings = headlessWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36");

        headlessWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isFound || isDestroyed) {
                    return super.shouldInterceptRequest(view, request);
                }

                String url = request.getUrl().toString();
                String lowerUrl = url.toLowerCase();
                Log.d(TAG, "요청 감지: " + lowerUrl);

                if (isVideoUrl(lowerUrl)) {
                    isFound = true;
                    Log.d(TAG, "영상 URL 발견: " + url);

                    mainHandler.post(() -> {
                        cancelTimeout();
                        if (listener != null && !isDestroyed) {
                            listener.onVideoFound(url);
                        }
                        destroy();
                    });
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "페이지 로드 완료: " + url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !isFound) {
                    Log.e(TAG, "페이지 로드 오류: " + error.getDescription());
                }
            }
        });
    }

    private boolean isVideoUrl(String lowerUrl) {
        // .m3u8 (chunklist 제외)
//        if (lowerUrl.contains(".m3u8") && !lowerUrl.contains("chunklist")) {
//            return true;
//        }
        if (lowerUrl.contains(".m3u8")) {
            return true;
        }
        // .mp4
        if (lowerUrl.contains(".mp4")) {
            return true;
        }
        // .ts 세그먼트는 제외 (m3u8을 먼저 찾아야 함)
        return false;
    }

    private void startTimeout() {
        timeoutRunnable = () -> {
            if (!isFound && !isDestroyed) {
                Log.e(TAG, "타임아웃: 영상 URL을 찾지 못함");
                if (listener != null) {
                    listener.onError("영상 URL을 찾을 수 없습니다 (타임아웃)");
                }
                destroy();
            }
        };
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    public void destroy() {
        if (isDestroyed) return;
        isDestroyed = true;

        cancelTimeout();

        mainHandler.post(() -> {
            if (headlessWebView != null) {
                headlessWebView.stopLoading();
                headlessWebView.clearHistory();
                headlessWebView.clearCache(true);

                if (parentView != null) {
                    parentView.removeView(headlessWebView);
                }

                headlessWebView.destroy();
                headlessWebView = null;
            }
            parentView = null;
            listener = null;
        });
    }
}
