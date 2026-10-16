
function getCurrentSeason() {
    const month = new Date().getMonth() + 1;

    if (month >= 3 && month <= 5) return "spring";
    if (month >= 6 && month <= 8) return "summer";
    if (month >= 9 && month <= 11) return "autumn";
    return "winter";
}

function applySeason(season) {
    document.documentElement.dataset.season = season;

    const select = document.getElementById("seasonSelect");
    if (select) {
        select.value = season;
    }
}

document.addEventListener("DOMContentLoaded", function () {
    const savedSeason = localStorage.getItem("weatherLifeSeason");
    const season = savedSeason || getCurrentSeason();

    applySeason(season);

    document.getElementById("seasonSelect").addEventListener("change", function (event) {
        const selectedSeason = event.target.value;

        applySeason(selectedSeason);
        localStorage.setItem("weatherLifeSeason", selectedSeason);
    });
});
