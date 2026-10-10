package com.clubrating;

import com.clubrating.bot.ClubBot;
import com.clubrating.config.Config;
import com.clubrating.db.Database;
import com.clubrating.repo.AttendanceRepository;
import com.clubrating.repo.EventRepository;
import com.clubrating.repo.LectureRepository;
import com.clubrating.repo.MemberRepository;
import com.clubrating.repo.ResultRepository;
import com.clubrating.service.ExcelService;
import com.clubrating.service.RatingService;
import org.flywaydb.core.Flyway;

import java.util.concurrent.CountDownLatch;

public final class App {

    public static void main(String[] args) throws Exception {
        Config config = Config.load();

        Flyway flyway = Flyway.configure()
                .dataSource(config.dbUrl, config.dbUser, config.dbPassword)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (Database db = new Database(config.dbUrl, config.dbUser, config.dbPassword)) {
            MemberRepository members = new MemberRepository(db);
            LectureRepository lectures = new LectureRepository(db);
            EventRepository events = new EventRepository(db);
            AttendanceRepository attendance = new AttendanceRepository(db);
            ResultRepository results = new ResultRepository(db);
            RatingService ratingService = new RatingService(members, lectures, events, attendance, results);
            ExcelService excelService = new ExcelService(members, lectures, events, attendance, results);

            ClubBot bot = new ClubBot(config, members, lectures, events, attendance, results,
                    ratingService, excelService);

            var api = new org.telegram.telegrambots.meta.TelegramBotsApi(
                    org.telegram.telegrambots.updatesreceivers.DefaultBotSession.class);
            api.registerBot(bot);
            bot.registerCommands();

            System.out.println("Club rating bot started. Username: @" + config.botUsername
                    + ", admins: " + config.adminIds);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Останавливаюсь…")));
            new CountDownLatch(1).await();
        }
    }
}
