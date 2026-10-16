package com.weatherlife.service;

import com.weatherlife.service.ClothingCatalog;
import com.weatherlife.dto.OutfitRequest;
import com.weatherlife.dto.OutfitResponse;
import com.weatherlife.dto.OutfitResponse.Item;
import com.weatherlife.dto.OutfitResponse.Outfit;
import com.weatherlife.dto.Garment;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import static com.weatherlife.dto.OutfitTypes.*;

/** 외부 AI/API/DB 없이 카탈로그의 옷을 필터링하고 점수를 매겨 한 벌로 조합합니다. */
@Service
public class OutfitService {
    private final ClothingCatalog catalog;
    private final Validator validator;

    public OutfitService(ClothingCatalog catalog, Validator validator) {
        this.catalog = catalog;
        this.validator = validator;
    }

    public OutfitResponse recommend(OutfitRequest request) {
        Objects.requireNonNull(request, "request");
        // 컨트롤러를 거치지 않고 Thymeleaf용 서비스에서 호출해도 유효성을 검사합니다.
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        var ctx = WeatherRules.evaluate(request);
        var random = new Random(request.seed());
        var candidates = new LinkedHashMap<String, Candidate>();

        // 한 요청에서 여러 조합을 만들어 점수가 좋은 순서로 고릅니다.
        // 같은 입력+seed+카탈로그에는 같은 결과가 나옵니다. 새 후보는 seed를 바꾸면 됩니다.
        for (int attempt = 0; attempt < 80; attempt++) {
            if (request.preference() != Preference.ONE_PIECE)
                add(candidates, assemble(ctx, false, random));
            if (request.preference() == Preference.ANY || request.preference() == Preference.ONE_PIECE)
                add(candidates, assemble(ctx, true, random));
        }
        if (candidates.isEmpty()) throw new NoOutfitException();

        var selected = candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparing(c -> c.outfit().id()))
                .limit(request.count()).map(Candidate::outfit).toList();
        var warnings = new ArrayList<>(ctx.warnings());
        if (selected.size() < request.count()) warnings.add("조건을 만족하는 서로 다른 코디가 적어 " + selected.size() + "개만 반환했습니다.");
        return new OutfitResponse("RULE_BASED", catalog.data().version(), request,
                ctx.temperature(), ctx.outerTemperature(), selected, ctx.advice(), List.copyOf(warnings));
    }

    private record Candidate(Outfit outfit, String signature, double score) { }

    private static void add(Map<String, Candidate> candidates, Candidate c) {
        if (c != null) candidates.merge(c.signature(), c, (a, b) -> a.score() >= b.score() ? a : b);
    }

    private Candidate assemble(WeatherRules.Context ctx, boolean dressMode, Random random) {
        var r = ctx.request();
        Garment top = null, bottom = null, dress = null, inner = null, outer = null, base = null, shoes = null;
        Garment anchor;
        if (dressMode) {
            dress = pick(Slot.ONE_PIECE, ctx, ctx.temperature(), null, g -> true, random);
            if (dress == null) return null;
            anchor = dress;
        } else {
            top = pick(Slot.TOP, ctx, ctx.temperature(), null, g -> true, random);
            if (top == null) return null;
            anchor = top;
            bottom = pick(Slot.BOTTOM, ctx, ctx.temperature(), top.style(), g -> switch (r.preference()) {
                case PANTS -> g.bottomType() == BottomType.PANTS || g.bottomType() == BottomType.SHORTS;
                case SKIRT -> g.bottomType() == BottomType.SKIRT;
                default -> true;
            }, random);
            if (bottom == null) return null;
        }
        // 뷔스티에·멜빵옷·니트 조끼 등은 별도 innerTop을 채웁니다.
        if (anchor.requiresInnerTop()) {
            inner = pick(Slot.TOP, ctx, ctx.temperature(), anchor.style(),
                    g -> !g.requiresInnerTop() && g.tags().contains("INNER_OK") && !g.id().equals(anchor.id()), random);
            if (inner == null) return null;
        }
        if (ctx.outerRequired()) {
            outer = pick(Slot.OUTER, ctx, ctx.outerTemperature(), anchor.style(), g -> true, random);
            if (outer == null) return null;
        }
        if (ctx.baseLayerRequired()) {
            base = pick(Slot.BASE_LAYER, ctx, ctx.temperature(), anchor.style(), g -> true, random);
            if (base == null) return null;
        }
        if (r.occasion() != Occasion.HOME) {
            shoes = pick(Slot.SHOES, ctx, ctx.temperature(), anchor.style(), g -> true, random);
            if (shoes == null) return null;
        }

        var items = new ArrayList<Garment>();
        for (var item : new Garment[]{top, bottom, dress, inner, outer, base, shoes}) if (item != null) items.add(item);
        // 하나의 옷이 다른 슬롯에 중복 추천되는 것을 막습니다.
        if (items.stream().map(Garment::id).distinct().count() != items.size()) return null;
        var accessories = accessories(ctx, bottom, dress);
        var reasons = new ArrayList<String>();
        reasons.add("의류별 추천 기온 범위와 선택한 용도에 맞춰 조합했습니다.");
        if (ctx.wet()) reasons.add("겉옷과 신발은 카탈로그에 등록된 방수·발수 등급을 반영했습니다.");
        if (inner != null) reasons.add("단독 착용용이 아닌 의류에 이너 상의를 함께 구성했습니다.");
        if (ctx.baseLayerRequired()) reasons.add("추운 조건이어서 발열 내의를 추가했습니다.");
        if (ctx.outerUsage() == OuterUsage.CARRY) reasons.add("겉옷은 지금 입는 용도가 아니라 낮은 기온에 대비해 챙기는 용도입니다.");

        var structure = !dressMode ? Structure.SEPARATES
                : inner == null ? Structure.ONE_PIECE : Structure.LAYERED_ONE_PIECE;
        // 신발만 바뀐 코디는 같은 조합으로 취급해 대안의 다양성을 확보합니다.
        String signature = String.join("|", id(top), id(bottom), id(dress), id(inner), id(outer), id(base));
        String fullSignature = signature + "|" + id(shoes);
        String outfitId = UUID.nameUUIDFromBytes(fullSignature.getBytes(StandardCharsets.UTF_8)).toString();
        var outfit = new Outfit(outfitId, structure, Item.of(top), Item.of(bottom), Item.of(dress), Item.of(inner),
                Item.of(outer), ctx.outerUsage(), Item.of(base), Item.of(shoes), accessories, List.copyOf(reasons));
        double meanScore = items.stream().mapToDouble(g -> score(g, ctx,
                g.slot() == Slot.OUTER ? ctx.outerTemperature() : ctx.temperature(), anchor.style())).average().orElse(0);
        // 작은 변동만 허용합니다. 기온·용도·선호 등 필수 조건은 이미 필터로 지켜집니다.
        return new Candidate(outfit, signature, meanScore + random.nextDouble() * 3);
    }

    private Garment pick(Slot slot, WeatherRules.Context ctx, double t, Style style,
                         Predicate<Garment> extra, Random random) {
        List<Garment> eligible = catalog.items().stream()
                .filter(g -> g.slot() == slot && eligible(g, ctx, t) && extra.test(g)).toList();
        if (eligible.isEmpty()) return null;
        double best = eligible.stream().mapToDouble(g -> score(g, ctx, t, style)).max().orElseThrow();
        Garment selected = null;
        double selectedScore = -Double.MAX_VALUE;
        for (var g : eligible) {
            double s = score(g, ctx, t, style);
            if (s < best - 10) continue;
            double varied = s + random.nextDouble() * 12;
            if (varied > selectedScore) { selected = g; selectedScore = varied; }
        }
        return selected;
    }

    private boolean eligible(Garment g, WeatherRules.Context ctx, double t) {
        var r = ctx.request();
        if (!g.genders().contains(r.gender()) || !g.occasions().contains(r.occasion())) return false;
        if (t < g.minTemperature() || t > g.maxTemperature()) return false;
        if (ctx.wet() && g.tags().contains("AVOID_RAIN")) return false;
        if (ctx.windy() && g.tags().contains("WIND_SENSITIVE")) return false;
        if (ctx.wet() && (g.slot() == Slot.OUTER || g.slot() == Slot.SHOES) && g.waterResistance() < 1) return false;
        if (ctx.windy() && g.slot() == Slot.OUTER && g.windResistance() < 1) return false;
        if ((r.precipitation() == Precipitation.SNOW || r.precipitation() == Precipitation.MIXED)
                && r.occasion() != Occasion.HOME && g.slot() == Slot.SHOES && !g.tags().contains("GRIP")) return false;
        return true;
    }

    private double score(Garment g, WeatherRules.Context ctx, double t, Style style) {
        double s = 100 - Math.abs(t - g.idealTemperature()) * 1.5;
        if (style != null && g.style() != style) s -= 6;
        if (ctx.request().temperature() >= 23 && ctx.request().humidity() >= 70) s += g.breathability() * 3;
        else if (ctx.request().temperature() >= 27) s += g.breathability() * 2;
        if (ctx.wet()) s += g.waterResistance() * 5;
        if (ctx.windy()) s += g.windResistance() * 4;
        return s;
    }

    private List<String> accessories(WeatherRules.Context ctx, Garment bottom, Garment dress) {
        if (ctx.request().occasion() == Occasion.HOME) return List.of();
        var result = new ArrayList<String>();
        if (ctx.wet() && ctx.request().precipitation() != Precipitation.SNOW) {
            if (!ctx.windy() && ctx.request().occasion() != Occasion.EXERCISE) result.add("우산");
            else result.add("후드가 있는 방수 겉옷");
        }
        if (ctx.temperature() <= 8) result.add("목도리");
        if (ctx.temperature() <= 3) { result.add("장갑"); result.add("방한 모자"); }
        if (ctx.temperature() > 15 && ctx.request().sky() == Sky.SUNNY && !ctx.wet()) result.add("챙이 있는 모자");
        if (ctx.temperature() <= 15 && (dress != null || (bottom != null && bottom.bottomType() == BottomType.SKIRT)))
            result.add("보온 스타킹 또는 타이즈");
        return List.copyOf(result);
    }

    private static String id(Garment g) { return g == null ? "-" : g.id(); }
}
