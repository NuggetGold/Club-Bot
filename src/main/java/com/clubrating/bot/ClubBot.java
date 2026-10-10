package com.clubrating.bot;

import com.clubrating.config.Config;
import com.clubrating.model.ClubEvent;
import com.clubrating.model.Lecture;
import com.clubrating.model.Member;
import com.clubrating.repo.AttendanceRepository;
import com.clubrating.repo.EventRepository;
import com.clubrating.repo.LectureRepository;
import com.clubrating.repo.MemberRepository;
import com.clubrating.repo.ResultRepository;
import com.clubrating.service.ExcelService;
import com.clubrating.service.RatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClubBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(ClubBot.class);
    private static final int MEMBERS_PAGE_SIZE = 20;
    private static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    private enum State {
        NONE, REGISTER, NEW_LECTURE, NEW_EVENT
    }

    private final Config cfg;
    private final MemberRepository members;
    private final LectureRepository lectures;
    private final EventRepository events;
    private final AttendanceRepository attendance;
    private final ResultRepository results;
    private final RatingService ratingService;
    private final ExcelService excelService;
    private final Map<Long, State> sessions = new ConcurrentHashMap<>();
    private volatile String resolvedUsername;

    public ClubBot(Config cfg,
                   MemberRepository members,
                   LectureRepository lectures,
                   EventRepository events,
                   AttendanceRepository attendance,
                   ResultRepository results,
                   RatingService ratingService,
                   ExcelService excelService) {
        this.cfg = cfg;
        this.members = members;
        this.lectures = lectures;
        this.events = events;
        this.attendance = attendance;
        this.results = results;
        this.ratingService = ratingService;
        this.excelService = excelService;
    }

    @Override
    public String getBotToken() {
        return cfg.botToken;
    }

    @Override
    public String getBotUsername() {
        if (resolvedUsername != null) {
            return resolvedUsername;
        }
        if (!cfg.botUsername.isBlank()) {
            resolvedUsername = cfg.botUsername;
            return resolvedUsername;
        }
        try {
            resolvedUsername = execute(GetMe.builder().build()).getUserName();
        } catch (Exception e) {
            log.warn("не удалось получить юзернейм бота через getMe", e);
            resolvedUsername = "";
        }
        return resolvedUsername;
    }

    public void registerCommands() throws TelegramApiException {
        execute(SetMyCommands.builder()
                .commands(List.of(
                        new BotCommand("start", "Регистрация и главное меню"),
                        new BotCommand("menu", "Главное меню"),
                        new BotCommand("help", "Справка")
                ))
                .build());
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update);
            } else if (update.hasMessage()) {
                Message message = update.getMessage();
                if (message.hasDocument()) {
                    handleDocument(message);
                } else if (message.hasText()) {
                    handleText(message);
                }
            }
        } catch (Exception e) {
            log.error("update handling failed", e);
            if (update.hasMessage()) {
                send(update.getMessage().getChatId(), "⚠️ Что-то пошло не так. Нажмите /start и попробуйте снова.", null);
            }
        }
    }

    private void handleText(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        boolean isPrivate = isPrivate(message);
        String raw = message.getText().trim();
        String command = raw.split("@")[0].toLowerCase(Locale.ROOT);

        if (command.equals("/start")) {
            sessions.remove(userId);
            if (!isPrivate) {
                send(chatId, "Чтобы зарегистрироваться, напишите мне в личные сообщения 👉 @" + getBotUsername(), null);
                return;
            }
            members.findByTelegramId(userId).ifPresentOrElse(
                    m -> send(chatId, welcomeBack(m), mainMenu(userId)),
                    () -> {
                        sessions.put(userId, State.REGISTER);
                        send(chatId, "👋 Привет!\n\nКак вас зовут? Просто напишите имя.", null);
                    });
            return;
        }
        if (command.equals("/menu") || command.equals("/help")) {
            sessions.remove(userId);
            send(chatId, "🏠 <b>Главное меню</b>\n\nКуда заглянем?", mainMenu(userId));
            return;
        }
        if (command.startsWith("/")) {
            sessions.remove(userId);
            send(chatId, "Не знаю такую команду 🤔 Откройте /menu", null);
            return;
        }

        State state = sessions.getOrDefault(userId, State.NONE);
        switch (state) {
            case REGISTER -> {
                sessions.remove(userId);
                if (!isPrivate) {
                    return;
                }
                registerUser(chatId, userId, raw);
            }
            case NEW_LECTURE -> {
                sessions.remove(userId);
                if (!requireAdmin(chatId, userId)) {
                    return;
                }
                String title = truncate(raw, 200);
                if (lectures.findByTitle(title).isPresent()) {
                    send(chatId, "Такая лекция уже есть.", adminLecturesKeyboard());
                    return;
                }
                lectures.create(title);
                send(chatId, "✅ Лекция «" + Ui.esc(title) + "» добавлена.", adminLecturesKeyboard());
            }
            case NEW_EVENT -> {
                sessions.remove(userId);
                if (!requireAdmin(chatId, userId)) {
                    return;
                }
                String title = truncate(raw, 200);
                if (events.findByTitle(title).isPresent()) {
                    send(chatId, "Такое мероприятие уже есть.", adminEventsKeyboard());
                    return;
                }
                events.create(title);
                send(chatId, "✅ Мероприятие «" + Ui.esc(title) + "» добавлено.", adminEventsKeyboard());
            }
            default -> {
                if (isPrivate) {
                    send(chatId, "🏠 <b>Главное меню</b>\n\nКуда заглянем?", mainMenu(userId));
                }
            }
        }
    }

    private void registerUser(long chatId, long userId, String name) {
        String trimmed = name.trim();
        if (trimmed.isBlank() || trimmed.length() > 100) {
            sessions.put(userId, State.REGISTER);
            send(chatId, "Имя не подошло — нужно до 100 символов. Напишите ещё раз.", null);
            return;
        }
        if (members.findByTelegramId(userId).isPresent()) {
            send(chatId, "Вы уже зарегистрированы 👍", mainMenu(userId));
            return;
        }
        String normalized = truncate(trimmed, 120);
        Member member = members.findByNameNotLinked(normalized)
                .filter(existing -> members.linkTelegram(existing.id(), userId))
                .map(existing -> new Member(existing.id(), userId, existing.name()))
                .orElseGet(() -> members.create(userId, normalized));
        send(chatId, "🎉 Готово, <b>" + Ui.esc(member.name()) + "</b>!\n\nТеперь доступно:\n" +
                "👤 карточка — лекции и места\n🏆 рейтинг клуба", mainMenu(userId));
    }

    private InlineKeyboardMarkup mainMenu(long userId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                Keyboards.btn("👤 Моя карточка", "uc"),
                Keyboards.btn("🏆 Рейтинг", "ur:0")));
        if (cfg.isAdmin(userId)) {
            rows.add(List.of(Keyboards.btn("⚙ Админ-панель", "am")));
        }
        return Keyboards.kb(rows);
    }

    private String welcomeBack(Member m) {
        return "👋 С возвращением, <b>" + Ui.esc(m.name()) + "</b>!";
    }

    private void handleCallback(Update update) {
        var cb = update.getCallbackQuery();
        long userId = cb.getFrom().getId();
        String data = cb.getData() == null ? "" : cb.getData();

        if (cb.getMessage() != null) {
            sessions.remove(userId);
        }

        if (data.equals("m")) {
            answer(cb.getId());
            long chatId = cb.getMessage().getChatId();
            edit(chatId, cb.getMessage().getMessageId(),
                    "🏠 <b>Главное меню</b>\n\nКуда заглянем?", mainMenu(userId));
            return;
        }

        if (cb.getMessage() == null) {
            answer(cb.getId(), "Откройте меню заново 🙏");
            return;
        }
        long chatId = cb.getMessage().getChatId();
        int messageId = cb.getMessage().getMessageId();

        if (data.equals("uc")) {
            answer(cb.getId());
            showCard(chatId, messageId, userId);
            return;
        }
        if (data.startsWith("ur")) {
            answer(cb.getId());
            int page = 0;
            if (data.startsWith("ur:")) {
                page = parseint(data.substring(3), 0);
            }
            showRating(chatId, messageId, page);
            return;
        }

        if (!cfg.isAdmin(userId)) {
            answer(cb.getId(), "Только для админа 🙅");
            return;
        }
        answer(cb.getId());

        try {
            if (data.equals("am")) {
                edit(chatId, messageId, "⚙ <b>Админ-панель</b>\n\nЧто делаем?", adminMenuKeyboard());
            } else if (data.startsWith("au")) {
                int page = data.startsWith("au:") ? parseint(data.substring(3), 0) : 0;
                showMembers(chatId, messageId, page);
            } else if (data.startsWith("um:")) {
                showMemberAdmin(chatId, messageId, parseLong(data.substring(3)));
            } else if (data.startsWith("ut:")) {
                String[] p = data.split(":");
                if (p.length < 3) {
                    edit(chatId, messageId, "Кнопка устарела — откройте список заново.", null);
                    return;
                }
                long memberId = parseLong(p[1]);
                long lectureId = parseLong(p[2]);
                if (members.findById(memberId).isEmpty() || lectures.findById(lectureId).isEmpty()) {
                    edit(chatId, messageId, "Лекция или участник уже удалены.", null);
                    return;
                }
                attendance.toggle(memberId, lectureId);
                showMemberAdmin(chatId, messageId, memberId);
            } else if (data.startsWith("ue:")) {
                showMemberEvents(chatId, messageId, parseLong(data.substring(3)));
            } else if (data.startsWith("uf:")) {
                String[] p = data.split(":");
                if (p.length < 3) {
                    edit(chatId, messageId, "Кнопка устарела — откройте список заново.", null);
                    return;
                }
                showPlacePicker(chatId, messageId, parseLong(p[1]), parseLong(p[2]));
            } else if (data.startsWith("us:")) {
                String[] p = data.split(":");
                if (p.length < 4) {
                    edit(chatId, messageId, "Кнопка устарела — откройте список заново.", null);
                    return;
                }
                long memberId = parseLong(p[1]);
                long eventId = parseLong(p[2]);
                int place = parseint(p[3], -1);
                if (members.findById(memberId).isEmpty() || events.findById(eventId).isEmpty()) {
                    edit(chatId, messageId, "Участник или мероприятие не найдены.", null);
                    return;
                }
                if (place < 0) {
                    edit(chatId, messageId, "Не понял место — попробуйте заново.", null);
                    return;
                }
                if (place == 0) {
                    results.delete(eventId, memberId);
                } else {
                    results.upsert(eventId, memberId, place);
                }
                showPlacePicker(chatId, messageId, memberId, eventId);
            } else if (data.equals("af")) {
                edit(chatId, messageId, "📚 <b>Лекции</b>\n\nНажмите на лекцию, чтобы убрать её.", adminLecturesKeyboard());
            } else if (data.equals("nl")) {
                sessions.put(userId, State.NEW_LECTURE);
                send(chatId, "Как называется новая лекция? Напишите название.", null);
            } else if (data.startsWith("ld:")) {
                lectures.delete(parseLong(data.substring(3)));
                edit(chatId, messageId, "✅ Лекция удалена.\n\nНажмите на следующую, чтобы убрать её.", adminLecturesKeyboard());
            } else if (data.equals("ae")) {
                edit(chatId, messageId, "🗓 <b>Мероприятия</b>\n\nНажмите на мероприятие, чтобы убрать его.", adminEventsKeyboard());
            } else if (data.equals("ne")) {
                sessions.put(userId, State.NEW_EVENT);
                send(chatId, "Как называется мероприятие? Напишите название.", null);
            } else if (data.startsWith("ed:")) {
                events.delete(parseLong(data.substring(3)));
                edit(chatId, messageId, "✅ Мероприятие удалено.\n\nНажмите на следующее, чтобы убрать его.", adminEventsKeyboard());
            } else if (data.equals("ai")) {
                edit(chatId, messageId, "📄 <b>Импорт Excel</b>\n\n" +
                        "Пришлите файл .xlsx с двумя листами:\n" +
                        "• <b>" + ExcelService.SHEET_LECTURES + "</b> — колонка «" + ExcelService.COL_MEMBER +
                        "» и колонки лекций с отметками ✓ / да / 1\n" +
                        "• <b>" + ExcelService.SHEET_RESULTS + "</b> — колонки «" + ExcelService.COL_MEMBER + "», «"
                        + ExcelService.COL_EVENT + "», «" + ExcelService.COL_PLACE + "»\n\n" +
                        "Всё из файла попадёт в базу, рейтинг пересчитается сам.", null);
            } else if (data.equals("ax")) {
                sendExcel(chatId);
            } else {
                edit(chatId, messageId, "Кнопка устарела — откройте меню заново.", null);
            }
        } catch (Exception e) {
            log.error("callback {} failed", data, e);
            edit(chatId, messageId, "⚠️ Что-то пошло не так. Откройте меню заново.", null);
        }
    }

    private void showCard(long chatId, int messageId, long userId) {
        Member member = members.findByTelegramId(userId).orElse(null);
        if (member == null) {
            sessions.put(userId, State.REGISTER);
            edit(chatId, messageId, "Кажется, вы ещё не регистрировались. Просто напишите ваше имя.", null);
            return;
        }
        edit(chatId, messageId, Ui.formatCard(ratingService.statsFor(member.id())), kbBackToMenu());
    }

    private void showRating(long chatId, int messageId, int page) {
        List<String> pages = Ui.ratingPages(ratingService.leaderboard(), Ui.RATING_PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages.size() - 1));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (pages.size() > 1) {
            List<InlineKeyboardButton> nav = new ArrayList<>();
            if (page > 0) {
                nav.add(Keyboards.btn("◀️", "ur:" + (page - 1)));
            }
            nav.add(Keyboards.btn((page + 1) + "/" + pages.size(), "ur:" + page));
            if (page < pages.size() - 1) {
                nav.add(Keyboards.btn("▶️", "ur:" + (page + 1)));
            }
            rows.add(nav);
        }
        rows.add(List.of(Keyboards.btn("👤 Моя карточка", "uc"), Keyboards.btn("↩ Меню", "m")));
        edit(chatId, messageId, pages.get(page), Keyboards.kb(rows));
    }

    private void showMembers(long chatId, int messageId, int page) {
        List<Member> all = members.findAllOrdered();
        int totalPages = Math.max(1, (all.size() + MEMBERS_PAGE_SIZE - 1) / MEMBERS_PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        int from = page * MEMBERS_PAGE_SIZE;
        int to = Math.min(from + MEMBERS_PAGE_SIZE, all.size());
        for (int i = from; i < to; i++) {
            rows.add(List.of(Keyboards.btn(all.get(i).name(), "um:" + all.get(i).id())));
        }
        if (all.isEmpty()) {
            rows.add(List.of(Keyboards.btn("Пока никого — зарегистрируйте участников", "am")));
        }
        if (totalPages > 1) {
            List<InlineKeyboardButton> nav = new ArrayList<>();
            if (page > 0) {
                nav.add(Keyboards.btn("◀️", "au:" + (page - 1)));
            }
            nav.add(Keyboards.btn((page + 1) + "/" + totalPages, "au:" + page));
            if (page < totalPages - 1) {
                nav.add(Keyboards.btn("▶️", "au:" + (page + 1)));
            }
            rows.add(nav);
        }
        rows.add(List.of(Keyboards.btn("↩ Админ-панель", "am")));
        edit(chatId, messageId, "👥 <b>Участники</b> (" + all.size() + ")\n\nКого открываем?", Keyboards.kb(rows));
    }

    private void showMemberAdmin(long chatId, int messageId, long memberId) {
        Member member = members.findById(memberId).orElse(null);
        if (member == null) {
            edit(chatId, messageId, "Такого участника больше нет.",
                    Keyboards.kb(List.of(List.of(Keyboards.btn("↩ К участникам", "au:0")))));
            return;
        }
        List<Lecture> allLectures = lectures.findAllOrdered();
        Set<Long> attended = attendance.attendedLectureIds(memberId);

        StringBuilder sb = new StringBuilder();
        sb.append("👤 <b>").append(Ui.esc(member.name())).append("</b>\n\n");
        sb.append("📚 Отметьте лекции — нажатие переключает ✅ был / ❌ не был:");
        if (allLectures.isEmpty()) {
            sb.append("\n<i>Лекций пока нет — добавьте их в разделе «Лекции».</i>");
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Lecture lec : allLectures) {
            boolean was = attended.contains(lec.id());
            rows.add(List.of(Keyboards.btn(
                    (was ? "✅ " : "❌ ") + lec.title(),
                    "ut:" + memberId + ":" + lec.id())));
        }
        rows.add(List.of(Keyboards.btn("🗓 Указать место на мероприятии", "ue:" + memberId)));
        rows.add(List.of(Keyboards.btn("↩ К участникам", "au:0")));
        edit(chatId, messageId, sb.toString(), Keyboards.kb(rows));
    }

    private void showMemberEvents(long chatId, int messageId, long memberId) {
        Member member = members.findById(memberId).orElse(null);
        if (member == null) {
            edit(chatId, messageId, "Такого участника больше нет.",
                    Keyboards.kb(List.of(List.of(Keyboards.btn("↩ К участникам", "au:0")))));
            return;
        }
        List<ClubEvent> allEvents = events.findAllOrdered();
        StringBuilder sb = new StringBuilder();
        sb.append("🗓 <b>").append(Ui.esc(member.name())).append("</b> — где ставим место:\n\n");
        if (allEvents.isEmpty()) {
            sb.append("<i>Мероприятий пока нет — добавьте их в разделе «Мероприятия».</i>");
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ClubEvent ev : allEvents) {
            String placeInfo = results.findPlace(ev.id(), memberId)
                    .map(p -> " → " + p + " место")
                    .orElse("");
            rows.add(List.of(Keyboards.btn(ev.title() + placeInfo, "uf:" + memberId + ":" + ev.id())));
        }
        rows.add(List.of(Keyboards.btn("↩ Участник", "um:" + memberId)));
        edit(chatId, messageId, sb.toString(), Keyboards.kb(rows));
    }

    private void showPlacePicker(long chatId, int messageId, long memberId, long eventId) {
        Member member = members.findById(memberId).orElse(null);
        ClubEvent event = events.findById(eventId).orElse(null);
        if (member == null || event == null) {
            edit(chatId, messageId, "Участник или мероприятие не найдены.",
                    Keyboards.kb(List.of(List.of(Keyboards.btn("↩ К участникам", "au:0")))));
            return;
        }
        int currentPlace = results.findPlace(eventId, memberId).orElse(0);
        int currentPoints = RatingService.pointsFor(currentPlace);

        StringBuilder sb = new StringBuilder();
        sb.append("🏆 <b>").append(Ui.esc(member.name())).append("</b>\n");
        sb.append("Мероприятие: «").append(Ui.esc(event.title())).append("»\n\n");
        if (currentPlace == 0) {
            sb.append("Текущее место: —\n\n");
        } else {
            sb.append("Текущее место: ").append(currentPlace).append(" (+").append(currentPoints)
                    .append(" ").append(Ui.pluralizePoints(currentPoints)).append(")\n\n");
        }
        sb.append("Какое место занял участник? Нажатие сразу сохраняет.");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int start = 1; start <= 8; start += 4) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int p = start; p < start + 4; p++) {
                row.add(Keyboards.btn((currentPlace == p ? "✅ " : "") + p,
                        "us:" + memberId + ":" + eventId + ":" + p));
            }
            rows.add(row);
        }
        rows.add(List.of(Keyboards.btn("9", "us:" + memberId + ":" + eventId + ":9"),
                Keyboards.btn("10", "us:" + memberId + ":" + eventId + ":10"),
                Keyboards.btn("🗑 Убрать место", "us:" + memberId + ":" + eventId + ":0")));
        rows.add(List.of(Keyboards.btn("↩ К мероприятию", "ue:" + memberId),
                Keyboards.btn("👤 Участник", "um:" + memberId)));
        edit(chatId, messageId, sb.toString(), Keyboards.kb(rows));
    }

    private InlineKeyboardMarkup adminMenuKeyboard() {
        return Keyboards.kb(List.of(
                List.of(Keyboards.btn("👥 Участники", "au:0"), Keyboards.btn("📚 Лекции", "af")),
                List.of(Keyboards.btn("🗓 Мероприятия", "ae"), Keyboards.btn("⬆️ Загрузить таблицу", "ai")),
                List.of(Keyboards.btn("⬇️ Скачать таблицу", "ax")),
                List.of(Keyboards.btn("↩ Меню", "m"))));
    }

    private InlineKeyboardMarkup adminLecturesKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Lecture lec : lectures.findAllOrdered()) {
            rows.add(List.of(Keyboards.btn(lec.title(), "ld:" + lec.id())));
        }
        rows.add(List.of(Keyboards.btn("➕ Добавить лекцию", "nl")));
        rows.add(List.of(Keyboards.btn("↩ Админ-панель", "am")));
        return Keyboards.kb(rows);
    }

    private InlineKeyboardMarkup adminEventsKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ClubEvent ev : events.findAllOrdered()) {
            rows.add(List.of(Keyboards.btn(ev.title(), "ed:" + ev.id())));
        }
        rows.add(List.of(Keyboards.btn("➕ Добавить мероприятие", "ne")));
        rows.add(List.of(Keyboards.btn("↩ Админ-панель", "am")));
        return Keyboards.kb(rows);
    }

    private InlineKeyboardMarkup kbBackToMenu() {
        return Keyboards.kb(
                Keyboards.btn("🔄 Обновить", "uc"),
                Keyboards.btn("🏆 Рейтинг", "ur:0"),
                Keyboards.btn("↩ Меню", "m"));
    }

    private void handleDocument(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        sessions.remove(userId);
        if (!requireAdmin(chatId, userId)) {
            return;
        }
        var document = message.getDocument();
        String fileName = document.getFileName() == null ? "" : document.getFileName().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            send(chatId, "Нужен файл Excel — .xlsx или .xls.", adminMenuKeyboard());
            return;
        }
        if (document.getFileSize() != null && document.getFileSize() > MAX_IMPORT_BYTES) {
            send(chatId, "Файл слишком большой — до 10 МБ.", adminMenuKeyboard());
            return;
        }
        try {
            GetFile getFile = new GetFile(document.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
            if (file == null || file.getFilePath() == null) {
                send(chatId, "⚠️ Не получилось получить файл. Попробуйте ещё раз.", adminMenuKeyboard());
                return;
            }
            try (InputStream in = download(file.getFilePath())) {
                ExcelService.ImportSummary summary = excelService.importFrom(in);
                send(chatId, "✅ <b>Импорт завершён</b>\n\n" +
                        "👥 новых участников: " + summary.membersCreated() + "\n" +
                        "📚 новых лекций: " + summary.lecturesCreated() + "\n" +
                        "🗓 новых мероприятий: " + summary.eventsCreated() + "\n" +
                        "посещений отмечено: " + summary.attendanceMarked() + "\n" +
                        "результатов сохранено: " + summary.resultsSaved() + "\n\n" +
                        "Рейтинг уже пересчитан.", adminMenuKeyboard());
            }
        } catch (Exception e) {
            log.error("excel import failed", e);
            send(chatId, "⚠️ Не получилось прочитать файл. Проверьте формат и попробуйте снова.", adminMenuKeyboard());
        }
    }

    private InputStream download(String filePath) throws java.io.IOException {
        URL url = new URL("https://api.telegram.org/file/bot" + cfg.botToken + "/" + filePath);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        return conn.getInputStream();
    }

    private void sendExcel(long chatId) {
        try {
            byte[] data = excelService.exportToBytes();
            String fileName = "club-rating-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
            execute(SendDocument.builder()
                    .chatId(String.valueOf(chatId))
                    .document(new InputFile(new ByteArrayInputStream(data), fileName))
                    .caption("📊 Готово — вот выгрузка клуба")
                    .build());
        } catch (Exception e) {
            log.error("excel export failed", e);
            send(chatId, "⚠️ Выгрузка не получилась. Попробуйте ещё раз.", null);
        }
    }

    private boolean requireAdmin(long chatId, long userId) {
        if (cfg.isAdmin(userId)) {
            return true;
        }
        send(chatId, "⛔ Такое может только админ.", null);
        return false;
    }

    private void send(long chatId, String html, InlineKeyboardMarkup keyboard) {
        try {
            execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(html)
                    .parseMode("HTML")
                    .replyMarkup(keyboard)
                    .build());
        } catch (TelegramApiException e) {
            log.error("sendMessage failed", e);
        }
    }

    private void edit(long chatId, int messageId, String html, InlineKeyboardMarkup keyboard) {
        try {
            execute(EditMessageText.builder()
                    .chatId(String.valueOf(chatId))
                    .messageId(messageId)
                    .text(html)
                    .parseMode("HTML")
                    .replyMarkup(keyboard)
                    .build());
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                return;
            }
            log.warn("edit failed, sending new message", e);
            send(chatId, html, keyboard);
        }
    }

    private void answer(String callbackQueryId) {
        answer(callbackQueryId, null);
    }

    private void answer(String callbackQueryId, String text) {
        try {
            execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(text != null)
                    .build());
        } catch (TelegramApiException ignored) {
        }
    }

    private static boolean isPrivate(Message message) {
        return message.getChat() != null && "private".equals(message.getChat().getType());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static int parseint(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
