package com.uospd.utils;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class KeyboardBuilder{
    private final InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
    private final List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    private int buttonOnRowLimit=20;

    {
        inlineKeyboardMarkup.setKeyboard(rows);
        nextRow();
    }

    public KeyboardBuilder(){ }

    public KeyboardBuilder(int buttonOnRowLimit){
        this.buttonOnRowLimit = buttonOnRowLimit;
    }

    public static InlineKeyboardMarkup oneButtonKeyboard(String text,String action){
        KeyboardBuilder builder = new KeyboardBuilder();
        builder.addButtonOnRow(text,action);
        return builder.build();
    }

    public List<List<InlineKeyboardButton>> getRows(){
        return rows;
    }

    public KeyboardBuilder nextRow(){
        rows.add(new ArrayList<>());
        return this;
    }

    public KeyboardBuilder addButtonOnRow(String buttonLabel,String buttonAction){
        if(rows.get(rows.size() - 1).size() >= buttonOnRowLimit) nextRow();
        InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
        keyboardButton.setText(buttonLabel);
        keyboardButton.setCallbackData(buttonAction);
        rows.get(rows.size() - 1).add(keyboardButton);
        return this;
    }

    public KeyboardBuilder addButtonNextRow(String buttonLabel,String buttonAction){
        nextRow();
        addButtonOnRow(buttonLabel,buttonAction);
        return this;
    }

    public InlineKeyboardMarkup build(){
        return inlineKeyboardMarkup;
    }

    public int getRowsCount(){
        return rows.size();
    }

}
