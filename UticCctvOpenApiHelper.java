package com.boolint.camlocation.helper;

import android.content.Context;
import android.util.Log;

import com.boolint.camlocation.bean.CctvItemVo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;


public class UticCctvOpenApiHelper {

    private static final String TAG = "UTIC-DOWN";
    private static final String FILE_URL = "https://pub-34fdb3554a5344a480bd5d01e36793f0.r2.dev/utic.xlsx";

    /**
     * UTIC Excel 파일을 다운로드하여 internal storage 에 저장한다.
     *
     * @param context   Context
     * @param roadType  저장 파일명에 사용될 prefix
     * @return 저장된 File (실패 시 null)
     */
    public static File downloadCctvExcelFile(Context context, String roadType) {
        HttpURLConnection connection = null;
        BufferedInputStream input = null;
        FileOutputStream output = null;

        try {
            URL url = new URL(FILE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            connection.connect();

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP Response = " + responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "파일 다운로드 실패. code = " + responseCode);
                return null;
            }

            input = new BufferedInputStream(connection.getInputStream());

            // 디렉토리 생성
            File dir = new File(context.getFilesDir(), "cctv_cache");
            if (!dir.exists()) dir.mkdirs();

            // 타임스탬프 + roadType 조합 파일명
            String timestamp = new SimpleDateFormat("yyyyMMddHHmm", Locale.KOREA).format(new Date());
            File outFile = new File(dir, roadType + "_" + timestamp + ".xlsx");

            output = new FileOutputStream(outFile);

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            output.flush();
            Log.d(TAG, "✅ 파일 저장 완료: " + outFile.getAbsolutePath());

            return outFile;

        } catch (Exception e) {
            Log.e(TAG, "다운로드 오류", e);
            return null;

        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) {}
            try { if (output != null) output.close(); } catch (Exception ignored) {}
            if (connection != null) connection.disconnect();
        }
    }

    public static List<CctvItemVo> parseCctvExcelFile(File excelFile, String roadType) {
        List<CctvItemVo> list = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(excelFile);

            Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            Log.d("ttt", "parseCctvFile: " + String.format("%d", rowCount));

            for (int i = 1; i < rowCount; i++) { // 0은 헤더니까 1부터
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // B - roadSectionId "L933113"
                String roadSectionId = getStringCell(row, 1);

                // C - cctvName "강원 강릉 용강동"
                String cctvName = getStringCell(row, 2);

                // D - filterRoadName "KBS 재난포털"
                String filterRoadName = getStringCell(row, 3);

                // E - coordX //128.89116
                double coordX = getDoubleCell(row, 4);

                // F - coordY //37.752075
                double coordY = getDoubleCell(row, 5);

                // 상수/기본값 항목
                String filterRoadType = "도로";
                String fileCreateTime = "";
                String cctvFormat = "";
                String cctvResolution = "";
                String cctvUrl = "";

                CctvItemVo item = new CctvItemVo(
                        coordX,
                        coordY,
                        roadType,
                        filterRoadType,
                        filterRoadName,
                        fileCreateTime,
                        cctvFormat,
                        cctvResolution,
                        roadSectionId,
                        cctvName,
                        cctvUrl
                );

                list.add(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }


    private static String getStringCell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private static double getDoubleCell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 0;
        try {
            return cell.getNumericCellValue();
        } catch (Exception e) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim());
            } catch (Exception ex) {
                return 0;
            }
        }
    }



}

