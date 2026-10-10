package com.clubrating.bot;

import com.clubrating.model.MemberStats;
import com.clubrating.model.RatingRow;
import com.clubrating.service.RatingService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class Ui {

    public static final int RATING_PAGE_SIZE = 15;

    private static final DateTimeFormatter UPDATED_AT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Ui() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String pluralizePoints(int n) {
        int a = Math.abs(n) % 10;
        int b = Math.abs(n) % 100;
        if (a == 1 && b != 11) {
            return "очко";
        }
        if (a >= 2 && a <= 4 && (b < 10 || b >= 20)) {
            return "очка";
        }
        return "очков";
    }

    public static String pluralizeEvents(int n) {
        int a = Math.abs(n) % 10;
        int b = Math.abs(n) % 100;
        if (a == 1 && b != 11) {
            return "мероприятие";
        }
        if (a >= 2 && a <= 4 && (b < 10 || b >= 20)) {
            return "мероприятия";
        }
        return "мероприятий";
    }

    public static String pluralizeMembers(int n) {
        return pluralize(n, "участник", "участника", "участников");
    }

    private static String pluralize(int n, String one, String few, String many) {
        int a = Math.abs(n) % 10;
        int b = Math.abs(n) % 100;
        if (a == 1 && b != 11) {
            return one;
        }
        if (a >= 2 && a <= 4 && (b < 10 || b >= 20)) {
            return few;
        }
        return many;
    }

    public static String medal(int rank) {
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "";
        };
    }

    public static List<String> ratingPages(List<RatingRow> board, int pageSize) {
        List<String> pages = new ArrayList<>();
        if (board.isEmpty()) {
            pages.add("🏆 <b>Рейтинг клуба</b>\n\nПока пусто. Как только отметите места на мероприятиях, " +
                    "здесь появятся имена.");
            return pages;
        }
        int totalPages = (board.size() + pageSize - 1) / pageSize;
        for (int page = 0; page < totalPages; page++) {
            StringBuilder sb = new StringBuilder();
            sb.append("🏆 <b>Рейтинг клуба</b>\n");
            sb.append("<i>Очки: 1-е место — ").append(RatingService.POINTS_FIRST).append(", 2-е — ")
                    .append(RatingService.POINTS_SECOND).append(", 3-е — ")
                    .append(RatingService.POINTS_THIRD).append(", остальные — 0</i>\n\n");
            int from = page * pageSize;
            int to = Math.min(from + pageSize, board.size());
            for (int i = from; i < to; i++) {
                RatingRow row = board.get(i);
                int rank = i + 1;
                String medal = medal(rank);
                sb.append("<b>").append(rank).append(".</b>");
                if (!medal.isEmpty()) {
                    sb.append(" ").append(medal);
                }
                sb.append(" <b>").append(esc(row.member().name())).append("</b> — ")
                        .append(row.points()).append(" ").append(pluralizePoints(row.points()));
                if (row.events() > 0) {
                    sb.append(" · ").append(row.events()).append(" ").append(pluralizeEvents((int) row.events()));
                }
                sb.append("\n");
            }
            sb.append("\n<i>Стр. ").append(page + 1).append("/").append(totalPages)
                    .append(" · всего: ").append(board.size()).append(" ").append(pluralizeMembers(board.size()))
                    .append("</i>");
            pages.add(sb.toString());
        }
        return pages;
    }

    public static String formatCard(MemberStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("👤 <b>").append(esc(stats.member().name())).append("</b>\n");
        sb.append("━━━━━━━━━━━━\n");

        sb.append("📚 <b>Лекции:</b> ").append(stats.attendedCount()).append(" из ")
                .append(stats.lectures().size()).append("\n");
        if (stats.lectures().isEmpty()) {
            sb.append("<i>Лекций пока нет</i>\n");
        } else {
            for (var lec : stats.lectures()) {
                boolean was = stats.attendedLectureIds().contains(lec.id());
                sb.append(was ? "✅ " : "❌ ").append(esc(lec.title())).append("\n");
            }
        }

        sb.append("\n🗓 <b>Места на мероприятиях:</b>\n");
        if (stats.results().isEmpty()) {
            sb.append("<i>Пока ничего — места появятся после ближайших мероприятий</i>\n");
        } else {
            for (var r : stats.results()) {
                sb.append(" • «").append(esc(r.event().title())).append("» — ")
                        .append(r.place()).append(" место (+").append(r.points()).append(" ")
                        .append(pluralizePoints(r.points())).append(")\n");
            }
        }

        sb.append("\n⭐ <b>Очки:</b> ").append(stats.points()).append(" ")
                .append(pluralizePoints(stats.points())).append("\n");
        sb.append("🏅 <b>В рейтинге:</b> ").append(stats.rank()).append(" из ")
                .append(stats.totalMembers()).append("\n");
        sb.append("━━━━━━━━━━━━\n");
        sb.append("<i>обновлено ").append(LocalDateTime.now().format(UPDATED_AT)).append("</i>");
        return sb.toString();
    }

    public static List<String> splitMessage(String text, int limit) {
        List<String> parts = new ArrayList<>();
        if (text.length() <= limit) {
            parts.add(text);
            return parts;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > start) {
                    end = nl + 1;
                }
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }
}
