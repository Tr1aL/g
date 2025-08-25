package org.tr1al.gainium.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.nats.client.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.tr1al.gainium.dto.gainium.*;
import org.tr1al.gainium.dto.nats.NatsData;
import org.tr1al.gainium.dto.nats.NatsResponse;
import org.tr1al.gainium.entity.Bot;
import org.tr1al.gainium.entity.Setting;
import org.tr1al.gainium.exception.ToManyException;
import org.tr1al.gainium.repository.BotRepository;
import org.tr1al.gainium.repository.SettingRepository;
import org.tr1al.gainium.utils.ThrowingSupplier;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsService {
    private final static String SPEED_RUSH_ADTS = "sf.core.scripts.screener.speedRush.adts";
    private final static String SPEED_RUSH_ADTV = "sf.core.scripts.screener.speedRush.adtv";
    private final static String SPEED_RUSH_ALL = "sf.core.scripts.screener.speedRush.all";
    public final static String STATUS_OK = "OK";
    public final static String STATUS_NOTOK = "NOTOK";
    private final static String NO_CHECK_SETTINGS = "Pairs didn't pass settings check";
    private final static String NOTHING_CHANGED = "Nothing changed";
    private final static String NATS_SERVER = "nats://nats.eu-central.prod.linode.spreadfighter.cloud";

    @Value("${nats.username}")
    private String natsUsername;
    @Value("${nats.password}")
    private String natsPassword;
    @Value("${gainium.bot.process.enabled:true}")
    private boolean processEnabled;

    private final GainiumService gainiumService;
    private final BotRepository botRepository;
    private final SettingRepository settingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Set<String> IGNORE_PAIRS = new CopyOnWriteArraySet<>();

    private BotsResult cachedBotsTemplate = null;
    private long lastCachedBotTemplate;

    private long lastToMany;
    private long lastIterate;
    private final Cache<String, String> STARTED_PAIR_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(180, TimeUnit.SECONDS)
            .build();
    private boolean isWorking = false;
    private final Object isWorkingObj = new Object();
    private final Map<String, String> clonedBotMap = new ConcurrentHashMap<>();
    private final Map<String, String> changedPairBotMap = new ConcurrentHashMap<>();

    public static List<NatsData> LAST_NATS_DATA = new ArrayList<>();
    public static List<BotsResult> LAST_OPEN_BOTS = new ArrayList<>();
    public static List<DealsResult> LAST_OPEN_DEALS = new ArrayList<>();

    private Long lastProcess = null;
    private Connection nc = null;
    private Dispatcher d = null;
    private Subscription s = null;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() throws IOException, InterruptedException {
        createNatsConnection();
        executorService.submit(new ReconnectTask());
    }

    private void createNatsConnection() throws IOException, InterruptedException {
        Properties props = new Properties();
        props.put("username", natsUsername);
        props.put("password", natsPassword);
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


        nc = Nats.connectReconnectOnConnect(options);
        doWork();
    }

    @PreDestroy
    public void destroy() {
        doReconnectWork = false;
        executorService.shutdown();
        disconnectNats();
    }

    private void disconnectNats() {
        try {
            if (s != null) {
                s.unsubscribe();
            }
            if (d != null) {
                d.unsubscribe(SPEED_RUSH_ALL);
            }
            if (nc != null) {
                nc.close();
            }
        } catch (Exception e) {
            log.error("error on destroy Nats subscribe and connection");
        }
    }

    private void doWork() {
        d = nc.createDispatcher((msg) -> {
        });
        s = d.subscribe(SPEED_RUSH_ALL, getMessageHandler());
    }

    private MessageHandler getMessageHandler() {
        return (msg) -> {
            lastProcess = System.currentTimeMillis();
            Setting setting = settingRepository.findById(Setting.SETTING_ID)
                    .orElseThrow(() -> new RuntimeException("setting with id " + Setting.SETTING_ID + " not found"));
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
                log.debug("Nats response: {}", response);
                LAST_NATS_DATA = response.getData();
                List<NatsData> adtsTop = response.getData().stream()
                        .filter(a -> !IGNORE_PAIRS.contains(a.getSymbol()))
                        .sorted((o1, o2) -> o2.getAdts().compareTo(o1.getAdts()))
                        .limit(setting.getBotCount())
                        .toList();
                List<NatsData> data = response.getData();
                Map<String, BigDecimal> adtsMap = data.stream()
                        .collect(Collectors.toMap(NatsData::getSymbol, NatsData::getAdts, (o1, o2) -> o1));
                log.debug("ignored pairs {}", IGNORE_PAIRS);
                List<BotsResult> openBots = request(() -> gainiumService.getBotsDCA("open", 1L));
                if (openBots == null) {
                    log.debug("openBots is null");
                    return;
                }
                Set<String> openBotIds = openBots.stream()
                        .map(BotsResult::getId)
                        .collect(Collectors.toSet());
                Map<String, List<Bot>> botFromDBMap = botRepository.findAllById(openBotIds).stream()
                        .collect(Collectors.groupingBy(Bot::getSymbol));
                leaveBots(adtsMap, openBots, botFromDBMap, setting.isBotLeaveEnabled(), setting.getBotLeavePercent());
                if (adtsTop.isEmpty()) {
                    log.debug("Top Adts is empty");
                    return;
                }
                log.debug("Top Adts:" + adtsTop.stream().map(NatsData::toString).toList());
                LAST_OPEN_BOTS = openBots;
                if (!processEnabled) {
                    log.debug("process enabled is false");
                    return;
                }
                Set<String> openBotSymbols = openBots.stream()
                        .map(BotsResult::getSettings)
                        .flatMap(a -> a.getPair().stream())
                        .collect(Collectors.toSet());
                List<DealsResult> openDeals = request(() -> gainiumService.getAllDeals("open", null, false, "dca"));
                if (openDeals == null) {
                    log.debug("openDeals is null");
                    return;
                }
                LAST_OPEN_DEALS = openDeals;
                BotsResult template = findBotTemplate(openBots, setting.getBotTemplateName(), openDeals, setting.isBotArchiveEnabled());
                if (template == null) {
                    log.debug("template not fount");
                    return;
                }
                log.debug("OPEN_BOT_LIMIT {}", setting.getBotCount());
                log.debug("template {}", template);
                log.debug("clonedBotMap {}", clonedBotMap);
                log.debug("changedPairBotMap {}", changedPairBotMap);
                long count = openBots.size();
                log.debug("openBots count " + count);


                log.debug("process open new bots");
                if (count < setting.getBotCount()) {
                    count = Math.max(count, openDeals.stream()
                            .map(DealsResult::getBotId)
                            .distinct()
                            .count());
                    Set<String> openDealsSymbols = openDeals.stream()
                            .map(DealsResult::getSymbol)
                            .map(DealSymbol::getSymbol)
                            .collect(Collectors.toSet());
                    log.debug("openDealsSymbols {}", openDealsSymbols);
                    for (NatsData natsData : adtsTop) {
                        log.debug("try " + natsData.getSymbol());
                        if (count >= setting.getBotCount()) {
                            log.debug("break limit");
                            break;
                        }
                        log.debug("openBots count " + count);
                        if (openBotSymbols.contains(natsData.getSymbol())) {
                            log.debug(natsData.getSymbol() + " already started");
                            continue;
                        }
                        if (openDealsSymbols.contains(natsData.getSymbol())) {
                            log.debug(natsData.getSymbol() + " exists in open deals");
                            continue;
                        }
                        int idx = natsData.getSymbol().indexOf("USDT");
                        String toClonePair = natsData.getSymbol().substring(0, idx) + "_USDT";
                        if (STARTED_PAIR_CACHE.getIfPresent(toClonePair) != null) {
                            log.debug(toClonePair + " already cloned");
                            return;
                        }

                        String clonedBotId = cloneBot(template, natsData, toClonePair, setting.getBotTemplateName());
                        if (clonedBotId != null) {
                            String changedPairBotId = changeBotPair(natsData, toClonePair, clonedBotId);
                            if (changedPairBotId != null) {
                                count = startBot(count, natsData, toClonePair, changedPairBotId);
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

    private long startBot(long count, NatsData natsData, String toClonePair, String changedPairBotId) {
        SimpleBotResponse startBotResponse = request(() -> gainiumService.startBot(changedPairBotId, "dca"));
        log.debug("startBotResponse: " + startBotResponse);
        if (startBotResponse != null && STATUS_OK.equals(startBotResponse.getStatus())) {
            clonedBotMap.remove(natsData.getSymbol());
            changedPairBotMap.remove(natsData.getSymbol());
            STARTED_PAIR_CACHE.put(toClonePair, toClonePair);
            createBotInDb(natsData, changedPairBotId);
            count++;
        }
        return count;
    }

    private String changeBotPair(NatsData natsData, String toClonePair, String clonedBotId) {
        String changedPairBotId = changedPairBotMap.get(natsData.getSymbol());
        if (changedPairBotId == null) {
            SimpleBotResponse changeBotResponse = request(() -> gainiumService.changeBotPairs(clonedBotId, toClonePair));
            log.debug("changeBotResponse: " + changeBotResponse);
            if (changeBotResponse != null && (STATUS_OK.equals(changeBotResponse.getStatus())
                    || STATUS_NOTOK.equals(changeBotResponse.getStatus()) && NOTHING_CHANGED.equals(changeBotResponse.getReason()))) {
                changedPairBotId = clonedBotId;
                changedPairBotMap.put(natsData.getSymbol(), changedPairBotId);
            } else if (changeBotResponse != null && NO_CHECK_SETTINGS.equals(changeBotResponse.getReason())) {
                log.debug("ignore pair {}", natsData.getSymbol());
                IGNORE_PAIRS.add(natsData.getSymbol());
            }
        }
        return changedPairBotId;
    }

    private String cloneBot(BotsResult template, NatsData natsData, String toClonePair, String templateName) {
        String clonedBotId = clonedBotMap.get(natsData.getSymbol());
        if (clonedBotId == null) {
            SimpleBotResponse cloneBotResponse = request(() -> gainiumService.cloneDCABot(template.getId(),
                    "clone " + templateName + " to " + toClonePair,
                    toClonePair));
            log.debug("cloneBotResponse: " + cloneBotResponse);
            if (cloneBotResponse != null && STATUS_OK.equals(cloneBotResponse.getStatus())) {
                clonedBotId = cloneBotResponse.getData().toString();
                clonedBotMap.put(natsData.getSymbol(), clonedBotId);
            }
        }
        return clonedBotId;
    }

    private BotsResult findBotTemplate(List<BotsResult> openBots, String templateName, List<DealsResult> openDeals, boolean isBotArchiveEnabled) {
        BotsResult template = openBots.stream()
                .filter(a -> templateName.equals(a.getSettings().getName()))
                .findFirst().orElse(null);
        Set<String> openDealsBotIds = openDeals.stream()
                .map(DealsResult::getBotId)
                .collect(Collectors.toSet());
        if (template == null) {
            if (cachedBotsTemplate != null && lastCachedBotTemplate > System.currentTimeMillis() - 60 * 1000) {
                template = cachedBotsTemplate;
            } else {
                List<BotsResult> closed = request(() -> gainiumService.getBotsDCA("closed", 1L));
                if (closed == null) {
                    log.debug("closed is null");
                    return null;
                }
                for (BotsResult botsResult : closed) {
                    if (template == null && templateName.equals(botsResult.getSettings().getName())) {
                        template = botsResult;
                        cachedBotsTemplate = botsResult;
                        lastCachedBotTemplate = System.currentTimeMillis();
                    } else {
                        if (isBotArchiveEnabled) {
                            if (openDealsBotIds.contains(botsResult.getId())) {
                                log.debug("skip archive botId {} in openDeals", botsResult.getId());
                                continue;
                            }
                            SimpleBotResponse archiveBotResponse = request(() -> gainiumService.archiveBot(botsResult.getId(), "dca"));
                            log.debug("archiveBotResponse: " + archiveBotResponse);
                        } else {
                            log.debug("skip archive botId {} archive is disabled", botsResult.getId());
                        }
                    }
                }
            }
        }
        return template;
    }

    private void leaveBots(Map<String, BigDecimal> adtsMap, List<BotsResult> openBots,
                           Map<String, List<Bot>> botFromDBMap, boolean isBotLeaveEnabled, BigDecimal botLeavePercent) {
        if (!isBotLeaveEnabled) {
            log.debug("leave bot is disabled");
            return;
        }
        log.debug("process close bots with leave");
        for (BotsResult botsResult : openBots) {
            String symbol = ofNullable(botsResult.getSettings())
                    .map(Settings::getPair)
                    .map(a -> a.get(0))
                    .orElse(null);
            log.debug("try close {} botId {}", symbol, botsResult.getId());
            List<Bot> bots = botFromDBMap.get(symbol);
            if (bots == null || bots.isEmpty()) {
                log.debug("not found in db {} botId {}", symbol, botsResult.getId());
                continue;
            }
            for (Bot bot : bots) {
                BigDecimal adts = adtsMap.get(symbol);
                if (adts == null) {
                    log.debug("{} botId {} bot adts {} top adts {}", symbol, botsResult.getId(), bot.getAdts(), adts);
                    continue;
                }
                BigDecimal compareAdts = BigDecimal.ONE.subtract(botLeavePercent.movePointLeft(2)).multiply(bot.getAdts());
                log.debug("{} botId {} bot adts {} compare adts {} top adts {}",
                        symbol, botsResult.getId(), bot.getAdts(), compareAdts, adts);
                if (compareAdts.compareTo(adts) > 0) {
                    log.debug("stopBot {} botId {}", symbol, botsResult.getId());
                    request(() -> gainiumService.stopBot(bot.getBotId(), "dca", "leave"));
                }
            }
        }
    }

    private void createBotInDb(NatsData natsData, String botId) {
        Bot bot = new Bot();
        bot.setBotId(botId);
        bot.setSymbol(natsData.getSymbol());
        bot.setCreated(Instant.now());
        bot.setAdts(natsData.getAdts());
        bot.setAdtv(natsData.getAdtv());
        bot = botRepository.save(bot);
        log.debug("created bot in db {}", bot);
    }

    private <T> T request(ThrowingSupplier<T, ToManyException> supplier) {
        try {
            return supplier.get();
        } catch (ToManyException e) {
            lastToMany = System.currentTimeMillis();
            return null;
        }
    }

    private boolean doReconnectWork = true;

    private class ReconnectTask implements Runnable {
        @Override
        public void run() {
            while (doReconnectWork) {
                try {
                    if (lastProcess != null && lastProcess + 60_1000 < System.currentTimeMillis()) {
                        disconnectNats();
                        createNatsConnection();
                    }
                } catch (Exception e) {
                    log.error("error reconnect Nats", e);
                }
            }
        }
    }
}
