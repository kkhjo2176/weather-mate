package com.weatherlife.service;

import com.weatherlife.dto.RegionMedium;
import com.weatherlife.dto.RegionShort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Slf4j
@Service
public class OfficialRegionService {

    // 단기예보 지역정보
    private Map<String, RegionShort> shortRegions = Map.of();

    // 중기예보 지역정보
    private Map<String, RegionMedium> mediumRegions = Map.of();


    // 서버 실행 시 자동 호출
    @PostConstruct
    public void init() throws IOException {

        try {
//            log.info("========== CSV 초기화 시작 ==========");
//            log.info("단기예보 CSV 로딩 시작");
            loadShortRegions();
//            log.info("단기예보 CSV 로딩 완료 : {}건", shortRegions.size());
//            log.info("중기예보 CSV 로딩 시작");
            loadMediumRegions();
//            log.info("중기예보 CSV 로딩 완료 : {}건", mediumRegions.size());
//            log.info("========== CSV 초기화 완료 ==========");

        } catch (Exception e) {

            log.error("CSV 초기화 실패! 원인: {}", e.getMessage(), e);

            throw new IllegalStateException("지역정보 CSV 로딩 실패", e);
        }

        loadShortRegions();
        loadMediumRegions();

        log.info("단기예보 지역정보 {}건 로딩 완료", shortRegions.size());
        log.info("중기예보 지역정보 {}건 로딩 완료", mediumRegions.size());
    }


    // CSV 파일 읽기
    private CSVParser openCsv(String fileName) throws IOException {

        ClassPathResource resource =
                new ClassPathResource("data/" + fileName);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        // UTF-8 BOM이 있어도 읽을 수 있도록 처리
        reader.mark(1);

        if (reader.read() != '\uFEFF') {
            reader.reset();
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .build();

        try {
            return format.parse(reader);
        } catch (IOException | RuntimeException e) {
            reader.close();
            throw e;
        }
    }


    // 단기예보 CSV 로딩
    private void loadShortRegions() throws IOException {

        Map<String, RegionShort> data = new HashMap<>();

        try (CSVParser parser =
                     openCsv("short_range_region.csv")) {

            for (CSVRecord row : parser) {

//                String regionName = row.get("regionName").trim();
//
//                int nx = Integer.parseInt(row.get("nx").trim());
//                int ny = Integer.parseInt(row.get("ny").trim());

                String step1 = row.get("1단계").trim();
                String step2 = row.get("2단계").trim();
                String step3 = row.get("3단계").trim();

                // 지역명 합치기
                String regionName = String.join(" ", step1, step2, step3)
                        .replaceAll("\\s+", " ")
                        .trim();

                // 좌표 가져오기

                int nx = Integer.parseInt(row.get("격자 X").trim());
                int ny = Integer.parseInt(row.get("격자 Y").trim());

                RegionShort region = new RegionShort(nx, ny);

//                if (data.putIfAbsent(regionName, region) != null) {
//                    throw new IllegalStateException(
//                            "중복된 지역명: " + regionName
//                    );
//                }
                if (data.putIfAbsent(regionName, region) != null) {
                    log.warn("중복된 지역명 스킵: {}", regionName);
                    continue;
                }
            }
        }

        shortRegions = Map.copyOf(data);
    }


    // 중기예보 CSV 로딩
    private void loadMediumRegions() throws IOException {

        Map<String, RegionMedium> data = new HashMap<>();

        try (CSVParser parser =
                     openCsv("medium_range_region.csv")) {

            for (CSVRecord row : parser) {
                String regionName = row.get("구역명").trim();
                String regId = row.get("예보구역코드").trim();
                String type = row.get("특성").trim();

                RegionMedium region = new RegionMedium(regId);

                // 특성과 지역명을 조합해서 저장
//                String key = type + ":" + regionName;
//                RegionMedium existing = data.putIfAbsent(key, region);

//                if (data.putIfAbsent(regionName, region) != null) {
//                    throw new IllegalStateException(
//                            "중복된 지역명: " + regionName
//                    );
//                }
                if (data.putIfAbsent(regionName, region) != null) {
                    log.warn("중복된 지역명 스킵: {}", regionName);
                    continue;
                }
            }
        }

        mediumRegions = Map.copyOf(data);
    }


    // 단기예보 지역정보 조회
    public RegionShort getShortRegion(String regionName) {

        return shortRegions.get(regionName);
    }


    // 중기예보 지역정보 조회
    public RegionMedium getMediumRegion(String regionName) {

        return mediumRegions.get(regionName);
    }
}