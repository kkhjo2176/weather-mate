package com.weatherlife.service;

public class NoOutfitException extends RuntimeException {
    public NoOutfitException() {
        super("날씨·성별·용도·선호 조건을 모두 만족하는 코디가 없습니다. 선호 조건을 완화하거나 카탈로그에 의류를 추가하세요.");
    }
}
