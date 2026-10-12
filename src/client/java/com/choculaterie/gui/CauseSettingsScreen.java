package com.choculaterie.gui;

import com.choculaterie.history.Cause;
import com.choculaterie.history.CauseSettings;
import com.choculaterie.vanilib.gui.theme.UIDraw;
import com.choculaterie.vanilib.gui.theme.UIIcon;
import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.vanilib.gui.widget.CustomButton;
import com.choculaterie.vanilib.gui.widget.ScrollBar;
import com.choculaterie.vanilib.gui.widget.ToggleButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class CauseSettingsScreen extends Screen {
	private static final int LABEL_WIDTH = 190;
	private static final int GAP = 10;
	private static final int CONTROL_WIDTH = 170;
	private static final int ROW_HEIGHT = UITheme.Dimensions.BUTTON_HEIGHT;
	private static final int ROW_SPACING = 26;
	private static final int PADDING = 10;
	private static final int CLOSE_BUTTON_SIZE = UITheme.Dimensions.BUTTON_HEIGHT;
	private static final int VIEWPORT_TOP = 50;

	private final Screen parent;
	private final CauseSettings.Category category;
	private final BooleanSupplier masterGetter;
	private final Consumer<Boolean> masterSetter;

	private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
	private final List<Integer> scrollBaseY = new ArrayList<>();
	private final List<String> rowLabels = new ArrayList<>();
	private ScrollBar scrollBar;
	private int scrollAmount;
	private int maxScroll;
	private int viewportBottom;
	private int contentX;
	private int titleY;

	public CauseSettingsScreen(Screen parent, CauseSettings.Category category, String title, BooleanSupplier masterGetter, Consumer<Boolean> masterSetter) {
		super(Component.literal(title));
		this.parent = parent;
		this.category = category;
		this.masterGetter = masterGetter;
		this.masterSetter = masterSetter;
	}

	@Override
	protected void init() {
		super.init();
		scrollWidgets.clear();
		scrollBaseY.clear();
		rowLabels.clear();
		scrollAmount = 0;

		CustomButton closeButton = new CustomButton(this.width - PADDING - CLOSE_BUTTON_SIZE, PADDING, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
			Component.literal(""), b -> this.onClose());
		closeButton.setIcon(UIIcon.ARROW_LEFT);
		this.addRenderableWidget(closeButton);

		int totalWidth = LABEL_WIDTH + GAP + CONTROL_WIDTH;
		contentX = (this.width - totalWidth) / 2;
		int controlX = contentX + LABEL_WIDTH + GAP;

		Cause[] causes = Cause.values();
		int rowsBlockHeight = ROW_SPACING * causes.length + ROW_HEIGHT;
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
		ToggleButton masterToggle = new ToggleButton(controlX, rowY, masterGetter.getAsBoolean(), masterSetter::accept);
		registerScrollWidget(masterToggle, rowY, "All " + this.getTitle().getString().toLowerCase(java.util.Locale.ROOT));

		for (Cause cause : causes) {
			rowY += ROW_SPACING;
			ToggleButton toggle = new ToggleButton(controlX, rowY, CauseSettings.isEnabled(category, cause),
				enabled -> CauseSettings.setEnabled(category, cause, enabled));
			registerScrollWidget(toggle, rowY, cause.displayName());
		}

		if (maxScroll > 0) {
			scrollBar = new ScrollBar(controlX + CONTROL_WIDTH + GAP, VIEWPORT_TOP, viewportHeight);
			scrollBar.setScrollData(rowsBlockHeight, viewportHeight);
		} else {
			scrollBar = null;
		}
		applyScroll();
	}

	private void registerScrollWidget(AbstractWidget widget, int baseY, String label) {
		scrollWidgets.add(widget);
		scrollBaseY.add(baseY);
		rowLabels.add(label);
	}

	private void applyScroll() {
		for (int i = 0; i < scrollWidgets.size(); i++) {
			scrollWidgets.get(i).setY(scrollBaseY.get(i) - scrollAmount);
		}
		if (scrollBar != null) {
			scrollBar.setScrollPercentage(maxScroll > 0 ? (double) scrollAmount / maxScroll : 0);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (AbstractWidget widget : scrollWidgets) {
			if (widget.isFocused() && widget.keyPressed(event)) {
				return true;
			}
		}
		return super.keyPressed(event);
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

		UIDraw.centeredText(context, this.getTitle().getString(), this.width / 2, titleY, UITheme.Colors.TEXT_HIGHLIGHT);

		boolean clip = maxScroll > 0;
		if (clip) {
			context.enableScissor(0, VIEWPORT_TOP, this.width, viewportBottom);
		}
		for (int i = 0; i < scrollWidgets.size(); i++) {
			AbstractWidget widget = scrollWidgets.get(i);
			widget.extractRenderState(context, mouseX, mouseY, delta);
			UIDraw.text(context, rowLabels.get(i), contentX, UIDraw.flatContentY(widget.getY(), widget.getHeight()), UITheme.Colors.TEXT_PRIMARY);
		}
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
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(parent);
		}
	}
}
