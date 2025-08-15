package org.tr1al.gainium.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.nats.client.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.tr1al.gainium.dto.gainium.BotsResult;
import org.tr1al.gainium.dto.gainium.SimpleBotResponse;
import org.tr1al.gainium.dto.nats.NatsData;
import org.tr1al.gainium.dto.nats.NatsResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsService {
    private final static String SPEED_RUSH_ADTS = "sf.core.scripts.screener.speedRush.adts";
    private final static String SPEED_RUSH_ADTV = "sf.core.scripts.screener.speedRush.adtv";
    private final static String SPEED_RUSH_ALL = "sf.core.scripts.screener.speedRush.all";
    public final static String SHORT_TEMPLATE = "SHORT_TEMPLATE";
    public final static String STATUS_OK = "OK";
    private final static long OPEN_BOT_LIMIT = 5;

    private final GainiumService gainiumService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // Create a cache with expiration time of 5 minutes after write
    Cache<String, String> STARTED_PAIR_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    private BotsResult cachedBotsTemplate = null;
    private long lastCachedBotTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() throws IOException, InterruptedException {

        Properties props = new Properties();
        props.put("username", "crypto");
        props.put("password", "cryptoPass");
        Options options = Options.builder()
                .properties(props)
                .server("nats://nats.eu-central.prod.linode.spreadfighter.cloud")
                .connectionListener((conn, event) -> {
                    log.debug("Событие: " + event);
                    if (event == ConnectionListener.Events.DISCONNECTED) {
                        log.debug("⚠️ Соединение разорвано!");
                    } else if (event == ConnectionListener.Events.RECONNECTED) {
                        log.debug("✅ Соединение восстановлено!");
                    } else if (event == ConnectionListener.Events.CLOSED) {
                        log.debug("❌ Соединение закрыто навсегда.");
                    }
                })
                .reconnectWait(Duration.ofMillis(1000)) // Ждать 1 сек между попытками
                .maxReconnects(-1)   // Бесконечные попытки (-1)
                .build();


        Connection nc = Nats.connectReconnectOnConnect(options);
        doWork(nc);
    }

    private void doWork(Connection nc) {
        Dispatcher d = nc.createDispatcher((msg) -> {
        });
        Subscription s = d.subscribe(SPEED_RUSH_ALL, (msg) -> {
            NatsResponse response = null;
            try {
                response = objectMapper.readValue(msg.getData(), NatsResponse.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
            List<NatsData> adtsTop = response.getData().stream().sorted((o1, o2) -> o2.getAdts().compareTo(o1.getAdts()))
                    .limit(OPEN_BOT_LIMIT).toList();
            log.debug("Top Adts:" + adtsTop.stream().map(NatsData::toString).toList());
            List<BotsResult> openBots = gainiumService.getBotsDCA("open", 1L);
            if (openBots == null) {
                log.debug("openBots is null");
                return;
            }
            Set<String> openBotSymbols = openBots.stream()
                    .map(BotsResult::getSettings)
                    .flatMap(a -> a.getPair().stream())
                    .collect(Collectors.toSet());
            BotsResult shortTemplate = openBots.stream()
                    .filter(a -> SHORT_TEMPLATE.equals(a.getSettings().getName()))
                    .findFirst().orElse(null);
            if (shortTemplate == null) {
                if (cachedBotsTemplate != null && lastCachedBotTemplate > System.currentTimeMillis() - 60 * 1000) {
                    shortTemplate = cachedBotsTemplate;
                } else {
                    List<BotsResult> closed = gainiumService.getBotsDCA("closed", 1L);
                    if (closed == null) {
                        log.debug("closed is null");
                        return;
                    }
                    for (BotsResult botsResult : closed) {
                        if (shortTemplate == null && SHORT_TEMPLATE.equals(botsResult.getSettings().getName())) {
                            shortTemplate = botsResult;
                            cachedBotsTemplate = botsResult;
                            lastCachedBotTemplate = System.currentTimeMillis();
                        } else {
                            SimpleBotResponse archiveBotResponse = gainiumService.archiveBot(botsResult.getId(), "dca");
                            log.debug("archiveBotResponse: " + archiveBotResponse);
                        }
                    }
                }
            }
            if (shortTemplate == null) {
                log.debug("SHORT_TEMPLATE not fount");
                return;
            }
            int count = openBots.size();
            log.debug("openBots count " + count);
            if (count < OPEN_BOT_LIMIT) {
                while (count < OPEN_BOT_LIMIT) {
                    for (NatsData natsData : adtsTop) {
                        log.debug("try " + natsData.getSymbol());
                        log.debug("openBots count " + count);
                        if (openBotSymbols.contains(natsData.getSymbol())) {
                            log.debug(natsData.getSymbol() + " already started");
                            continue;
                        }
                        int idx = natsData.getSymbol().indexOf("USDT");
                        String toClonePair = natsData.getSymbol().substring(0, idx) + "_USDT";
                        if (STARTED_PAIR_CACHE.getIfPresent(toClonePair) != null) {
                            log.debug(toClonePair + " already cloned");
                            return;
                        }
                        SimpleBotResponse cloneBotResponse = gainiumService.cloneDCABot(shortTemplate.getId(),
                                "clone " + SHORT_TEMPLATE + " to " + toClonePair,
                                toClonePair);
                        log.debug("cloneBotResponse: " + cloneBotResponse);
                        if (cloneBotResponse != null && STATUS_OK.equals(cloneBotResponse.getStatus())) {
                            SimpleBotResponse changeBotResponse = gainiumService.changeBotPairs(cloneBotResponse.getData().toString(), toClonePair);
                            log.debug("changeBotResponse: " + changeBotResponse);
//                        SimpleBotResponse updateBotResponse = gainiumService.updateDCABot(cloneBotResponse.getData(),
//                                "clone " + SHORT_TEMPLATE + " to " + toClonePair, toClonePair);
//                        log.debug("updateBotResponse: " + updateBotResponse);
                            if (changeBotResponse != null && STATUS_OK.equals(changeBotResponse.getStatus())) {
                                Integer countActive = countActive();
                                if (countActive == null) {
                                    log.debug("countActive is null");
                                    return;
                                }
                                if (countActive < OPEN_BOT_LIMIT) {
                                    SimpleBotResponse startBotResponse = gainiumService.startBot(cloneBotResponse.getData().toString(), "dca");
                                    log.debug("startBotResponse: " + startBotResponse);
                                    if (startBotResponse != null && STATUS_OK.equals(startBotResponse.getStatus())) {
                                        STARTED_PAIR_CACHE.put(toClonePair, toClonePair);
                                        count = countActive + 1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private Integer countActive() {
        List<BotsResult> openBots = gainiumService.getBotsDCA("open", 1L);
        if (openBots == null) {
            log.debug("countActive openBots is null");
            return null;
        }
        return openBots.size();
    }

//    public static void main(String[] args) {
//        String s = "GTCUSDT";
//        int idx = s.indexOf("USDT");
//
//        log.debug(s.substring(0, idx));
//    }
}
//    Попробуем такую логику: Я сделал бот-шаблон SHORT_TEMPLATE.
//    Для топ5 пар из ADTS клонируем этого бота.
//    При этом одновременно работающих должно быть не более пяти пар.
