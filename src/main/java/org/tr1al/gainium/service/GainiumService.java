package org.tr1al.gainium.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.tr1al.gainium.dto.gainium.BotsData;
import org.tr1al.gainium.dto.gainium.BotsResponse;
import org.tr1al.gainium.dto.gainium.BotsResult;
import org.tr1al.gainium.dto.gainium.SimpleBotResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GainiumService {

    private final static String GAINIUM_TOKEN = "6899a0b49fc939e674d6262d";
    private final static String GAINIUM_SECRET = "2ecf03c1-29c6-4abc-baea-040294d0e23d";
    private final static String GAINIUM_API_URL = "https://api.gainium.io";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public List<BotsResult> getBotsCombo(String status, Long page) {
        String endpoint = "/api/bots/combo";
        return getBotsResults(status, page, endpoint);
    }

    public List<BotsResult> getBotsDCA(String status, Long page) {
        String endpoint = "/api/bots/dca";
        return getBotsResults(status, page, endpoint);
    }

    private List<BotsResult> getBotsResults(String status, Long page, String endpoint) {
        String method = "GET";
        endpoint += "?paperContext=false";
        if (status != null) {
            endpoint += "&status=" + status;
        }
        if (page != null) {
            endpoint += "&page=" + page;
        }
        String body = "";
        BotsResponse response = doRequest(BotsResponse.class, method, endpoint);
        return Optional.ofNullable(response)
                .map(BotsResponse::getData)
                .map(BotsData::getResult)
                .orElse(null);
    }

    public SimpleBotResponse cloneComboBot(String botId, String name, String pair) {
        String method = "PUT";
        String endpoint = "/api/cloneComboBot?botId=" + botId + "" +
                "&paperContext=false";
        String body = "{\"name\":\"" + name + "\",\"pair\":[" + pair + "\"]}";
        return doRequest(SimpleBotResponse.class, method, endpoint, body);
    }

    public SimpleBotResponse cloneDCABot(String botId, String name, String pair) {
        String method = "PUT";
        String endpoint = "/api/cloneDCABot?botId=" + botId + "" +
                "&paperContext=false";
        String body = "";
//        String body = "{\"name\":\"" + name + "\",\"pair\":[" + pair + "\"]}";
        return doRequest(SimpleBotResponse.class, method, endpoint, body);
    }

    public SimpleBotResponse startBot(String botId, String type) {
        String method = "POST";
        String endpoint = "/api/startBot?botId=" + botId + "" +
                "&type=" + type + "" +
                "&paperContext=false";
        return doRequest(SimpleBotResponse.class, method, endpoint);
    }

    public SimpleBotResponse archiveBot(String botId, String type) {
        String method = "DELETE";
        String endpoint = "/api/archiveBot?botId=" + botId + "" +
                "&botType=" + type + "" +
                "&paperContext=false";
        return doRequest(SimpleBotResponse.class, method, endpoint);
    }

    public SimpleBotResponse stopBot(String botId, String type) {
        String method = "DELETE";
        String endpoint = "/api/stopBot?botId=" + botId + "" +
                "&botType=" + type + "" +
                "&paperContext=false";
        return doRequest(SimpleBotResponse.class, method, endpoint);
    }

    public SimpleBotResponse changeBotPairs(String botId, String pair) {
        String method = "POST";
        String endpoint = "/api/changeBotPairs?botId=" + botId +
                "&paperContext=false" +
                "&pairsToSetMode=replace" +
                "&pairsToSet=" + pair;
        return doRequest(SimpleBotResponse.class, method, endpoint);
    }

    public SimpleBotResponse updateDCABot(String botId, String name, String pair) {


        String method = "POST";
        String endpoint = "/api/updateDCABot?botId=" + botId +
                "&paperContext=false";
        String body = "{\"name\":\"" + name + "\",\"pair\":[" + pair + "\"]}";

        return doRequest(SimpleBotResponse.class, method, endpoint, body);
    }

    private <T> T doRequest(Class<T> clazz, String method, String endpoint) {
        return doRequest(clazz, method, endpoint, "");
    }

    private <T> T doRequest(Class<T> clazz, String method, String endpoint, String body) {
        Long time = System.currentTimeMillis();
        body = body.trim();
        String hmac = calculateHmac(body, method, endpoint, time);

        //System.setProperty("jdk.httpclient.HttpClient.log", "all");
        HttpClient client = HttpClient.newHttpClient();

        // Создаем GET-запрос
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(GAINIUM_API_URL + endpoint))
                .header("accept", "application/json")
                .header("token", GAINIUM_TOKEN)
                .header("time", String.valueOf(time))
                .header("signature", hmac);
        switch (method) {
            case "GET":
                builder = builder.GET();
                break;
            case "PUT":
                builder = builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                break;
            case "POST":
                builder = builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                break;
            case "DELETE":
                builder = builder.DELETE();
                break;
        }
        HttpRequest request = builder.build();

        try {
            // Отправляем запрос и получаем ответ
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
//
//                // Выводим статус код и тело ответа
//                log.debug("Status code: " + response.statusCode());
//                String body1 = response.body();
//                log.debug("Response body: " + body1);

            String body1 = response.body();
            log.debug(body1);
            return objectMapper.readValue(body1, clazz);

        } catch (Exception e) {
            e.printStackTrace();
            //todo
            return null;
        }
    }

    private String calculateHmac(String body, String method, String endpoint, long time) {
        try {
            String data = method + endpoint + time;

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(GAINIUM_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hashBytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        GainiumService service = new GainiumService();
        List<BotsResult> list = service.getBotsDCA("open", 1L);
//        List<BotsResult> list = service.getBotsCombo("open", 1L);
//        String id = list.get(0).get_id();
//        CloneBotResponse comboBot = service.cloneComboBot(id, "BNB_USDT");
        List<BotsResult> startedBots = list.stream()
                .filter(a -> !a.getSettings().getName().equals(NatsService.SHORT_TEMPLATE))
                .toList();
        startedBots.forEach(a -> {
            SimpleBotResponse response = service.stopBot(a.getId(), "dca");
            System.out.println(response);
        });
        System.out.println();
    }

}
