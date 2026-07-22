package tech.qiantong.qknow.hermes.tool.function;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WeatherQueryToolTest {

    private static HttpServer server;
    private static WeatherQueryToolFunction tool;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = ("{\"nearest_area\":[{}],\"current_condition\":[{"
                    + "\"temp_C\":\"21\",\"weatherDesc\":[{\"value\":\"Clear\"}],"
                    + "\"humidity\":\"55\",\"windspeedKmph\":\"9\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        tool = new WeatherQueryToolFunction(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/{}?format=j1");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

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
