package com.boolint.camlocation.helper;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

public class CctvVideoManager {

    public interface OnVideoReadyListener {
        void onVideoReady(String videoUrl, SourceType sourceType);
        void onError(String message);
    }

    public enum SourceType {
        UTIC,           // UTIC 전체 (WebView)
        HANGANG,        // 한강 (API + SSL 우회)
        NAKDONG,        // 낙동강 (API)
        YEONGSAN,       // 영산강 (API)
        GEUM            // 금강 (API)
    }

    private Context context;
    private Activity activity;
    private ViewGroup rootLayout;
    private ApiSourceFetcher apiFetcher;
    private VideoSourceExtractor webExtractor;

    // ⭐️ 생성자 1: ViewGroup 직접 지정
    public CctvVideoManager(Context context, ViewGroup rootLayout) {
        this.context = context;
        this.rootLayout = rootLayout;
        this.activity = null;
    }

    // ⭐️ 생성자 2: Activity만 전달 (자동 rootLayout 생성)
    public CctvVideoManager(Activity activity) {
        this.context = activity;
        this.activity = activity;
        this.rootLayout = null;
    }

    // ⭐️ 생성자 3: Context만 전달 (Headless 모드)
    public CctvVideoManager(Context context) {
        this.context = context;
        this.activity = null;
        this.rootLayout = null;
    }

    public void extract(String pageUrl, OnVideoReadyListener listener) {
        SourceType type = detectSourceType(pageUrl);

        if (type == SourceType.UTIC) {
            extractWithWebView(pageUrl, type, listener);
        } else {
            extractWithApi(pageUrl, type, listener);
        }
    }

    private SourceType detectSourceType(String url) {
        if (url.contains("hrfco.go.kr")) return SourceType.HANGANG;
        if (url.contains("nakdongriver.go.kr")) return SourceType.NAKDONG;
        if (url.contains("yeongsanriver.go.kr")) return SourceType.YEONGSAN;
        if (url.contains("geumriver.go.kr")) return SourceType.GEUM;
        return SourceType.UTIC;
    }

    // ⭐️ WebView 방식 (UTIC 등 대다수)
    private void extractWithWebView(String pageUrl, SourceType type, OnVideoReadyListener listener) {
        webExtractor = new VideoSourceExtractor(context);

        VideoSourceExtractor.OnVideoFoundListener videoListener = new VideoSourceExtractor.OnVideoFoundListener() {
            @Override
            public void onVideoFound(String videoUrl) {
                listener.onVideoReady(videoUrl, type);
            }

            @Override
            public void onError(String message) {
                listener.onError(message);
            }
        };

        // ⭐️ 상황에 따라 적절한 extract 메서드 호출
        if (rootLayout != null) {
            // ViewGroup이 지정된 경우
            webExtractor.extract(pageUrl, rootLayout, videoListener);
        } else if (activity != null) {
            // Activity만 있는 경우 (자동 rootLayout 생성)
            webExtractor.extract(activity, pageUrl, videoListener);
        } else {
            // Headless 모드 (addView 없이)
            webExtractor.extract(pageUrl, videoListener);
        }
    }

    // ⭐️ API 방식 (4대강)
    private void extractWithApi(String pageUrl, SourceType type, OnVideoReadyListener listener) {
        String apiUrl = convertToApiUrl(pageUrl, type);

        apiFetcher = new ApiSourceFetcher(apiUrl);
        apiFetcher.startFetch(new ApiSourceFetcher.OnSourceExtractedListener() {
            @Override
            public void onSourceExtracted(String videoUrl) {
                listener.onVideoReady(videoUrl, type);
            }

            @Override
            public void onError(String message) {
                listener.onError(message);
            }
        });
    }

    private String convertToApiUrl(String pageUrl, SourceType type) {
        // 각 강별로 API 엔드포인트 변환 로직
        return pageUrl;
    }

    public void destroy() {
        if (apiFetcher != null) {
            apiFetcher.shutdown();
            apiFetcher = null;
        }
        if (webExtractor != null) {
            webExtractor.destroy();
            webExtractor = null;
        }
    }
}
