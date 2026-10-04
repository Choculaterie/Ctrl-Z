package com.choculaterie.gui;

import com.choculaterie.history.CauseSettings;
import com.choculaterie.history.HistorySettings;
import com.choculaterie.history.UndoCategorySettings;
import com.choculaterie.keybind.KeyCombo;
import com.choculaterie.keybind.KeybindSettings;
import com.choculaterie.network.ClearHistoryPayload;
import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.vanilib.gui.widget.CustomButton;
import com.choculaterie.vanilib.gui.widget.CustomTextField;
import com.choculaterie.vanilib.gui.widget.ScrollBar;
import com.choculaterie.vanilib.gui.widget.ToggleButton;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class CtrlZScreen extends Screen {
	private static final String TITLE = "Ctrl-Z";
	private static final int LABEL_WIDTH = 150;
	private static final int GAP = 10;
	private static final int CONTROL_WIDTH = 170;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_SPACING = 32;
	private static final int PADDING = 10;
	private static final int CLOSE_BUTTON_SIZE = 20;
	private static final int VIEWPORT_TOP = 50;

	private static final int HISTORY_FIELD_WIDTH = 100;
	private static final int HISTORY_UNIT_GAP = 6;
	private static final int HISTORY_UNIT_WIDTH = 64;
	private static final int TOGGLE_WIDTH = 40;
	private static final int CONFIG_BUTTON_WIDTH = 80;
	private static final int CONFIG_BUTTON_GAP = 6;

	private CustomButton closeButton;
	private CustomButton undoButton;
	private CustomButton redoButton;
	private CustomTextField historyField;
	private CustomButton historyUnitButton;
	private CustomButton clearButton;
	private ToggleButton creativeOnlyToggle;
	private ToggleButton blockOtherKeybindsToggle;
	private ToggleButton trackBlocksToggle;
	private ToggleButton trackEntitiesToggle;
	private ToggleButton trackInventoryToggle;
	private KeyCombo capturing;
	private CustomButton capturingButton;
	private final Set<Integer> capturingKeys = new LinkedHashSet<>();

	private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
	private final List<Integer> scrollBaseY = new ArrayList<>();
	private ScrollBar scrollBar;
	private int scrollAmount;
	private int maxScroll;
	private int viewportBottom;

	private int contentX;
	private int titleY;

	public CtrlZScreen() {
		super(Component.literal(TITLE));
	}

	@Override
	protected void init() {
		super.init();
		scrollWidgets.clear();
		scrollBaseY.clear();
		scrollAmount = 0;

		closeButton = new CustomButton(this.width - PADDING - CLOSE_BUTTON_SIZE, PADDING, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
			Component.literal("X"), b -> this.onClose());
		closeButton.setRenderAsXIcon(true);
		this.addRenderableWidget(closeButton);

		int totalWidth = LABEL_WIDTH + GAP + CONTROL_WIDTH;
		contentX = (this.width - totalWidth) / 2;
		int controlX = contentX + LABEL_WIDTH + GAP;

		int rowsBlockHeight = ROW_SPACING * 8 + ROW_HEIGHT;
		viewportBottom = this.height - PADDING;
		int viewportHeight = viewportBottom - VIEWPORT_TOP;
		maxScroll = Math.max(0, rowsBlockHeight - viewportHeight);

		int rowsTop;
		if (maxScroll > 0) {
			rowsTop = VIEWPORT_TOP;
			titleY = 15;
		} else {
			rowsTop = (this.height - rowsBlockHeight) / 2;
			titleY = Math.max(10, rowsTop - 40);
		}

		int rowY = rowsTop;

		undoButton = new CustomButton(controlX, rowY, CONTROL_WIDTH, ROW_HEIGHT,
			Component.literal(KeybindSettings.UNDO.display()), b -> startCapture(KeybindSettings.UNDO, undoButton));
		registerScrollWidget(undoButton, rowY);

		rowY += ROW_SPACING;
		redoButton = new CustomButton(controlX, rowY, CONTROL_WIDTH, ROW_HEIGHT,
			Component.literal(KeybindSettings.REDO.display()), b -> startCapture(KeybindSettings.REDO, redoButton));
		registerScrollWidget(redoButton, rowY);

		rowY += ROW_SPACING;
		historyField = new CustomTextField(this.minecraft, controlX, rowY, HISTORY_FIELD_WIDTH, ROW_HEIGHT, Component.literal("History"));
		historyField.setValue(String.valueOf(HistorySettings.getValue()));
		historyField.setOnChanged(this::applyHistoryValue);
		registerScrollWidget(historyField, rowY);

		historyUnitButton = new CustomButton(controlX + HISTORY_FIELD_WIDTH + HISTORY_UNIT_GAP, rowY, HISTORY_UNIT_WIDTH, ROW_HEIGHT,
			Component.literal(unitLabel(HistorySettings.getUnit())), b -> cycleHistoryUnit());
		registerScrollWidget(historyUnitButton, rowY);

		rowY += ROW_SPACING;
		clearButton = new CustomButton(controlX, rowY, CONTROL_WIDTH, ROW_HEIGHT,
			Component.literal("Clear History"), b -> clearHistory());
		registerScrollWidget(clearButton, rowY);

		rowY += ROW_SPACING;
		creativeOnlyToggle = new ToggleButton(controlX, rowY, KeybindSettings.isCreativeOnly(), KeybindSettings::setCreativeOnly);
		registerScrollWidget(creativeOnlyToggle, rowY);

		rowY += ROW_SPACING;
		blockOtherKeybindsToggle = new ToggleButton(controlX, rowY, KeybindSettings.isBlockOtherKeybinds(), KeybindSettings::setBlockOtherKeybinds);
		registerScrollWidget(blockOtherKeybindsToggle, rowY);

		int configButtonX = controlX + TOGGLE_WIDTH + CONFIG_BUTTON_GAP;

		rowY += ROW_SPACING;
		trackBlocksToggle = new ToggleButton(controlX, rowY, UndoCategorySettings.isTrackBlocks(), UndoCategorySettings::setTrackBlocks);
		registerScrollWidget(trackBlocksToggle, rowY);
		CustomButton blocksConfigButton = new CustomButton(configButtonX, rowY, CONFIG_BUTTON_WIDTH, ROW_HEIGHT,
			Component.literal("Actions..."), b -> openCauseSettings(CauseSettings.Category.BLOCKS, "Blocks", UndoCategorySettings::isTrackBlocks, UndoCategorySettings::setTrackBlocks));
		registerScrollWidget(blocksConfigButton, rowY);

		rowY += ROW_SPACING;
		trackEntitiesToggle = new ToggleButton(controlX, rowY, UndoCategorySettings.isTrackEntities(), UndoCategorySettings::setTrackEntities);
		registerScrollWidget(trackEntitiesToggle, rowY);
		CustomButton entitiesConfigButton = new CustomButton(configButtonX, rowY, CONFIG_BUTTON_WIDTH, ROW_HEIGHT,
			Component.literal("Actions..."), b -> openCauseSettings(CauseSettings.Category.ENTITIES, "Entities", UndoCategorySettings::isTrackEntities, UndoCategorySettings::setTrackEntities));
		registerScrollWidget(entitiesConfigButton, rowY);

		rowY += ROW_SPACING;
		trackInventoryToggle = new ToggleButton(controlX, rowY, UndoCategorySettings.isTrackInventory(), UndoCategorySettings::setTrackInventory);
		registerScrollWidget(trackInventoryToggle, rowY);
		CustomButton inventoryConfigButton = new CustomButton(configButtonX, rowY, CONFIG_BUTTON_WIDTH, ROW_HEIGHT,
			Component.literal("Actions..."), b -> openCauseSettings(CauseSettings.Category.INVENTORY, "Inventory", UndoCategorySettings::isTrackInventory, UndoCategorySettings::setTrackInventory));
		registerScrollWidget(inventoryConfigButton, rowY);

		if (maxScroll > 0) {
			scrollBar = new ScrollBar(controlX + CONTROL_WIDTH + GAP, VIEWPORT_TOP, viewportHeight);
			scrollBar.setScrollData(rowsBlockHeight, viewportHeight);
		} else {
			scrollBar = null;
		}
		applyScroll();
	}

	private void registerScrollWidget(AbstractWidget widget, int baseY) {
		scrollWidgets.add(widget);
		scrollBaseY.add(baseY);
	}

	private void applyScroll() {
		for (int i = 0; i < scrollWidgets.size(); i++) {
			scrollWidgets.get(i).setY(scrollBaseY.get(i) - scrollAmount);
		}
		if (scrollBar != null) {
			scrollBar.setScrollPercentage(maxScroll > 0 ? (double) scrollAmount / maxScroll : 0);
		}
	}

	private void applyHistoryValue() {
		try {
			HistorySettings.setValue(Integer.parseInt(historyField.getValue().trim()));
		} catch (NumberFormatException ignored) {
		}
	}

	private void cycleHistoryUnit() {
		HistorySettings.Unit[] units = HistorySettings.Unit.values();
		HistorySettings.Unit next = units[(HistorySettings.getUnit().ordinal() + 1) % units.length];
		HistorySettings.setUnit(next);
		historyUnitButton.setMessage(Component.literal(unitLabel(next)));
	}

	private static String unitLabel(HistorySettings.Unit unit) {
		return switch (unit) {
			case ACTIONS -> "Actions";
			case SECONDS -> "Seconds";
			case MINUTES -> "Minutes";
		};
	}

	private void openCauseSettings(CauseSettings.Category category, String title, BooleanSupplier masterGetter, Consumer<Boolean> masterSetter) {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(new CauseSettingsScreen(this, category, title, masterGetter, masterSetter));
		}
	}

	private void clearHistory() {
		if (ClientPlayNetworking.canSend(ClearHistoryPayload.TYPE)) {
			ClientPlayNetworking.send(new ClearHistoryPayload());
		}
	}

	private void startCapture(KeyCombo combo, CustomButton button) {
		capturing = combo;
		capturingButton = button;
		capturingKeys.clear();
		button.setMessage(Component.literal("> Press keys <"));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturing != null) {
			if (event.isEscape()) {
				cancelCapture();
			} else {
				capturingKeys.add(event.key());
			}
			return true;
		}
		for (AbstractWidget widget : scrollWidgets) {
			if (widget.isFocused() && widget.keyPressed(event)) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (capturing != null && !capturingKeys.isEmpty()) {
			finishCapture();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		for (AbstractWidget widget : scrollWidgets) {
			if (widget.isFocused() && widget.charTyped(event)) {
				return true;
			}
		}
		return super.charTyped(event);
	}

	private void cancelCapture() {
		capturingButton.setMessage(Component.literal(capturing.display()));
		capturing = null;
		capturingButton = null;
		capturingKeys.clear();
	}

	private void finishCapture() {
		int[] keys = new int[capturingKeys.size()];
		int i = 0;
		for (int key : capturingKeys) {
			keys[i++] = key;
		}
		capturing.set(keys);
		KeybindSettings.save();
		capturingButton.setMessage(Component.literal(capturing.display()));
		capturing = null;
		capturingButton = null;
		capturingKeys.clear();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		boolean handled = super.mouseClicked(event, doubleClick);
		if (!handled && scrollBar != null) {
			handled = scrollBar.mouseClicked(event.x(), event.y(), event.button());
		}
		if (!handled) {
			for (AbstractWidget widget : scrollWidgets) {
				if (isRowVisible(widget) && widget.mouseClicked(event, doubleClick)) {
					handled = true;
					break;
				}
			}
		}
		if (historyField.isMouseOver(event.x(), event.y())) {
			this.setFocused(historyField);
		} else {
			this.setFocused(null);
		}
		return handled;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean handled = super.mouseReleased(event);
		if (scrollBar != null && scrollBar.mouseReleased(event.x(), event.y(), event.button())) {
			handled = true;
		}
		for (AbstractWidget widget : scrollWidgets) {
			if (widget.mouseReleased(event)) {
				handled = true;
			}
		}
		return handled;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (scrollBar != null && scrollBar.isDragging()) {
			if (scrollBar.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)) {
				scrollAmount = (int) Math.round(scrollBar.getScrollPercentage() * maxScroll);
				applyScroll();
				return true;
			}
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (maxScroll > 0 && mouseY >= VIEWPORT_TOP && mouseY < viewportBottom) {
			scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - (int) (verticalAmount * ROW_SPACING)));
			applyScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private boolean isRowVisible(AbstractWidget widget) {
		return maxScroll == 0 || (widget.getY() + widget.getHeight() > VIEWPORT_TOP && widget.getY() < viewportBottom);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, UITheme.Colors.PANEL_BG);
		super.extractRenderState(context, mouseX, mouseY, delta);

		int titleX = (this.width - this.font.width(TITLE)) / 2;
		context.text(this.font, TITLE, titleX, titleY, UITheme.Colors.TEXT_PRIMARY);

		boolean clip = maxScroll > 0;
		if (clip) {
			context.enableScissor(0, VIEWPORT_TOP, this.width, viewportBottom);
		}
		for (AbstractWidget widget : scrollWidgets) {
			widget.extractRenderState(context, mouseX, mouseY, delta);
		}
		context.text(this.font, "Undo key:", contentX, undoButton.getY() + 6, UITheme.Colors.TEXT_PRIMARY);
		context.text(this.font, "Redo key:", contentX, redoButton.getY() + 6, UITheme.Colors.TEXT_PRIMARY);
		context.text(this.font, "History (" + unitLabel(HistorySettings.getUnit()).toLowerCase(Locale.ROOT) + "):",
			contentX, historyField.getY() + 6, UITheme.Colors.TEXT_PRIMARY);
		context.text(this.font, "Creative only:", contentX, creativeOnlyToggle.getY() + 6, UITheme.Colors.TEXT_PRIMARY);
		context.text(this.font, "Block other keys:", contentX, blockOtherKeybindsToggle.getY() + 6, UITheme.Colors.TEXT_PRIMARY);
		context.text(this.font, "Track blocks:", contentX, trackBlocksToggle.getY() + 6, UITheme.Colors.TEXT_PRIMARY);
		context.text(this.font, "Track entities:", contentX, trackEntitiesToggle.getY() + 6, UITheme.Colors.TEXT_PRIMARY);
		context.text(this.font, "Track inventory:", contentX, trackInventoryToggle.getY() + 6, UITheme.Colors.TEXT_PRIMARY);
		if (clip) {
			context.disableScissor();
		}
		if (scrollBar != null) {
			scrollBar.extractRenderState(context, mouseX, mouseY, delta);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		CustomTextField.restoreMinecraftCharCallback();
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(null);
		}
	}
}
