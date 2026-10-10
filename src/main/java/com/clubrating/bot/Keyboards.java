package com.clubrating.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

public final class Keyboards {

    private Keyboards() {
    }

    public static InlineKeyboardButton btn(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    public static InlineKeyboardMarkup kb(List<List<InlineKeyboardButton>> rows) {
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup kb(InlineKeyboardButton... singleRow) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(List.of(singleRow))).build();
    }
}
