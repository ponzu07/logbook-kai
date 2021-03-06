package logbook.internal.gui;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.Maparea;
import logbook.bean.MapareaCollection;
import logbook.bean.Mission;
import logbook.bean.MissionCollection;
import logbook.bean.MissionCondition;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.internal.LoggerHolder;
import logbook.plugin.PluginContainer;

/**
 * 遠征確認画面
 *
 */
public class MissionCheck extends WindowController {

    @FXML
    private ChoiceBox<DeckPort> fleet;

    @FXML
    private TreeView<String> conditionTree;

    private ObjectMapper mapper = new ObjectMapper();

    @FXML
    void initialize() {
        this.fleet.getSelectionModel().selectedItemProperty().addListener((ChangeListener<DeckPort>) this::buildTree);
        this.fleet.getItems().addAll(DeckPortCollection.get().getDeckPortMap().values());
        this.fleet.getSelectionModel().select(1);
        this.fleet.setConverter(new StringConverter<DeckPort>() {
            @Override
            public String toString(DeckPort deck) {
                return deck.getName();
            }

            @Override
            public DeckPort fromString(String string) {
                return null;
            }
        });
    }

    @FXML
    void update(ActionEvent event) {
        this.buildTree(this.fleet.getSelectionModel().getSelectedItem());
    }

    private void buildTree(ObservableValue<? extends DeckPort> observable, DeckPort oldValue, DeckPort newValue) {
        this.buildTree(newValue);
    }

    private void buildTree(DeckPort deck) {
        TreeItem<String> root = new TreeItem<>();
        if (deck != null) {
            Map<Integer, Ship> shipMap = ShipCollection.get()
                    .getShipMap();
            List<Ship> fleet = DeckPortCollection.get().getDeckPortMap().get(deck.getId()).getShip()
                    .stream()
                    .map(shipMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            MissionCollection.get().getMissionMap().values().stream()
                    .sorted(Comparator.comparing(Mission::getMapareaId, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Mission::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(mission -> {
                        TreeItem<String> sub = this.buildTree0(mission, fleet);
                        if (sub != null) {
                            root.getChildren().add(sub);
                        }
                    });
        }
        this.conditionTree.setRoot(root);
    }

    private TreeItem<String> buildTree0(Mission mission, List<Ship> fleet) {
        try {
            ClassLoader classLoader = PluginContainer.getInstance().getClassLoader();
            InputStream is = classLoader.getResourceAsStream("logbook/mission/" + mission.getId() + ".json");
            if (is == null) {
                return null;
            }
            MissionCondition condition;
            TreeItem<String> item;
            try {
                condition = this.mapper.readValue(is, MissionCondition.class);
                condition.test(fleet);
                item = this.buildLeaf(condition);
                String area = Optional.ofNullable(MapareaCollection.get().getMaparea().get(mission.getMapareaId()))
                        .map(Maparea::getName)
                        .map(s -> s + " : ").orElse("");
                item.setValue(area + mission.getName());
            } finally {
                is.close();
            }
            return item;
        } catch (Exception e) {
            LoggerHolder.get().error("遠征確認画面で例外", e);
        }
        return null;
    }

    private TreeItem<String> buildLeaf(MissionCondition condition) {
        TreeItem<String> item = new TreeItem<>(condition.toString());
        this.setIcon(item, condition.getResult());
        if (condition.getConditions() != null) {
            if (condition.getConditions().size() == 1) {
                item.setValue(condition.getConditions().get(0).toString());
            } else {
                for (MissionCondition subcondition : condition.getConditions()) {
                    item.getChildren().add(this.buildLeaf(subcondition));
                }
            }
        }
        return item;
    }

    private void setIcon(TreeItem<String> item, Boolean result) {
        GlyphFont fontAwesome = GlyphFontRegistry.font("FontAwesome");

        StackPane pane = new StackPane();
        pane.setPrefWidth(18);

        if (result != null) {
            if (result) {
                pane.getChildren().add(fontAwesome.create(FontAwesome.Glyph.CHECK).color(Color.GREEN));
            } else {
                pane.getChildren().add(fontAwesome.create(FontAwesome.Glyph.EXCLAMATION).color(Color.RED));
            }
        } else {
            pane.getChildren().add(fontAwesome.create(FontAwesome.Glyph.QUESTION).color(Color.GRAY));
        }
        item.setGraphic(pane);
    }
}
