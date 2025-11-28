package com.boolint.camlocation.helper;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiSourceFetcher {

    private final String apiUrl;

    // 비동기 처리를 위한 ExecutorService (백그라운드 스레드 관리)
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    // 메인 스레드로 결과를 전달하기 위한 Handler
    private Handler handler = new Handler(Looper.getMainLooper());

    // 결과를 Activity로 전달하기 위한 인터페이스
    public interface OnSourceExtractedListener {
        void onSourceExtracted(String videoUrl);
        void onError(String message);
    }

    // ⭐️ 생성자에서 URL 주입
    public ApiSourceFetcher(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /**
     * API 호출을 시작하고 스트리밍 주소 추출을 시도합니다.
     */
    public void startFetch(OnSourceExtractedListener listener) {
        executor.execute(() -> {
            String result = fetchApiData();
            Log.d("ttt", "ttt: " + "youcan see html result");
            String finalVideoUrl = parseVideoUrl(result);

            handler.post(() -> {
                if (finalVideoUrl != null && !finalVideoUrl.isEmpty()) {
                    listener.onSourceExtracted(finalVideoUrl);
                } else {
                    listener.onError("스트리밍 주소를 찾을 수 없습니다. API 응답을 확인하세요.");
                    Log.e("ApiSourceFetcher", "API 응답 본문: " + result);
                }
            });
        });
    }

    /**
     * HTTP GET 요청을 통해 API 데이터를 가져옵니다.
     */
    private String fetchApiData() {
        StringBuilder response = new StringBuilder();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(apiUrl);  // ⭐️ 멤버 변수 사용
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
            } else {
                Log.e("ApiSourceFetcher", "API 호출 실패, 응답 코드: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            Log.e("ApiSourceFetcher", "네트워크/파싱 오류 발생", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return response.toString();
    }

    private String parseVideoUrl(String apiResponse) {
        if (apiResponse == null || apiResponse.isEmpty()) return null;

        String normalized = apiResponse.replace("\\/", "/");

        String[] patterns = {
                "(https?://[^\"'<>\\s]+\\.m3u8[^\"'<>\\s]*)",
                "(rtsp://[^\"'<>\\s]+)",
                "(rtmp://[^\"'<>\\s]+)",
                "(https?://[^\"'<>\\s]+\\.mp4[^\"'<>\\s]*)"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(normalized);
            if (m.find()) {
                return m.group(1);
            }
        }

        return null;
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        handler = null;
    }
}
