package com.weatherlife.dto;

import com.weatherlife.dto.Garment;
import java.util.List;
import static com.weatherlife.dto.OutfitTypes.*;

public record OutfitResponse(
        String recommendationType, String catalogVersion, OutfitRequest input,
        double selectionTemperature, double outerSelectionTemperature,
        List<Outfit> outfits, List<String> advice, List<String> warnings) {

    /** 화면에는 id 대신 name을 표시하면. id는 이미지 매핑 등에 이용할. */
    public record Item(String id, String name, Slot slot) {
        public static Item of(Garment g) { return g == null ? null : new Item(g.id(), g.name(), g.slot()); }
    }

    public record Outfit(
            String id, Structure structure,
            Item top, Item bottom, Item onePiece, Item innerTop,
            Item outer, OuterUsage outerUsage, Item baseLayer, Item shoes,
            List<String> accessories, List<String> reasons) { }
}
