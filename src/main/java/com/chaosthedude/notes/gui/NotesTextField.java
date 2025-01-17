package com.chaosthedude.notes.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.chaosthedude.notes.util.RenderUtils;
import com.chaosthedude.notes.util.StringUtils;
import com.chaosthedude.notes.util.WrappedString;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class NotesTextField extends Screen implements IGuiEventListener {

	private static final Minecraft mc = Minecraft.getInstance();

	private int margin;
	private String text;
	private int topVisibleLine;
	private int bottomVisibleLine;
	private int maxVisibleLines;
	private int wrapWidth;
	private final FontRenderer fontRenderer;
	public int xPosition;
	public int yPosition;
	public int width;
	public int height;
	private int cursorCounter;
	private boolean canLoseFocus = true;
	private boolean isFocused;
	private boolean isEnabled = true;
	private int cursorPos;
	private int selectionPos;
	private int enabledColor = 14737632;
	private int disabledColor = 7368816;
	private boolean visible = true;

	public NotesTextField(FontRenderer fontRenderer, int x, int y, int width, int height, int margin) {
		super(new StringTextComponent(""));
		this.fontRenderer = fontRenderer;
		this.xPosition = x;
		this.yPosition = y;
		this.width = width;
		this.height = height;
		this.margin = margin;

		text = "";
		maxVisibleLines = MathHelper.floor((height - (margin * 2)) / fontRenderer.FONT_HEIGHT) - 1;
		wrapWidth = width - (margin * 2);
		selectionPos = -1;
	}

	@Override
	public boolean keyPressed(int keyCode, int par2, int par3) {
		if (Screen.isCopy(keyCode)) {
			mc.keyboardListener.setClipboardString(getSelectedText());
		} else if (Screen.isCut(keyCode)) {
			if (getSelectionDifference() != 0) {
				mc.keyboardListener.setClipboardString(getSelectedText());
				deleteSelectedText();
			}
		} else if (Screen.isPaste(keyCode)) {
			insert(mc.keyboardListener.getClipboardString());
		} else if (isKeyComboCtrlBack(keyCode)) {
			deletePrevWord();
		} else {
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				if (getSelectionDifference() != 0) {
					deleteSelectedText();
				} else {
					deletePrev();
				}
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_DELETE) {
				if (getSelectionDifference() != 0) {
					deleteSelectedText();
				} else {
					deleteNext();
				}
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_TAB) {
				insert("    ");
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				insertNewLine();
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_ENTER) {
				insertNewLine();
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_HOME) {
				updateSelectionPos();
				setCursorPos(0);
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_END) {
				updateSelectionPos();
				setCursorPos(text.length());
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_UP) {
				updateSelectionPos();
				moveUp();
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_DOWN) {
				updateSelectionPos();
				moveDown();
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_LEFT) {
				boolean moveLeft = true;
				if (Screen.hasShiftDown()) {
					if (selectionPos < 0) {
						selectionPos = cursorPos;
					}
				} else {
					if (selectionPos > -1) {
						setCursorPos(getSelectionStart());
						moveLeft = false;
					}
					selectionPos = -1;
				}

				if (moveLeft) {
					moveLeft();
				}
				return true;
			} else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
				boolean moveRight = true;
				if (Screen.hasShiftDown()) {
					if (selectionPos < 0) {
						selectionPos = cursorPos;
					}
				} else {
					if (selectionPos > -1) {
						setCursorPos(getSelectionEnd());
						moveRight = false;
					}
					selectionPos = -1;
				}

				if (moveRight) {
					moveRight();
				}
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean charTyped(char typedChar, int p_charTyped_2_) {
	      if (isFocused()) {
	         if (SharedConstants.isAllowedCharacter(typedChar)) {
	            if (this.isEnabled) {
	               insert(Character.toString(typedChar));
	               updateVisibleLines();
	            }

	            return true;
	         }
	      }
	      return false;
	   }

	@Override
	public void render(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
		RenderUtils.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 255 / 2 << 24);

		renderVisibleText(stack);
		renderCursor();
		renderScrollBar();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		final boolean isWithinBounds = isWithinBounds(mouseX, mouseY);
		if (canLoseFocus) {
			setFocused(isWithinBounds);
		}

		if (isFocused && isWithinBounds) {
			if (mouseButton == 0) {
				final int relativeMouseX = (int) mouseX - xPosition - margin;
				final int relativeMouseY = (int) mouseY - yPosition - margin;
				final int y = MathHelper.clamp((relativeMouseY / fontRenderer.FONT_HEIGHT) + topVisibleLine, 0, getFinalLineIndex());
				final int x = fontRenderer.func_238413_a_(getLine(y), relativeMouseX, false).length();

				setCursorPos(countCharacters(y) + x);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int state) {
		final boolean isWithinBounds = isWithinBounds(mouseX, mouseY);
		if (canLoseFocus) {
			setFocused(isWithinBounds);
		}

		if (isFocused && isWithinBounds) {
			if (state == 0) {
				final int relativeMouseX = (int) mouseX - xPosition - margin;
				final int relativeMouseY = (int) mouseY - yPosition - margin;
				final int y = MathHelper.clamp((relativeMouseY / fontRenderer.FONT_HEIGHT) + topVisibleLine, 0, getFinalLineIndex());
				final int x = fontRenderer.func_238413_a_(getLine(y), relativeMouseX, false).length();

				final int pos = MathHelper.clamp(countCharacters(y) + x, 0, text.length());
				if (pos != cursorPos) {
					selectionPos = cursorPos;
					setCursorPos(pos);
				} else {
					selectionPos = -1;
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double par1, double par2, double par3) {
		if (par1 < 0) {
			incrementVisibleLines();
			return true;
		} else if (par1 > 0) {
			decrementVisibleLines();
			return true;
		}
		return false;
	}

	public void tick() {
		cursorCounter++;
	}

	public List<String> toLines() {
		return StringUtils.wrapToWidth(text, wrapWidth);
	}

	public List<WrappedString> toLinesWithIndication() {
		return StringUtils.wrapToWidthWithIndication(text, wrapWidth);
	}

	public String getLine(int line) {
		return line >= 0 && line < toLines().size() ? toLines().get(line) : getFinalLine();
	}

	public String getFinalLine() {
		return getLine(getFinalLineIndex());
	}

	public String getCurrentLine() {
		return getLine(getCursorY());
	}

	public List<String> getVisibleLines() {
		final List<String> lines = toLines();
		final List<String> visibleLines = new ArrayList<String>();
		for (int i = topVisibleLine; i <= bottomVisibleLine; i++) {
			if (i < lines.size()) {
				visibleLines.add(lines.get(i));
			}
		}

		return visibleLines;
	}

	public String getText() {
		return text;
	}

	public void setText(String newText) {
		text = newText;
		updateVisibleLines();
	}

	public int getFinalLineIndex() {
		return toLines().size() - 1;
	}

	public boolean cursorIsValid() {
		final int y = getCursorY();
		return y >= topVisibleLine && y <= bottomVisibleLine;
	}

	public int getRenderSafeCursorY() {
		return getCursorY() - topVisibleLine;
	}

	public int getAbsoluteBottomVisibleLine() {
		return topVisibleLine + (maxVisibleLines - 1);
	}

	public int getCursorWidth(int pos) {
		final String line = getCurrentLine();
		return fontRenderer.getStringWidth(line.substring(0, MathHelper.clamp(getCursorX(), 0, line.length())));
	}

	public int getCursorWidth() {
		return getCursorWidth(cursorPos);
	}

	public boolean isWithinBounds(double mouseX, double mouseY) {
		return mouseX >= xPosition && mouseX < xPosition + width && mouseY >= yPosition && mouseY < yPosition + height;
	}

	public boolean atBeginningOfLine() {
		return getCursorX() == 0;
	}

	public boolean atEndOfLine() {
		return getCursorX() == getCurrentLine().length();
	}

	public boolean atBeginningOfNote() {
		return cursorPos == 0;
	}

	public boolean atEndOfNote() {
		return cursorPos >= text.length();
	}

	public int getVisibleLineCount() {
		return bottomVisibleLine - topVisibleLine + 1;
	}

	public void updateVisibleLines() {
		while (getVisibleLineCount() <= maxVisibleLines && bottomVisibleLine < getFinalLineIndex()) {
			bottomVisibleLine++;
		}
	}

	public boolean needsScrollBar() {
		return toLines().size() > getVisibleLineCount();
	}

	public int getWidth() {
		return width - (margin * 2);
	}

	public boolean isFocused() {
		return isFocused;
	}

	public void setFocused(boolean focused) {
		if (focused && !isFocused) {
			cursorCounter = 0;
		}

		isFocused = focused;
	}

	public boolean isKeyComboCtrlBack(int keyCode) {
		return keyCode == GLFW.GLFW_KEY_BACKSPACE && Screen.hasControlDown() && !Screen.hasShiftDown() && !Screen.hasAltDown();
	}

	public void insert(String newText) {
		deleteSelectedText();

		final String finalText = StringUtils.insertStringAt(StringUtils.filter(newText), text, cursorPos);
		setText(finalText);
		moveCursorPosBy(newText.length());
	}

	public void insertNewLine() {
		insert(String.valueOf('\n'));
	}

	private void deleteNext() {
		final String currentText = text;
		if (!atEndOfNote() && !currentText.isEmpty()) {
			final StringBuilder sb = new StringBuilder(currentText);
			sb.deleteCharAt(cursorPos);
			setText(sb.toString());
			selectionPos--;
		}
	}

	private void deletePrev() {
		final String currentText = text;
		if (!atBeginningOfNote() && !currentText.isEmpty()) {
			final StringBuilder sb = new StringBuilder(currentText);
			sb.deleteCharAt(cursorPos - 1);
			setText(sb.toString());
			moveLeft();
		}
	}

	private void deletePrevWord() {
		if (!atBeginningOfNote()) {
			char prev = text.charAt(cursorPos - 1);
			if (prev == ' ') {
				while (prev == ' ') {
					deletePrev();
					if (atBeginningOfNote()) {
						return;
					}
					prev = text.charAt(cursorPos - 1);
				}
			} else {
				while (prev != ' ') {
					deletePrev();
					if (atBeginningOfNote()) {
						return;
					}
					prev = text.charAt(cursorPos - 1);
				}
			}
		}
	}

	private void deleteSelectedText() {
		while (getSelectionDifference() > 0) {
			deletePrev();
		}

		while (getSelectionDifference() < 0) {
			deleteNext();
		}

		selectionPos = -1;
	}

	private void incrementVisibleLines() {
		if (bottomVisibleLine < getFinalLineIndex()) {
			topVisibleLine++;
			bottomVisibleLine++;
		}
	}

	private void decrementVisibleLines() {
		if (topVisibleLine > 0) {
			topVisibleLine--;
			bottomVisibleLine--;
		}
	}

	private int countCharacters(int maxLineIndex) {
		final List<WrappedString> wrappedLines = toLinesWithIndication();
		int count = 0;
		for (int i = 0; i < maxLineIndex; i++) {
			final WrappedString wrappedLine = wrappedLines.get(i);
			count += wrappedLine.getText().length();
			if (!wrappedLine.isWrapped()) {
				count++;
			}
		}

		return count;
	}

	private int getCursorX(int pos) {
		final List<WrappedString> wrappedLines = toLinesWithIndication();
		final int y = getCursorY();
		boolean currentLineIsWrapped = false;
		int count = 0;
		for (int i = 0; i <= y; i++) {
			if (i < wrappedLines.size()) {
				final WrappedString wrappedLine = wrappedLines.get(i);
				if (i < y) {
					count += wrappedLine.getText().length();
					if (!wrappedLine.isWrapped()) {
						count++;
					}
				}

				if (wrappedLine.isWrapped()) {
					if (i == y && i > 0) {
						currentLineIsWrapped = true;
					}
				}
			}
		}

		if (currentLineIsWrapped) {
			count--;
		}

		return pos - count;
	}

	private int getCursorX() {
		return getCursorX(cursorPos);
	}

	private int getCursorY(int pos) {
		final List<WrappedString> wrappedLines = toLinesWithIndication();
		int count = 0;
		for (int i = 0; i < wrappedLines.size(); i++) {
			final WrappedString wrappedLine = wrappedLines.get(i);
			count += wrappedLine.getText().length();
			if (!wrappedLine.isWrapped()) {
				count++;
			}

			if (count > pos) {
				return i;
			}
		}

		return getFinalLineIndex();
	}

	private int getCursorY() {
		return getCursorY(cursorPos);
	}

	private int getSelectionDifference() {
		return selectionPos > -1 ? cursorPos - selectionPos : 0;
	}

	private boolean hasSelectionOnLine(int line) {
		if (selectionPos > -1) {
			final List<WrappedString> wrappedLines = toLinesWithIndication();
			int count = 0;
			for (int i = 0; i <= line; i++) {
				final WrappedString wrappedLine = wrappedLines.get(i);
				for (int j = 0; j < wrappedLine.getText().length(); j++) {
					count++;
					if (line == i && isInSelection(count)) {
						return true;
					}
				}

				if (!wrappedLine.isWrapped()) {
					count++;
				}
			}
		}

		return false;
	}

	private void setCursorPos(int pos) {
		cursorPos = MathHelper.clamp(pos, 0, text.length());
		if (getCursorY() > bottomVisibleLine) {
			incrementVisibleLines();
		} else if (getCursorY() < topVisibleLine) {
			decrementVisibleLines();
		}
	}

	private void moveCursorPosBy(int amount) {
		setCursorPos(cursorPos + amount);
	}

	private void moveRight() {
		if (!atEndOfNote()) {
			moveCursorPosBy(1);
		}
	}

	private void moveLeft() {
		if (!atBeginningOfNote()) {
			moveCursorPosBy(-1);
		}
	}

	private void moveUp() {
		final int width = getCursorWidth();
		final int y = getCursorY();
		while (cursorPos > 0 && (getCursorY() == y || getCursorWidth() > width)) {
			moveLeft();
		}
	}

	private void moveDown() {
		final int width = getCursorWidth();
		final int y = getCursorY();
		while (cursorPos < text.length() && (getCursorY() == y || getCursorWidth() < width)) {
			moveRight();
		}
	}

	private void updateSelectionPos() {
		if (Screen.hasShiftDown()) {
			if (selectionPos < 0) {
				selectionPos = cursorPos;
			}
		} else {
			selectionPos = -1;
		}
	}

	private boolean isInSelection(int pos) {
		if (selectionPos > -1) {
			return pos >= getSelectionStart() && pos <= getSelectionEnd();
		}

		return false;
	}

	private int getSelectionStart() {
		if (selectionPos > -1) {
			if (selectionPos > cursorPos) {
				return cursorPos;
			} else if (cursorPos > selectionPos) {
				return selectionPos;
			}
		}

		return -1;
	}

	private int getSelectionEnd() {
		if (selectionPos > -1) {
			if (selectionPos > cursorPos) {
				return selectionPos;
			} else if (cursorPos > selectionPos) {
				return cursorPos;
			}
		}

		return -1;
	}

	private String getSelectedText() {
		if (getSelectionStart() >= 0 && getSelectionEnd() >= 0) {
			return text.substring(getSelectionStart(), getSelectionEnd());
		}

		return "";
	}

	private void drawSelectionBox(int startX, int startY, int endX, int endY) {
		if (startX < endX) {
			final int temp = startX;
			startX = endX;
			endX = temp;
		}

		if (startY < endY) {
			final int temp = startY;
			startY = endY;
			endY = temp;
		}

		if (endX > xPosition + width) {
			endX = xPosition + width;
		}

		if (startX > xPosition + width) {
			startX = xPosition + width;
		}

		final Tessellator tessellator = Tessellator.getInstance();
		final BufferBuilder buffer = tessellator.getBuffer();

		RenderSystem.color4f(0.0F, 0.0F, 255.0F, 255.0F);
		RenderSystem.disableTexture();
		RenderSystem.enableColorLogicOp();
		RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);

		buffer.begin(7, DefaultVertexFormats.POSITION);
		buffer.pos(startX, endY, 0.0D).endVertex();
		buffer.pos(endX, endY, 0.0D).endVertex();
		buffer.pos(endX, startY, 0.0D).endVertex();
		buffer.pos(startX, startY, 0.0D).endVertex();
		tessellator.draw();

		RenderSystem.disableColorLogicOp();
		RenderSystem.enableTexture();
	}

	private void renderSelectionBox(int y, int renderY, String line) {
		if (hasSelectionOnLine(y)) {
			final String absoluteLine = getLine(y);
			int count = 0;
			final List<WrappedString> wrappedLines = toLinesWithIndication();
			for (int i = 0; i < y; i++) {
				final WrappedString wrappedLine = wrappedLines.get(i);
				count += wrappedLine.getText().length();
				if (!wrappedLine.isWrapped()) {
					count++;
				}
			}

			if (wrappedLines.get(y).isWrapped()) {
				count--;
			}

			int start = getSelectionStart() - count;
			if (start < 0) {
				start = 0;
			}

			int end = getSelectionEnd() - count;
			if (end > line.length()) {
				end = line.length();
			}

			if (start >= end) {
				selectionPos = -1;
			} else {
				final String selection = absoluteLine.substring(start, end);
				final int startX = xPosition + margin + fontRenderer.getStringWidth(absoluteLine.substring(0, start));
				final int endX = startX + fontRenderer.getStringWidth(selection);
				drawSelectionBox(startX, renderY, endX, renderY + fontRenderer.FONT_HEIGHT);
			}
		}
	}

	private void renderVisibleText(MatrixStack stack) {
		int renderY = yPosition + margin;
		int y = topVisibleLine;
		for (String line : getVisibleLines()) {
			fontRenderer.drawStringWithShadow(stack, line, xPosition + margin, renderY, 14737632);
			renderSelectionBox(y, renderY, line);

			renderY += fontRenderer.FONT_HEIGHT;
			y++;
		}
	}

	private void renderCursor() {
		final boolean shouldDisplayCursor = isFocused && cursorCounter / 6 % 2 == 0 && cursorIsValid();
		if (shouldDisplayCursor) {
			final String line = getCurrentLine();
			final int renderCursorX = xPosition + margin + fontRenderer.getStringWidth(line.substring(0, MathHelper.clamp(getCursorX(), 0, line.length())));
			final int renderCursorY = yPosition + margin + (getRenderSafeCursorY() * fontRenderer.FONT_HEIGHT);

			RenderUtils.drawRect(renderCursorX, renderCursorY - 1, renderCursorX + 1, renderCursorY + fontRenderer.FONT_HEIGHT + 1, -3092272);
		}
	}

	private void renderScrollBar() {
		if (needsScrollBar()) {
			final List<String> lines = toLines();
			final int effectiveHeight = height - (margin / 2);
			final int scrollBarHeight = MathHelper.floor(effectiveHeight * ((double) getVisibleLineCount() / lines.size()));
			int scrollBarTop = yPosition + (margin / 4) + MathHelper.floor(((double) topVisibleLine / lines.size()) * effectiveHeight);

			final int diff = (scrollBarTop + scrollBarHeight) - (yPosition + height);
			if (diff > 0) {
				scrollBarTop -= diff;
			}

			RenderUtils.drawRect(xPosition + width - (margin * 3 / 4), scrollBarTop, xPosition + width - (margin / 4), scrollBarTop + scrollBarHeight, -3092272);
		}
	}

}
