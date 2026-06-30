package tech.qiantong.qknow.hermes.tool.function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherQueryToolTest {

    private final WeatherQueryToolFunction tool = new WeatherQueryToolFunction();

    @Test
    void queryBeijingWeather() {
        WeatherQueryToolFunction.Request req = new WeatherQueryToolFunction.Request();
        req.setCity("Beijing");

        WeatherQueryToolFunction.Response resp = tool.apply(req);

        assertEquals("Beijing", resp.getCity());
        assertNotNull(resp.getWeatherInfo());
        assertNotNull(resp.getWeatherInfo().getTemperature());
        assertNotNull(resp.getWeatherInfo().getCondition());
        assertNotNull(resp.getWeatherInfo().getHumidity());
        assertNotNull(resp.getWeatherInfo().getWindSpeed());
        assertNotNull(resp.getWeatherInfo().getQueryTime());
    }

    @Test
    void queryShanghaiWeather() {
        WeatherQueryToolFunction.Request req = new WeatherQueryToolFunction.Request();
        req.setCity("Shanghai");

        WeatherQueryToolFunction.Response resp = tool.apply(req);

        assertEquals("Shanghai", resp.getCity());
        assertNotNull(resp.getWeatherInfo());
    }

    @Test
    void emptyCityReturnsNull() {
        WeatherQueryToolFunction.Request req = new WeatherQueryToolFunction.Request();
        req.setCity("");

        WeatherQueryToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getWeatherInfo());
    }

    @Test
    void blankCityReturnsNull() {
        WeatherQueryToolFunction.Request req = new WeatherQueryToolFunction.Request();
        req.setCity("   ");

        WeatherQueryToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getWeatherInfo());
    }

    @Test
    void nullCityReturnsNull() {
        WeatherQueryToolFunction.Request req = new WeatherQueryToolFunction.Request();
        req.setCity(null);

        WeatherQueryToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getWeatherInfo());
    }

    @Test
    void temperatureIsReasonable() {
        WeatherQueryToolFunction.Request req = new WeatherQueryToolFunction.Request();
        req.setCity("Beijing");

        WeatherQueryToolFunction.Response resp = tool.apply(req);

        if (resp.getWeatherInfo() != null) {
            int temp = resp.getWeatherInfo().getTemperature();
            assertTrue(temp >= -50 && temp <= 60, "Temperature should be reasonable: " + temp);
        }
    }

    @Test
    void humidityIsPercentage() {
        WeatherQueryToolFunction.Request req = new WeatherQueryToolFunction.Request();
        req.setCity("Beijing");

        WeatherQueryToolFunction.Response resp = tool.apply(req);

        if (resp.getWeatherInfo() != null) {
            int humidity = resp.getWeatherInfo().getHumidity();
            assertTrue(humidity >= 0 && humidity <= 100, "Humidity should be 0-100%: " + humidity);
        }
    }
}
