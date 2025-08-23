package org.tr1al.gainium.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tr1al.gainium.dto.gainium.*;
import org.tr1al.gainium.entity.Setting;
import org.tr1al.gainium.exception.ToManyException;
import org.tr1al.gainium.mock.SettingRepositoryMock;
import org.tr1al.gainium.repository.SettingRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GainiumService {
    private final static String TO_MANY_REQUESTS = "Too many requests, please try again later";

    @Value("${gainium.token}")
    private String gainiumToken;
    @Value("${gainium.secret}")
    private String gainiumSecret;

    private final static String GAINIUM_API_URL = "https://api.gainium.io";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final SettingRepository settingRepository;

    private boolean getPaperContext() {
        Setting setting = settingRepository.findById(Setting.SETTING_ID)
                .orElseThrow(() -> new RuntimeException("setting with id " + Setting.SETTING_ID + " not found"));
        return setting.isPaperContext();
    }

    public List<BotsResult> getBotsCombo(String status, Long page) throws ToManyException {
        String endpoint = "/api/bots/combo";
        return getBotsResults(status, page, endpoint);
    }

    public List<BotsResult> getBotsDCA(String status, Long page) throws ToManyException {
        String endpoint = "/api/bots/dca";
        return getBotsResults(status, page, endpoint);
    }

    private List<BotsResult> getBotsResults(String status, Long page, String endpoint) throws ToManyException {
        String method = "GET";
        endpoint += "?paperContext=" + getPaperContext();
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

    public List<DealsResult> getAllDeals(String status, String botId, boolean terminal, String botType) throws ToManyException {
        long page = 1;
        List<DealsResult> ret = new ArrayList<>();
        List<DealsResult> deals = getDeals(status, page++, botId, terminal, botType);
        while (!deals.isEmpty()) {
            ret.addAll(deals);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
                log.error(e.getMessage(), e);
            }
            deals = getDeals(status, page++, botId, terminal, botType);
        }
        return ret;
    }

    public List<DealsResult> getDeals(String status, Long page, String botId, boolean terminal, String botType) throws ToManyException {
        String method = "GET";
        String endpoint = "/api/deals?paperContext=" + getPaperContext();
        endpoint += "&terminal=" + terminal;
        if (status != null) {
            endpoint += "&status=" + status;
        }
        if (page != null) {
            endpoint += "&page=" + page;
        }
        if (botId != null) {
            endpoint += "&botId=" + botId;
        }
        if (botType != null) {
            endpoint += "&botType=" + botType;
        }
        String body = "";
        DealsResponse response = doRequest(DealsResponse.class, method, endpoint);
        return Optional.ofNullable(response)
                .map(DealsResponse::getData)
                .map(DealsData::getResult)
                .orElse(null);
    }

    public SimpleBotResponse cloneComboBot(String botId, String name, String pair) throws ToManyException {
        String method = "PUT";
        String endpoint = "/api/cloneComboBot?botId=" + botId + "" +
                "&paperContext=" + getPaperContext();
        String body = "{\"name\":\"" + name + "\",\"pair\":[" + pair + "\"]}";
        return doRequest(SimpleBotResponse.class, method, endpoint, body);
    }

    public SimpleBotResponse cloneDCABot(String botId, String name, String pair) throws ToManyException {
        String method = "PUT";
        String endpoint = "/api/cloneDCABot?botId=" + botId + "" +
                "&paperContext=" + getPaperContext();
        String body = "";
//        String body = "{\"name\":\"" + name + "\",\"pair\":[" + pair + "\"]}";
        return doRequest(SimpleBotResponse.class, method, endpoint, body);
    }

    public SimpleBotResponse startBot(String botId, String type) throws ToManyException {
        String method = "POST";
        String endpoint = "/api/startBot?botId=" + botId + "" +
                "&type=" + type + "" +
                "&paperContext=" + getPaperContext();
        return doRequest(SimpleBotResponse.class, method, endpoint);
    }

    public SimpleBotResponse archiveBot(String botId, String type) throws ToManyException {
        String method = "DELETE";
        String endpoint = "/api/archiveBot?botId=" + botId + "" +
                "&botType=" + type + "" +
                "&paperContext=" + getPaperContext();
        return doRequest(SimpleBotResponse.class, method, endpoint);
    }

    public SimpleBotResponse stopBot(String botId, String type, String closeType) throws ToManyException {
        String method = "DELETE";
        String endpoint = "/api/stopBot?botId=" + botId + "" +
                "&botType=" + type + "" +
                "&paperContext=" + getPaperContext();
        if (closeType != null) {
            endpoint += "&closeType=" + closeType;
        }
        return doRequest(SimpleBotResponse.class, method, endpoint);
    }

    public SimpleBotResponse changeBotPairs(String botId, String pair) throws ToManyException {
        String method = "POST";
        String endpoint = "/api/changeBotPairs?botId=" + botId +
                "&paperContext=" + getPaperContext() +
                "&pairsToSetMode=replace" +
                "&pairsToSet=" + pair;
        return doRequest(SimpleBotResponse.class, method, endpoint);
    }

    public SimpleBotResponse updateDCABot(String botId, String name, String pair) throws ToManyException {


        String method = "POST";
        String endpoint = "/api/updateDCABot?botId=" + botId +
                "&paperContext=" + getPaperContext();
        String body = "{\"name\":\"" + name + "\",\"pair\":[" + pair + "\"]}";

        return doRequest(SimpleBotResponse.class, method, endpoint, body);
    }

    private <T> T doRequest(Class<T> clazz, String method, String endpoint) throws ToManyException {
        return doRequest(clazz, method, endpoint, "");
    }

    private <T> T doRequest(Class<T> clazz, String method, String endpoint, String body) throws ToManyException {
        Long time = System.currentTimeMillis();
        body = body.trim();
        String hmac = calculateHmac(body, method, endpoint, time);

        //System.setProperty("jdk.httpclient.HttpClient.log", "all");
        HttpClient client = HttpClient.newHttpClient();

        // Создаем GET-запрос
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(GAINIUM_API_URL + endpoint))
                .header("accept", "application/json")
                .header("token", gainiumToken)
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

        HttpResponse<String> response;
        try {
            // Отправляем запрос и получаем ответ
            response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            log.error("{}{}{}", method, endpoint, body, e);
            return null;
        }

        String body1 = response.body();
        log.debug(body1);
        if (body1 != null && body1.contains(TO_MANY_REQUESTS)) {
            throw new ToManyException();
        }
        try {
            return objectMapper.readValue(body1, clazz);
        } catch (JsonProcessingException e) {
            log.error("{}{}{}, response {}", method, endpoint, body, body1, e);
            return null;
        }

    }

    private String calculateHmac(String body, String method, String endpoint, long time) {
        try {
            String data = method + endpoint + time;

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(gainiumSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hashBytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) throws ToManyException {
        GainiumService service = new GainiumService(new SettingRepositoryMock());
        service.gainiumToken = "";
        service.gainiumSecret = "";
//        service.paperContext = true;
        List<BotsResult> list1 = service.getBotsDCA("open", 1L);
        List<BotsResult> results = list1.stream()
                .filter(a -> a.getId().equals("68a8e1b9c45cf30fd1635cf3")
                        || a.getId().equals("68a90c63c45cf30fd16a570c"))
//                .filter(b -> b.getSettings().getPair().contains("ZEREBROUSDT"))
                .collect(Collectors.toList());
//        System.out.println(results);
//        for (BotsResult result : results) {
//            SimpleBotResponse response = service.stopBot(result.getId(), "dca", "closeByMarket");
//            System.out.println();
//        }
//        List<BotsResult> list2 = service.getBotsDCA(null, 2L);
//        List<BotsResult> list3 = service.getBotsDCA(null, 3L);
//        List<BotsResult> list4 = service.getBotsDCA(null, 4L);
//        List<BotsResult> list5 = service.getBotsDCA(null, 5L);
//        List<BotsResult> list = service.getBotsCombo("open", 1L);
//        String id = list.get(0).get_id();
//        CloneBotResponse comboBot = service.cloneComboBot(id, "BNB_USDT");
//        List<BotsResult> startedBots = list.stream()
//                .filter(a -> !a.getSettings().getName().equals(NatsService.SHORT_TEMPLATE))
//                .toList();
//        startedBots.forEach(a -> {
//            SimpleBotResponse response = null;
//            try {
//                response = service.stopBot(a.getId(), "dca");
//            } catch (ToManyException e) {
//                throw new RuntimeException(e);
//            }
//            System.out.println(response);
//        });

//        service.getDeals(String status, Long page, String botId, boolean terminal, String botType)
        List<DealsResult> deals = service.getAllDeals("open", null, false, "dca");
        List<String> openDealBots = deals.stream()
                .filter(a -> a.getBotId().equals("68a8e1b9c45cf30fd1635cf3")
                        || a.getBotId().equals("68a90c63c45cf30fd16a570c"))
                .map(a -> a.getBotId())
//                .distinct()
                .collect(Collectors.toList());
        System.out.println();
    }

}
