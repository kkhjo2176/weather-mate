package com.weatherlife;

import com.weatherlife.service.ClothingCatalog;
import com.weatherlife.dto.OutfitRequest;
import com.weatherlife.dto.OutfitResponse.Item;
import com.weatherlife.service.OutfitService;
import com.weatherlife.service.OfficialWeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import static com.weatherlife.dto.OutfitTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 사용자에게 보여줄 한 벌의 무결성, 날씨 제약, HTTP 오류 처리를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class OutfitApiTest {
    @Autowired MockMvc mvc;
    @Autowired OutfitService service;
    @Autowired ClothingCatalog catalog;
    @MockitoBean OfficialWeatherService weatherService;

    @Test void existingWeatherEndpointsRemainAvailable() throws Exception {
        given(weatherService.getShortRangeForecast("테스트 지역")).willReturn("{\"forecast\":\"short\"}");
        given(weatherService.getMediumLandForecast("11B00000")).willReturn("{\"forecast\":\"land\"}");
        given(weatherService.getMediumTemperatureForecast("11B10101")).willReturn("{\"forecast\":\"temperature\"}");
        given(weatherService.getShortRangeForecast("서울특별시 영등포구 신길제1동")).willReturn("{}");
        mvc.perform(get("/api/short").param("regionName", "테스트 지역"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.forecast").value("short"));
        mvc.perform(get("/api/medium/land").param("regId", "11B00000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.forecast").value("land"));
        mvc.perform(get("/api/medium/temperature").param("regId", "11B10101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.forecast").value("temperature"));
        mvc.perform(get("/api/test")).andExpect(status().isOk())
                .andExpect(content().string("API 호출 성공!"));
    }

    private OutfitRequest request(double t, Gender gender, Precipitation rain, double wind,
                                  Occasion occasion, Preference preference) {
        return new OutfitRequest(t, 75.0, wind, Sky.CLOUDY, rain, gender, occasion, preference,
                ThermalPreference.NORMAL, null, null, 3, 42L);
    }

    @Test void validApiAndCatalog() throws Exception {
        mvc.perform(post("/api/outfits/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature\":24,\"humidity\":65,\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommendationType").value("RULE_BASED"))
                .andExpect(jsonPath("$.outfits.length()").value(3));
        mvc.perform(get("/api/outfits/catalog")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(catalog.items().size()));
    }

    @Test void outfitsRemainCompleteAcrossSeasonsRainWindAndGenders() {
        int checked = 0;
        for (var gender : Gender.values()) for (double t : new double[]{-8, 6, 17, 26, 34})
            for (var rain : new Precipitation[]{Precipitation.NONE, Precipitation.RAIN})
                for (double wind : new double[]{0, 8}) {
                    var response = service.recommend(request(t, gender, rain, wind, Occasion.DAILY, Preference.ANY));
                    assertThat(response.outfits()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
                    for (var outfit : response.outfits()) {
                        if (outfit.structure() == Structure.SEPARATES) {
                            assertThat(outfit.top()).isNotNull();
                            assertThat(outfit.bottom()).isNotNull();
                            assertThat(outfit.onePiece()).isNull();
                        } else {
                            assertThat(outfit.onePiece()).isNotNull();
                            assertThat(outfit.top()).isNull();
                            assertThat(outfit.bottom()).isNull();
                        }
                        assertThat(outfit.shoes()).isNotNull();
                        var ids = Arrays.stream(new Item[]{outfit.top(),outfit.bottom(),outfit.onePiece(),
                                        outfit.innerTop(),outfit.outer(),outfit.baseLayer(),outfit.shoes()})
                                .filter(Objects::nonNull).map(Item::id).toList();
                        assertThat(ids).doesNotHaveDuplicates();
                        if (rain != Precipitation.NONE) {
                            assertThat(outfit.outer()).isNotNull();
                            for (var item : List.of(outfit.outer(), outfit.shoes())) {
                                var g = catalog.items().stream().filter(x -> x.id().equals(item.id())).findFirst().orElseThrow();
                                assertThat(g.waterResistance()).isGreaterThanOrEqualTo(1);
                            }
                        }
                        checked++;
                    }
                }
        assertThat(checked).isGreaterThanOrEqualTo(60);
    }

    @Test void explicitPantsAndDressPreferencesAreRespected() {
        var pants = service.recommend(request(25, Gender.FEMALE, Precipitation.NONE, 0, Occasion.DAILY, Preference.PANTS));
        assertThat(pants.outfits()).allSatisfy(o -> {
            assertThat(o.onePiece()).isNull();
            var g = catalog.items().stream().filter(x -> x.id().equals(o.bottom().id())).findFirst().orElseThrow();
            assertThat(g.bottomType()).isIn(BottomType.PANTS, BottomType.SHORTS);
        });
        var dresses = service.recommend(request(25, Gender.FEMALE, Precipitation.NONE, 0, Occasion.DAILY, Preference.ONE_PIECE));
        assertThat(dresses.outfits()).allSatisfy(o -> {
            assertThat(o.onePiece()).isNotNull(); assertThat(o.top()).isNull(); assertThat(o.bottom()).isNull();
        });
    }

    @Test void overallsHaveInnerTop() {
        var result = service.recommend(request(22, Gender.UNISEX, Precipitation.NONE, 0, Occasion.DAILY, Preference.ONE_PIECE));
        assertThat(result.outfits()).allSatisfy(o -> {
            assertThat(o.structure()).isEqualTo(Structure.LAYERED_ONE_PIECE);
            assertThat(o.innerTop()).isNotNull();
        });
    }

    @Test void homeDoesNotUseOutdoorWeather() {
        var result = service.recommend(request(24, Gender.UNISEX, Precipitation.RAIN, 20, Occasion.HOME, Preference.ANY));
        assertThat(result.selectionTemperature()).isEqualTo(24);
        assertThat(result.outfits()).allSatisfy(o -> {
            assertThat(o.outer()).isNull(); assertThat(o.shoes()).isNull(); assertThat(o.accessories()).isEmpty();
        });
    }

    @Test void officeAndExerciseHaveCoverageAtDifferentTemperatures() {
        for (var occasion : new Occasion[]{Occasion.OFFICE, Occasion.EXERCISE})
            for (double t : new double[]{-8, 0, 10, 20, 30}) {
                var result = service.recommend(request(t, Gender.UNISEX, Precipitation.RAIN, 4, occasion, Preference.PANTS));
                assertThat(result.outfits()).isNotEmpty();
                assertThat(result.outfits()).allSatisfy(o -> {
                    for (var item : List.of(o.top(), o.bottom(), o.outer(), o.shoes())) {
                        var g = catalog.items().stream().filter(x -> x.id().equals(item.id())).findFirst().orElseThrow();
                        assertThat(g.occasions()).contains(occasion);
                    }
                });
            }
    }

    @Test void snowRequiresGripAndColdAddsBaseLayer() {
        var result = service.recommend(request(-5, Gender.UNISEX, Precipitation.SNOW, 3, Occasion.DAILY, Preference.PANTS));
        assertThat(result.outfits()).allSatisfy(o -> {
            assertThat(o.baseLayer()).isNotNull();
            var shoe = catalog.items().stream().filter(x -> x.id().equals(o.shoes().id())).findFirst().orElseThrow();
            assertThat(shoe.tags()).contains("GRIP");
        });
    }

    @Test void coolerEveningProducesCarryOuter() {
        var req = new OutfitRequest(25.0,60.0,0.0,Sky.CLOUDY,Precipitation.NONE,Gender.UNISEX,
                Occasion.DAILY,Preference.PANTS,ThermalPreference.NORMAL,14.0,28.0,3,0L);
        assertThat(service.recommend(req).outfits()).allSatisfy(o -> {
            assertThat(o.outer()).isNotNull(); assertThat(o.outerUsage()).isEqualTo(OuterUsage.CARRY);
        });
    }

    @Test void sameSeedIsRepeatable() {
        var req = request(24,Gender.FEMALE,Precipitation.NONE,0,Occasion.DAILY,Preference.ANY);
        assertThat(service.recommend(req)).isEqualTo(service.recommend(req));
    }

    @Test void malformedAndInvalidRequestsReturn400() throws Exception {
        for (String json : List.of("{}", "{\"temperature\":20,\"humidity\":101}",
                "{\"temperature\":20,\"humidity\":60,\"count\":0}",
                "{\"temperature\":20,\"humidity\":60,\"gender\":\"OTHER\"}",
                "{\"temperature\":20,\"humidity\":60,\"minTemperature\":25}",
                "{\"temperature\":20,\"humidity\":60,\"typo\":1}",
                "{\"temperature\":\"NaN\",\"humidity\":60}")) {
            mvc.perform(post("/api/outfits/recommend").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void impossiblePreferenceReturns422RatherThanChangingUserPreference() throws Exception {
        mvc.perform(post("/api/outfits/recommend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"temperature\":22,\"humidity\":60,\"gender\":\"UNISEX\",\"preference\":\"SKIRT\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("NO_MATCHING_OUTFIT"));
    }
}
