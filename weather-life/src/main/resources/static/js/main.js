// 오늘의 날씨 슬라이드 (목업): 화살표로 하단 점 표시만 이동한다
document.addEventListener('DOMContentLoaded', () => {
    const dots = document.querySelectorAll('.wl-dots span');
    if (!dots.length) return;

    let current = 0;
    const move = (step) => {
        dots[current].classList.remove('is-active');
        current = (current + step + dots.length) % dots.length;
        dots[current].classList.add('is-active');
    };

    document.querySelector('.wl-slide-btn.prev')?.addEventListener('click', () => move(-1));
    document.querySelector('.wl-slide-btn.next')?.addEventListener('click', () => move(1));
});
