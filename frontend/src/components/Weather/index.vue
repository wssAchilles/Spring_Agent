<template>
    <div class="weather-wrap">
        <div class="weather-head">
            <div class="address">
                <el-icon><Location /></el-icon>
                {{ store.data.city || '定位中...' }}
            </div>
        </div>

        <div class="weather-body">
            <div class="left">
                <img class="icon" :src="getWeatherIcon(store.data.weatherCode)" alt="" />
                <div class="temperature">{{ store.data.temperature ?? '--' }}</div>
                <div class="explain">
                    <div class="unit">℃</div>
                    <div class="label">{{ store.data.weatherText }}</div>
                </div>
            </div>

            <div class="right">
                <div class="info-item">
                    <div class="label">湿度：</div>
                    <div class="value valueunit">{{ store.data.humidity ?? '--' }}%</div>
                </div>
                <div class="info-item border">
                    <div class="label">风速：</div>
                    <div class="value">{{ store.data.windSpeed ?? '--' }} km/h</div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup name="Weather">
    import { reactive } from 'vue';

    const store = reactive({
        data: {}
    });

    const WMO_MAP = {
        0: '晴', 1: '大部晴朗', 2: '多云', 3: '阴天',
        45: '雾', 48: '雾凇', 51: '小毛毛雨', 53: '毛毛雨', 55: '大毛毛雨',
        56: '冻毛毛雨', 57: '冻雨', 61: '小雨', 63: '中雨', 65: '大雨',
        66: '冻小雨', 67: '冻大雨', 71: '小雪', 73: '中雪', 75: '大雪',
        77: '雪粒', 80: '小阵雨', 81: '阵雨', 82: '大阵雨',
        85: '小阵雪', 86: '大阵雪', 95: '雷阵雨', 96: '雷阵雨伴冰雹', 99: '强雷阵雨伴冰雹'
    };

    function getWeatherIcon(code) {
        const name = WMO_MAP[code] || '晴';
        let img = name;
        if (['小雨', '中雨', '大雨', '小毛毛雨', '毛毛雨', '大毛毛雨', '小阵雨', '阵雨', '大阵雨'].includes(name)) img = '下雨';
        if (['小雪', '中雪', '大雪', '小阵雪', '大阵雪', '雪粒'].includes(name)) img = '雪';
        if (['雷阵雨', '雷阵雨伴冰雹', '强雷阵雨伴冰雹'].includes(name)) img = '雷阵雨';
        if (['雾', '雾凇'].includes(name)) img = '雾';
        if (['阴天'].includes(name)) img = '多云';
        const valid = ['晴','下雨','雪','雷阵雨','雾','多云','特大暴雨','风'];
        if (!valid.includes(img)) img = '晴';
        return new URL(`../../assets/system/images/index/weather/${img}.png`, import.meta.url).href;
    }

    async function getCityByIP() {
        return { lat: 39.9, lon: 116.4, city: '北京' };
    }

    async function getData() {
        try {
            const loc = await getCityByIP();
            store.data.city = loc.city;

            const url = `https://api.open-meteo.com/v1/forecast?latitude=${loc.lat}&longitude=${loc.lon}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto`;
            const res = await fetch(url);
            const data = await res.json();

            if (data.current) {
                store.data.temperature = Math.round(data.current.temperature_2m);
                store.data.humidity = data.current.relative_humidity_2m;
                store.data.weatherCode = data.current.weather_code;
                store.data.weatherText = WMO_MAP[data.current.weather_code] || '晴';
                store.data.windSpeed = Math.round(data.current.wind_speed_10m);
            }
        } catch {
            // 静默失败
        }
    }

    getData();
</script>

<style scoped lang="scss">
    .weather-wrap {
        width: 100%;
        height: 150px;
        background: url('@/assets/system/images/index/weather-bg.png') no-repeat center center;
        background-size: 100% 100%;
        display: flex;
        flex-direction: column;
        justify-content: center;
    }

    .info-item,
    .address,
    .left,
    .weather-body,
    .weather-head {
        display: flex;
        align-items: center;
    }
    .iconbox {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 25px;
        height: 25px;
        background-color: #f3f9ff;
        border-radius: 50%;
        margin-right: 10px;
    }
    .weather-head {
        line-height: 32px;
        gap: 10px;
        color: #333;
        padding: 0px 20px;
    }

    .weather-body {
        flex-wrap: wrap;
        justify-content: space-between;
        padding: 12px 20px;
        gap: 16px;
    }

    .spac {
        width: 2px;
        height: 16px;
        background-color: #c0c0c0;
    }

    .left {
        gap: 10px;
        flex: 0 0 188px;
        border-right: 1px solid #d1e1fa;
    }

    .icon {
        width: 52px;
        height: 52px;
    }
    .tqicon {
        width: 15px;
        height: 15px;
    }

    .temperature {
        font-size: 48px;
        line-height: 55px;
    }

    .explain {
        color: #555;
        .label {
            font-size: 12px;
            line-height: 30px;
        }

        .unit {
            font-size: 18px;
            line-height: 23px;
        }
    }

    .right {
        flex: 1;
        min-width: 100px;
        display: flex;
        flex-wrap: wrap;
        margin-left: 15px;
    }

    .info-item {
        font-size: 12px;
        .label {
            font-size: 12px;
            color: #868d98;
        }
        .value {
            width: 75px;
            color: #222;
            text-align: right;
        }
        .valueunit {
            font-size: 18px;
        }
    }
    .border {
        position: relative;
        margin-top: 10px;
        padding-top: 10px;

        &::before {
            content: '';
            position: absolute;
            top: 0;
            left: 35px;
            right: 0;
            height: 1px;
            background-color: #d1e1fa;
        }
    }
    .tqimg {
        margin-right: 10px;
    }
    .el-icon {
        width: 1.2em;
        height: 1.2em;
        margin-right: 5px;
    }
</style>

<style scoped lang="scss">
.weather-wrap {
    height: 172px;
    justify-content: flex-start;
    background: #ffffff;
}

.weather-head {
    min-height: 42px;
    padding: 0 16px;
    border-bottom: 1px solid #e9edf2;
    color: #667085;
    font-size: 12px;
}

.weather-body {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
    padding: 12px 16px;
}

.left {
    flex: none;
    border-right: 0;
}

.icon {
    width: 46px;
    height: 46px;
}

.temperature {
    font-size: 38px;
    line-height: 44px;
}

.right {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin-left: 0;
}

.info-item {
    min-width: 0;
    justify-content: space-between;
}

.info-item .value,
.info-item .valueunit {
    width: auto;
    font-size: 12px;
}

.border {
    margin-top: 0;
    padding-top: 0;
}

.border::before {
    display: none;
}
</style>
