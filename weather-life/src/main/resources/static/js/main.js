// ===== 단기예보(APIController.getShortForecast, GET /api/short) 로 게이지 갱신 =====
// 단기예보 응답에 있는 강수확률(POP)과 풍속(WSD)으로 '강수확률', '바람 세기' 게이지를 채운다.
// 자외선 지수와 미세먼지는 단기예보에 없는 항목이라 목업 값을 그대로 둔다.
// 진행 상황은 브라우저 개발자도구 Console 에 [weather-life] 로 남긴다.
(() => {
    const LOG = '[weather-life]';
    const REGION_PREFIX = '서울특별시 '; // short_range_region.csv 의 "1단계 2단계" 이름 형식
    const GAUGE_COLORS = ['blue', 'green', 'orange', 'red'];

    //이펙트 효과
    let latestRequest = 0;

    const updateWeatherEffect = (pty) => {
    // 비/눈이 섞인 경우에는 우선 눈 효과로 표시
    const effect = {
        '0': 'none',
        '1': 'rain',
        '2': 'snow',
        '3': 'snow',
        '4': 'rain'
    }[String(pty ?? '').trim()] ?? 'none';

    window.setWeatherEffect?.(effect);
    console.info(LOG, '날씨 효과:', effect, 'PTY:', pty);
    };

    // 강수확률(%) 단계
    const popLevel = (pop) => {
        if (pop < 30) return { status: '낮음', color: 'blue' };
        if (pop < 60) return { status: '보통', color: 'orange' };
        return { status: '높음', color: 'red' };
    };

    // 풍속(m/s) 단계: 기상청 기준 4 미만 약함, 4~9 약간 강함, 9 이상 강함
    const windLevel = (wsd) => {
        if (wsd < 4) return { status: '약함', color: 'green' };
        if (wsd < 9) return { status: '약간 강함', color: 'orange' };
        return { status: '강함', color: 'red' };
    };

    const pad = (n) => String(n).padStart(2, '0');

    // 현재 시각 이후 가장 가까운 예보 시각의 항목 값을 { POP: '30', WSD: '2.1', ... } 형태로 돌려준다.
    const pickNearestForecast = (items) => {
        const now = new Date();
        const nowKey = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}00`;

        const bySlot = new Map();
        items.forEach((item) => {
            const slot = `${item.fcstDate}${item.fcstTime}`;
            if (!bySlot.has(slot)) bySlot.set(slot, {});
            bySlot.get(slot)[item.category] = item.fcstValue;
        });

        const slots = [...bySlot.keys()].sort();
        const slot = slots.find((s) => s >= nowKey) ?? slots[slots.length - 1];
        return slot ? { slot, values: bySlot.get(slot) } : null;
    };

    const updateGauge = (key, { value, percent, status, color }) => {
        const gauge = document.querySelector(`.wl-gauge[data-gauge="${key}"]`);
        if (!gauge) return;

        gauge.querySelector('.wl-gauge-num').textContent = value;
        gauge.querySelector('.wl-gauge-status').textContent = status;
        gauge.querySelector('.fill').style.strokeDasharray = `${Math.max(0, Math.min(100, percent))} 100`;
        gauge.classList.remove(...GAUGE_COLORS);
        gauge.classList.add(color);
    };

    const loadShortForecast = async (district) => {
    const requestId = ++latestRequest;
    window.setWeatherEffect?.('none');

    const regionName = REGION_PREFIX + district;

        console.info(LOG, '단기예보 요청:', regionName);

        try {
            const res = await fetch(`/api/short?regionName=${encodeURIComponent(regionName)}`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);

            const data = await res.json();
            // 이전에 선택한 지역의 늦은 응답은 무시
            if (requestId !== latestRequest) return;
            const header = data?.response?.header;
            if (header && header.resultCode !== '00') {
                throw new Error(`기상청 응답 오류 ${header.resultCode} ${header.resultMsg}`);
            }

            const items = data?.response?.body?.items?.item ?? [];
            const nearest = pickNearestForecast(items);
            if (!nearest) throw new Error('예보 항목이 비어 있습니다.');

            const { slot, values } = nearest;
            updateWeatherEffect(values.PTY);
            console.info(LOG, `단기예보 수신: 항목 ${items.length}건, 사용 시각 ${slot}`, values);

            const pop = Number(values.POP);
            if (Number.isFinite(pop)) {
                updateGauge('pop', { value: pop, percent: pop, ...popLevel(pop) });
            }

            const wsd = Number(values.WSD);
            if (Number.isFinite(wsd)) {
                // 14m/s 를 게이지 최대치로 본다.
                updateGauge('wind', { value: wsd.toFixed(1), percent: (wsd / 14) * 100, ...windLevel(wsd) });
            }
        } catch (e) {
    if (requestId !== latestRequest) return;

    window.setWeatherEffect?.('none');
    console.warn(
        LOG,
        '단기예보 요청 실패: 게이지 값은 유지하고 효과는 끕니다.',
        e.message
    );
}
    };

    document.addEventListener('DOMContentLoaded', () => {
        const select = document.querySelector('select[name="district"]');
        if (!select) return;

        loadShortForecast(select.value);
        select.addEventListener('change', () => loadShortForecast(select.value));
    });
})();
