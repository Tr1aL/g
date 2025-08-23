package org.tr1al.gainium.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tr1al.gainium.dto.gainium.BotsResult;
import org.tr1al.gainium.dto.gainium.DealSymbol;
import org.tr1al.gainium.dto.gainium.DealsResult;
import org.tr1al.gainium.dto.gainium.Settings;
import org.tr1al.gainium.dto.nats.NatsData;
import org.tr1al.gainium.dto.rest.BotInfoDto;
import org.tr1al.gainium.entity.Bot;
import org.tr1al.gainium.repository.BotRepository;
import org.tr1al.gainium.service.NatsService;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;

@RestController
@RequiredArgsConstructor
public class BotController {

    private final BotRepository botRepository;


    @GetMapping("/bots/info")
    public List<BotInfoDto> settings() {
        List<BotInfoDto> ret = new ArrayList<>();
        Map<String, BigDecimal> currentAdtsMap = NatsService.LAST_NATS_DATA.stream()
                .collect(Collectors.toMap(NatsData::getSymbol, NatsData::getAdts));

        List<DealsResult> lastOpenDeals = NatsService.LAST_OPEN_DEALS;
        Map<String, DealsResult> dealMap = lastOpenDeals.stream()
                .collect(Collectors.toMap(DealsResult::getBotId, identity(), (o, o2) -> o));
        Map<String, Bot> botMap = botRepository.findAllById(dealMap.keySet()).stream()
                .collect(Collectors.toMap(Bot::getBotId, b -> b));

        Set<String> processedBots = new HashSet<>();
        for (BotsResult openBot : NatsService.LAST_OPEN_BOTS) {
            BotInfoDto infoDto = new BotInfoDto();
            infoDto.setBotId(openBot.getId());
            infoDto.setSymbol(ofNullable(openBot.getSettings())
                    .map(Settings::getPair)
                    .map(a -> a.get(0))
                    .orElse(null));
            infoDto.setStartAdts(ofNullable(botMap.get(openBot.getId()))
                    .map(Bot::getAdts)
                    .orElse(null));
            infoDto.setCurrentAdts(currentAdtsMap.get(infoDto.getSymbol()));
            infoDto.setSource("open bot");
            ret.add(infoDto);
            processedBots.add(openBot.getId());
        }

        for (Map.Entry<String, DealsResult> entry : dealMap.entrySet()) {
            if (processedBots.contains(entry.getKey())) {
                continue;
            }
            BotInfoDto infoDto = new BotInfoDto();
            infoDto.setBotId(entry.getKey());
            infoDto.setSymbol(ofNullable(entry.getValue())
                    .map(DealsResult::getSymbol)
                    .map(DealSymbol::getSymbol)
                    .orElse(null));
            infoDto.setStartAdts(ofNullable(botMap.get(entry.getKey()))
                    .map(Bot::getAdts)
                    .orElse(null));
            infoDto.setCurrentAdts(currentAdtsMap.get(infoDto.getSymbol()));
            infoDto.setSource("deals");
            ret.add(infoDto);

        }

        return ret;
    }
}
