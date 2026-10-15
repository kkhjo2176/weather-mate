package com.weatherlife;

import com.weatherlife.entity.HourlyForecast;
import com.weatherlife.repository.HourlyForecastRepository;
import com.weatherlife.service.OfficialWeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class TodayOutfitApiTest {
    @Autowired MockMvc mvc;
    @Autowired HourlyForecastRepository repository;
    @MockitoBean OfficialWeatherService weatherService;
    private final String region = "서울특별시 영등포구 신길제1동";

    @BeforeEach void setup() { repository.deleteAll(); }

    @Autowired com.weatherlife.service.OfficialRegionService regionService;

    @Test void byDateReturnsOnlyTheSpecifiedDay() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 14);
        var regionGrid = regionService.getShortRegion(region);
        HourlyForecast row = new HourlyForecast(regionGrid.nx(), regionGrid.ny(), date.atTime(9, 0));
        row.replaceWith(date.atTime(8, 0), LocalDateTime.now(), Map.of(
                "TMP","14","REH","40","WSD","2.9","SKY","1","PTY","0"));
        repository.saveAndFlush(row);
        mvc.perform(get("/api/outfits/by-date").param("regionName", region).param("date", "2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-14"))
                .andExpect(jsonPath("$.hourlyRecommendations['09:00'].input.temperature").value(14))
                .andExpect(jsonPath("$.hourlyRecommendations['09:00'].input.sky").value("SUNNY"))
                .andExpect(jsonPath("$.hourlyRecommendations['09:00'].outfits.length()").value(3));
        mvc.perform(get("/api/weather/short").param("regionName", region).param("date", "2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hourly[0].temperature").value("14"));
        mvc.perform(get("/api/weather/short").param("regionName", region).param("date", "2026-10-15"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(weatherService);
    }
}
