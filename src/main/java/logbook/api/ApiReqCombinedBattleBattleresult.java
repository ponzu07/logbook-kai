package logbook.api;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.JsonObject;

import logbook.bean.AppCondition;
import logbook.bean.AppConfig;
import logbook.bean.BattleLog;
import logbook.bean.BattleResult;
import logbook.bean.BattleTypes.CombinedType;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.internal.BattleLogs;
import logbook.internal.Logs;
import logbook.internal.PhaseState;
import logbook.internal.log.BattleResultLogFormat;
import logbook.internal.log.LogWriter;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_combined_battle/battleresult
 *
 */
@API("/kcsapi/api_req_combined_battle/battleresult")
public class ApiReqCombinedBattleBattleresult implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {

            BattleLog log = AppCondition.get().getBattleResult();
            if (log != null) {
                // 削除
                AppCondition.get().setBattleResult(null);

                AppCondition.get().setBattleResultConfirm(log);

                log.setResult(BattleResult.toBattleResult(data));
                log.setTime(Logs.nowString());
                // 艦隊スナップショットを作る
                if (log.getCombinedType() != CombinedType.未結成 && AppCondition.get().getDeckId() == 1) {
                    BattleLog.snapshot(log, 1, 2);
                } else {
                    BattleLog.snapshot(log, AppCondition.get().getDeckId());
                }
                // 戦闘ログの保存
                BattleLogs.write(log);

                LogWriter.getInstance(BattleResultLogFormat::new)
                        .write(log);
                if (AppConfig.get().isApplyResult()) {
                    // 艦隊を更新
                    PhaseState p = new PhaseState(log);
                    p.apply(log.getBattle());
                    p.apply(log.getMidnight());
                    ShipCollection.get()
                            .getShipMap()
                            .putAll(Stream.of(p.getAfterFriend(), p.getAfterFriendCombined())
                                    .flatMap(List::stream)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toMap(Ship::getId, v -> v)));
                }
            }
        }
    }
}
