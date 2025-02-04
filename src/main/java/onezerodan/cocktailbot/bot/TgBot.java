package onezerodan.cocktailbot.bot;

import onezerodan.cocktailbot.model.Cocktail;
import onezerodan.cocktailbot.service.CocktailService;
import onezerodan.cocktailbot.util.PropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TgBot extends TelegramLongPollingBot {

    Logger log = LoggerFactory.getLogger(TgBot.class);
    Properties properties = new PropertiesLoader().getProperties("bot");
    ConcurrentHashMap<Long, UserStates> userStatesMap = new ConcurrentHashMap<>();
    public TgBot(CocktailService cocktailService) {
        this.cocktailService = cocktailService;
    }

    @Autowired
    private CocktailService cocktailService;

    @Override
    public String getBotUsername() {
        return properties.getProperty("username");
    }

    @Override
    public String getBotToken() {
        return properties.getProperty("token");
    }

    @Override
    public void onUpdateReceived(Update update) {


        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }

        else if (update.hasMessage()){

            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText().toLowerCase();
            log.info("\n---NEW MESSAGE---\nFROM: "+chatId +"\nTEXT: " + text);

            switch (text) {
                case "/start":
                    sendMainMenu(chatId);
                    return;

                case "/tags":
                    sendMessage(chatId, "Список всех доступных тэгов:\n" + String.join("\n", cocktailService.getAllAvailableTags()));
                    return;

            }

            if (userStatesMap.get(chatId) != null) {
                switch (UserStates.valueOf(userStatesMap.get(chatId).toString())) {
                    case SEARCHING_BY_NAME:
                        searchCocktailsByName(text, chatId);
                        break;
                    case SEARCHING_BY_INGREDIENTS:
                        searchCocktailByIngredients(text, chatId);
                        break;
                    case SEARCHING_BY_TAGS:
                        searchCocktailByTags(text, chatId);
                        break;
                }
            }



        }

    }


    private void handleCallbackQuery(Update update) {

        Long chatId = update.getCallbackQuery().getFrom().getId();
        String callbackQuery = update.getCallbackQuery().getData();

        log.info("\n---NEW CALLBACK---\nFROM: "+chatId +"\nDATA: " + callbackQuery);

        if (callbackQuery.startsWith("ck_")) {
            long cocktailId = Long.parseLong(update.getCallbackQuery().getData().split("_")[1]);
            searchCocktailById(cocktailId, chatId);
        }

        else if (callbackQuery.startsWith("goto_")) {
            String dest = update.getCallbackQuery().getData().split("_")[1];
            switch (dest) {
                case "mainMenu":
                    sendMainMenu(chatId);
                    return;
            }
        }
        else if (callbackQuery.startsWith("search_")) {
            String searchParameter = callbackQuery.split("_")[1];
            switch (searchParameter){
                case "byName":
                    userStatesMap.put(chatId, UserStates.SEARCHING_BY_NAME);
                    sendMessage(chatId, "Введите название интересующего коктейля.");
                    break;

                case "byIngredients":
                    userStatesMap.put(chatId, UserStates.SEARCHING_BY_INGREDIENTS);
                    sendMessage(chatId, "Введите интересующие ингредиенты через запятую.");
                    break;

                case "byTags":
                    userStatesMap.put(chatId, UserStates.SEARCHING_BY_TAGS);
                    sendMessage(chatId, "Введите интересующие тэги через запятую.\nЧтобы посмотреть список всех доступных тэгов, отправьте команду \n/tags");
                    break;
            }


        }
    }

    private void sendCocktailsInline(List<Cocktail> cocktails, Long chatId, String text)  {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();


        for (Cocktail cocktail : cocktails) {
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(cocktail.getName());
            //ck (shortened form of cocktail), because of telegram callback size limit
            btn.setCallbackData("ck_" + cocktail.getId());
            rowInline.add(btn);
            rowsInline.add(rowInline);
        }

        // Randomize each output
        Collections.shuffle(rowsInline);
        markupInline.setKeyboard(rowsInline);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        msg.setReplyMarkup(markupInline);

        try{
            execute(msg);
        } catch (TelegramApiException e) {
            log.error(e.toString());
        }

    }

    private void searchCocktailByName(String name, Long chatId) {
        sendCocktail(cocktailService.findByName(name), chatId);
    }

    private void searchCocktailById(Long cocktailId, Long chatId) {
        sendCocktail(cocktailService.findById(cocktailId), chatId);
    }

    private void sendCocktail(Cocktail cocktail, Long chatId) {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText("🏠 Главное меню");
        btn.setCallbackData("goto_mainMenu");
        rowInline.add(btn);
        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(cocktail.toString());
        msg.setReplyMarkup(markupInline);

        try{
            execute(msg);
        } catch (TelegramApiException e) {
            log.error(e.toString());
        }
    }

    private void sendMainMenu(Long chatId) {

        userStatesMap.remove(chatId);

        Map<String, String> menuButtons = new LinkedHashMap<>();
        menuButtons.put("🍹Поиск по названию", "search_byName");
        menuButtons.put("🍊Поиск по ингредиентам", "search_byIngredients");
        menuButtons.put("🔖Поиск по тэгам", "search_byTags");
        sendMessageWithInlineKeyboard(chatId, "Салют! Ты можешь искать коктейли по названиям, ингредиентам и тэгам! \nВыбери способ поиска:", menuButtons);
    }

    private void searchCocktailsByName(String name, Long chatId) {

        List<Cocktail> cocktails = cocktailService.findAllByName(name);
        if (cocktails.size() == 1) {
            for (Cocktail cocktail : cocktails) {
                sendCocktail(cocktail, chatId);
            }
        }
        else if (cocktails.size() > 1) {
            sendCocktailsInline(cocktails, chatId, "По вашему запросу найдено несколько коктейлей:");
        }

        else {

            List<Cocktail> suggestions = cocktailService.suggestIfNotFound(name);
            if (suggestions.size() > 0) {
                sendCocktailsInline(suggestions, chatId, "Возможно, вы имели ввиду: ");
            }
            else {
                sendMessage(chatId, "По вашему запросу ничего не найдено. Повторите запрос.");
                userStatesMap.put(chatId, UserStates.SEARCHING_BY_NAME);
            }
        }

    }
    private void searchCocktailByIngredients(String ingredients, Long chatId) {
        List<String> ingredientsList = Arrays.asList(ingredients.split("\\s*,\\s*"));
        List<Cocktail> cocktails = cocktailService.findByIngredientsAll(ingredientsList);

        if (cocktails.size() > 0) {
            sendCocktailsInline(cocktailService.findByIngredientsAll(ingredientsList), chatId, "По вашему запросу найдены следующие коктейли:");
        }
        else {
            sendMessage(chatId, "По вашему запросу ничего не найдено.");
        }


    }

    private void searchCocktailByTags(String tags, Long chatId) {
        List<String> tagsList = Arrays.asList(tags.split("\\s*,\\s*"));

        List<Cocktail> cocktails = cocktailService.findByTags(tagsList);

        if (cocktails.size() > 0) {
            sendCocktailsInline(cocktailService.findByTags(tagsList), chatId, "По вашему запросу найдены следующие коктейли:");
        }
        else {
            sendMessage(chatId, "По вашему запросу ничего не найдено.");
        }

    }

    private void sendMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);

        try{
            execute(msg);
        } catch (TelegramApiException e) {
            log.error(e.toString());
        }
    }

    private void sendMessageWithInlineKeyboard(Long chatId, String text, Map<String, String> buttons) {

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (Map.Entry<String, String> entry : buttons.entrySet()) {
            String btnText = entry.getKey();
            String btnCallback = entry.getValue();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(btnText);
            btn.setCallbackData(btnCallback);
            rowInline.add(btn);
            rowsInline.add(rowInline);
        }
        markupInline.setKeyboard(rowsInline);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        msg.setReplyMarkup(markupInline);

        try{
            execute(msg);
        } catch (TelegramApiException e) {
            log.error(e.toString());
        }
    }
}
