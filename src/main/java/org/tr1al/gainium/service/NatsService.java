package org.tr1al.gainium.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.nats.client.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.tr1al.gainium.dto.gainium.BotsResult;
import org.tr1al.gainium.dto.gainium.SimpleBotResponse;
import org.tr1al.gainium.dto.nats.NatsData;
import org.tr1al.gainium.dto.nats.NatsResponse;
import org.tr1al.gainium.exception.ToManyException;
import org.tr1al.gainium.utils.ThrowingSupplier;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
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
    public final static String STATUS_NOTOK = "NOTOK";
    private final static long OPEN_BOT_LIMIT = 10;
    private final static String NO_CHECK_SETTINGS = "Pairs didn't pass settings check";
    private final static String NOTHING_CHANGED = "Nothing changed";

    private final static String NATS_SERVER = "nats://nats.eu-central.prod.linode.spreadfighter.cloud";
    @Value("${nats.username}")
    private String NATS_USERNAME;
    @Value("${nats.password}")
    private String NATS_PASSWORD;
    private final GainiumService gainiumService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // Create a cache with expiration time of 5 minutes after write
    private final Cache<String, String> STARTED_PAIR_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    private final Set<String> IGNORE_PAIRS = new CopyOnWriteArraySet<>();

    private BotsResult cachedBotsTemplate = null;
    private long lastCachedBotTemplate;

    private long lastToMany;
    private long lastIterate;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() throws IOException, InterruptedException {

        Properties props = new Properties();
        props.put("username", NATS_USERNAME);
        props.put("password", NATS_PASSWORD);
        Options options = Options.builder()
                .properties(props)
                .server(NATS_SERVER)
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
        Subscription s = d.subscribe(SPEED_RUSH_ALL, getMessageHandler());
    }

    private boolean isWorking = false;
    private final Object isWorkingObj = new Object();

    private MessageHandler getMessageHandler() {
        return (msg) -> {
            synchronized (isWorkingObj) {
                if (isWorking) {
                    return;
                }
                isWorking = true;
            }
            try {
                if (lastToMany > System.currentTimeMillis() - 10 * 1000L) {
                    log.debug("skip to many timeout");
                    return;
                }
                if (lastIterate > System.currentTimeMillis() - 5 * 1000L) {
                    log.debug("skip last iterate");
                    return;
                }
                lastIterate = System.currentTimeMillis();

                NatsResponse response = null;
                try {
                    response = objectMapper.readValue(msg.getData(), NatsResponse.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                List<NatsData> adtsTop = response.getData().stream()
                        .filter(a -> !IGNORE_PAIRS.contains(a.getSymbol()))
                        .sorted((o1, o2) -> o2.getAdts().compareTo(o1.getAdts()))
                        .limit(OPEN_BOT_LIMIT)
                        .toList();
                log.debug("Top Adts:" + adtsTop.stream().map(NatsData::toString).toList());
                log.debug("ignored pairs {}", IGNORE_PAIRS);
                List<BotsResult> openBots = request(() -> gainiumService.getBotsDCA("open", 1L));
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
                        List<BotsResult> closed = request(() -> gainiumService.getBotsDCA("closed", 1L));
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
                                SimpleBotResponse archiveBotResponse = request(() -> gainiumService.archiveBot(botsResult.getId(), "dca"));
                                log.debug("archiveBotResponse: " + archiveBotResponse);
                            }
                        }
                    }
                }
                if (shortTemplate == null) {
                    log.debug("SHORT_TEMPLATE not fount");
                    return;
                }
                log.debug("shortTemplate {}", shortTemplate);
                int count = openBots.size();
                log.debug("openBots count " + count);
                if (count < OPEN_BOT_LIMIT) {
                    for (NatsData natsData : adtsTop) {
                        log.debug("try " + natsData.getSymbol());
                        if (count >= OPEN_BOT_LIMIT) {
                            log.debug("break limit");
                            break;
                        }
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
                        BotsResult finalShortTemplate = shortTemplate;
                        SimpleBotResponse cloneBotResponse = request(() -> gainiumService.cloneDCABot(finalShortTemplate.getId(),
                                "clone " + SHORT_TEMPLATE + " to " + toClonePair,
                                toClonePair));
                        log.debug("cloneBotResponse: " + cloneBotResponse);
                        if (cloneBotResponse != null && STATUS_OK.equals(cloneBotResponse.getStatus())) {
                            SimpleBotResponse changeBotResponse = request(() -> gainiumService.changeBotPairs(cloneBotResponse.getData().toString(), toClonePair));
                            log.debug("changeBotResponse: " + changeBotResponse);
                            //                        SimpleBotResponse updateBotResponse = gainiumService.updateDCABot(cloneBotResponse.getData(),
                            //                                "clone " + SHORT_TEMPLATE + " to " + toClonePair, toClonePair);
                            //                        log.debug("updateBotResponse: " + updateBotResponse);
                            if (changeBotResponse != null && (STATUS_OK.equals(changeBotResponse.getStatus())
                                    || STATUS_NOTOK.equals(changeBotResponse.getStatus()) && NOTHING_CHANGED.equals(changeBotResponse.getReason()))) {
                                //                            Integer countActive = countActive();
                                //                            if (countActive == null) {
                                //                                log.debug("countActive is null");
                                //                                return;
                                //                            }
                                //                            if (countActive < OPEN_BOT_LIMIT) {
                                SimpleBotResponse startBotResponse = request(() -> gainiumService.startBot(cloneBotResponse.getData().toString(), "dca"));
                                log.debug("startBotResponse: " + startBotResponse);
                                if (startBotResponse != null && STATUS_OK.equals(startBotResponse.getStatus())) {
                                    STARTED_PAIR_CACHE.put(toClonePair, toClonePair);
                                    count++;// = countActive + 1;
                                }
                                //                            }
                            } else if (changeBotResponse != null && NO_CHECK_SETTINGS.equals(changeBotResponse.getReason())) {
                                log.debug("ignore pair {}", natsData.getSymbol());
                                IGNORE_PAIRS.add(natsData.getSymbol());
                            }
                        }

                    }
                }
            } finally {
                synchronized (isWorkingObj) {
                    isWorking = false;
                }
            }
        };
    }

    private <T> T request(ThrowingSupplier<T, ToManyException> supplier) {
        try {
            return supplier.get();
        } catch (ToManyException e) {
            lastToMany = System.currentTimeMillis();
            return null;
        }
    }

    private Integer countActive() {
        List<BotsResult> openBots = request(() -> gainiumService.getBotsDCA("open", 1L));
        if (openBots == null) {
            log.debug("countActive openBots is null");
            return null;
        }
        return openBots.size();
    }
}
