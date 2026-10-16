package com.weatherlife.service;

import com.weatherlife.dto.*;
import com.weatherlife.dto.OutfitResponse.*;
import com.weatherlife.service.OutfitSetCatalog.Definition;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.weatherlife.dto.OutfitTypes.*;

/** 날씨로 고정 세트를 고르고, 그 세트의 허용 슬롯 안에서만 세부 의류를 선택합니다. */
@Service
public class OutfitService {
    private final ClothingCatalog catalog;
    private final OutfitSetCatalog sets;
    private final AccessoryService accessories;
    private final Validator validator;

    public OutfitService(ClothingCatalog catalog, OutfitSetCatalog sets,
                         AccessoryService accessories, Validator validator) {
        this.catalog = catalog;
        this.sets = sets;
        this.accessories = accessories;
        this.validator = validator;
    }

    public OutfitResponse recommend(OutfitRequest request) {
        Objects.requireNonNull(request, "request");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        var ctx = WeatherRules.evaluate(request);
        var random = new Random(request.seed());
        var warnings = new LinkedHashSet<>(ctx.warnings());
        // 용도와 달력 계절은 후보를 제외하지 않습니다. 명시한 성별/하의 선호는 유지합니다.
        var candidates = sets.sets().stream()
                .filter(s -> request.gender() == Gender.UNISEX || s.gender() == request.gender())
                .filter(s -> matchesPreference(s, request.preference())).toList();
        if (candidates.isEmpty()) throw new NoOutfitException();
        var fitting = candidates.stream().filter(s -> distance(ctx.temperature(), s.minTemperature(), s.maxTemperature()) == 0).toList();
        if (fitting.isEmpty()) {
            // 기존 이미지 밖의 조합을 만들지 않고 가장 가까운 등록 세트로 한정합니다.
            double closest = candidates.stream().mapToDouble(s -> distance(ctx.temperature(), s.minTemperature(), s.maxTemperature())).min().orElseThrow();
            fitting = candidates.stream().filter(s -> distance(ctx.temperature(), s.minTemperature(), s.maxTemperature()) == closest).toList();
            warnings.add("등록된 세트의 권장 기온 범위 밖이어서 가장 가까운 세트를 반환했습니다. 착용 적합성을 확인하세요.");
        }
        record Ranked(Definition set, double score) { }
        var ranked = fitting.stream().map(s -> new Ranked(s, scoreSet(s, ctx) + random.nextDouble() * 0.2))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(x -> x.set().key()))
                .limit(request.count()).toList();
        var outfits = new ArrayList<Outfit>();
        for (var entry : ranked) outfits.add(assemble(entry.set(), ctx, random, warnings));
        if (outfits.size() < request.count()) warnings.add("조건에 맞는 서로 다른 세트가 적어 " + outfits.size() + "개만 반환했습니다.");
        if (request.gender() == Gender.UNISEX)
            warnings.add("성별 미지정/UNISEX는 남녀 세트를 모두 후보로 사용합니다. 캐릭터 성별을 고정하려면 MALE 또는 FEMALE을 전달하세요.");
        return new OutfitResponse("RULE_BASED", catalog.data().version(), request, ctx.temperature(),
                ctx.outerTemperature(), List.copyOf(outfits), ctx.advice(), List.copyOf(warnings));
    }

    private boolean matchesPreference(Definition set, Preference p) {
        boolean dress = !set.ids("onePiece").isEmpty();
        return switch (p) {
            case ANY -> true;
            case ONE_PIECE -> dress;
            case PANTS -> !dress && set.ids("bottom").stream().allMatch(id -> catalog.item(id).bottomType() != BottomType.SKIRT);
            case SKIRT -> !dress && set.ids("bottom").stream().allMatch(id -> catalog.item(id).bottomType() == BottomType.SKIRT);
        };
    }

    private double scoreSet(Definition set, WeatherRules.Context ctx) {
        double score = 100 - Math.abs(ctx.temperature() - set.idealTemperature()) * 4;
        score -= distance(ctx.temperature(), set.minTemperature(), set.maxTemperature()) * 12;
        if (ctx.wet()) {
            if (set.tags().contains("RAIN")) score += 24;
            if (set.ids("outer").contains("leather_jacket")) score -= 15;
        } else if (set.tags().contains("RAIN")) score -= 18;
        if (ctx.windy()) {
            if (set.tags().contains("WIND")) score += 8;
            if (set.tags().contains("WIND_SENSITIVE")) score -= 20;
        }
        // 계절은 근접 후보의 작은 가점일 뿐, 여름의 추위/봄가을의 더위를 막지 않습니다.
        if (season(ctx.request().date().getMonthValue()) == set.season()) score += 0.5;
        return score;
    }

    private Outfit assemble(Definition set, WeatherRules.Context ctx, Random random, Set<String> warnings) {
        Garment top = pick(set, "top", ctx, random), bottom = pick(set, "bottom", ctx, random);
        Garment dress = pick(set, "onePiece", ctx, random), inner = pick(set, "innerTop", ctx, random);
        Garment outer = pick(set, "outer", ctx, random), shoes = pick(set, "shoes", ctx, random);
        Garment base = ctx.baseLayerRequired() ? catalog.item("thermal") : null;
        OuterUsage usage = outer == null ? OuterUsage.NONE : OuterUsage.WEAR;
        var extras = accessories.select(ctx, set, usage);
        var reasons = new ArrayList<String>();
        reasons.add("기온·습도·바람·강수에 맞춰 이미지가 있는 고정 세트를 선택했습니다.");
        reasons.add("세부 의류는 " + set.key() + "에 허용된 조합 안에서만 선택했습니다.");
        if (set.season() != season(ctx.request().date().getMonthValue()))
            reasons.add("달력 계절보다 전달된 날씨를 우선했습니다.");
        if (base != null) reasons.add("추운 날씨에 대비해 겉으로 보이는 세트를 바꾸지 않는 발열 내의를 추가했습니다.");
        if (ctx.wet() && (outer == null || outer.waterResistance() == 0))
            warnings.add("선택된 세트의 일반 겉옷을 방수 의류로 간주하지 않습니다. 비·눈 대비는 신발·소품 및 별도 안내를 확인하세요.");
        if (ctx.windy() && set.tags().contains("WIND_SENSITIVE"))
            warnings.add("선택한 세트에 바람에 날릴 수 있는 치마·원피스가 포함되어 있습니다.");
        var structure = dress == null ? Structure.SEPARATES : inner == null ? Structure.ONE_PIECE : Structure.LAYERED_ONE_PIECE;
        String signature = set.key() + "|" + String.join("|", id(top), id(bottom), id(dress), id(inner), id(outer), id(base), id(shoes));
        String outfitId = UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8)).toString();
        return new Outfit(outfitId, set.key(), set.name(), set.gender(), set.season(), Visual.of(set.key()), structure,
                Item.of(top), Item.of(bottom), Item.of(dress), Item.of(inner), Item.of(outer), usage,
                Item.of(base), Item.of(shoes), extras.stream().map(AccessoryItem::name).toList(), extras, List.copyOf(reasons));
    }

    private Garment pick(Definition set, String part, WeatherRules.Context ctx, Random random) {
        var options = set.ids(part).stream().map(catalog::item).toList();
        if (options.isEmpty()) return null;
        if (part.equals("shoes")) {
            var precipitation = ctx.request().precipitation();
            if (precipitation == Precipitation.SNOW || precipitation == Precipitation.MIXED)
                options = options.stream().filter(g -> g.tags().contains("GRIP") && g.waterResistance() > 0).toList();
            else if (ctx.wet()) options = options.stream().filter(g -> g.waterResistance() > 0).toList();
            // 강수 때 범용 운동화를 방수로 바꾸어 해석하지 않습니다.
            if (options.isEmpty()) throw new IllegalStateException("세트의 날씨 대응 신발 후보가 없습니다: " + set.key());
        }
        Garment best = null;
        double bestScore = -Double.MAX_VALUE;
        for (var g : options) {
            double t = ctx.temperature();
            double score = 100 - Math.abs(t - g.idealTemperature()) * 2
                    - distance(t, g.minTemperature(), g.maxTemperature()) * 8;
            if (t >= 25) score += g.breathability() * (ctx.request().humidity() >= 70 ? 3 : 1);
            if (ctx.wet()) score += g.waterResistance() * 5;
            else if (g.id().equals("rain_sneakers")) score -= 10;
            if (ctx.windy()) score += g.windResistance() * 2;
            if (part.equals("shoes") && set.occasionLabel().equals("출근") && g.id().equals("loafers")) score += 3;
            if (part.equals("shoes") && set.tags().contains("SPORT") && g.style() == Style.SPORT) score += 3;
            score += random.nextDouble() * 0.5;
            if (score > bestScore) { best = g; bestScore = score; }
        }
        return best;
    }
    private static double distance(double t, double min, double max) { return Math.max(0, Math.max(min - t, t - max)); }
    private static String id(Garment g) { return g == null ? "-" : g.id(); }
    private static Season season(int month) {
        return switch (month) { case 3,4,5 -> Season.SPRING; case 6,7,8 -> Season.SUMMER;
            case 9,10,11 -> Season.AUTUMN; default -> Season.WINTER; };
    }
}
