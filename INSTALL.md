# Установка бота на хост

Две ситуации: обычная панель **Pterodactyl** (как у тебя) и просто **VDS с Linux**. Про Pterodactyl — основная часть, VDS — в конце.

---

## Что нужно заранее

1. **Токен бота.** У `@BotFather` команда `/newbot`, дать имя и юзернейм. Он выдаст строку вида `8867703293:AAG...`. Если бот уже есть — возьми готовый токен у того же BotFather (`/mybots` → бот → API Token).
2. **Свой Telegram ID.** У `@userinfobot` напиши `/start`, он пришлёт число. Это чтобы ты стал админом.
3. **Два файла** (лежат у того, кто собрал бота):
   - `club-rating-bot-1.0.0.jar` — сам бот;
   - `config.properties` — настройки.

---

## Часть 1. Pterodactyl

### Шаг 1. Заполнить config.properties

Открой файл в блокноте и впиши своё. Должно получиться такое:

```
BOT_TOKEN=8867703293:AAG...ваш токен...
BOT_USERNAME=
ADMIN_IDS=7455560238
DB_URL=jdbc:h2:file:./club_rating;MODE=MySQL;DB_CLOSE_ON_EXIT=TRUE
DB_USER=sa
DB_PASSWORD=
```

- `BOT_USERNAME` можно оставить пустым, бот сам уточнит юзернейм у Telegram.
- `ADMIN_IDS` — твои цифры из userinfobot. Несколько админов — через запятую без пробелов.
- Строку `DB_URL` с `h2` не меняй. База встроенная, ставить MySQL не надо.

### Шаг 2. Выбрать правильную Java

Бот собран под Java 21. В панели зайди в **Startup** и проверь поле **Docker Image**. Нужен образ с Java 21 (обычно в списке есть подпись `java_21`). Если оставить 17 или 11, бот не запустится и выдаст `UnsupportedClassVersionError`.

### Шаг 3. Стартовая команда

В **Startup** команда должна указывать на наш jar:

```
java -Xms256M -Xmx512M -jar club-rating-bot-1.0.0.jar
```

Флаги `-Dterminal.jline=false`, `-Dterminal.ansi=true` и слово `nogui` из игровых яиц не нужны, бот их игнорирует. Если у тебя шаблон с `{{SERVER_JARFILE}}`, впиши имя файла `club-rating-bot-1.0.0.jar` в переменную.

Порты выделять не нужно. Бот работает через long polling и сам стучится к Telegram.

### Шаг 4. Залить файлы

Открой **File Manager** сервера, папку `/home/container`. Загрузи туда оба файла. Если там лежит старый jar, удали его перед загрузкой нового.

### Шаг 5. Запустить и проверить

Жми **Start** и смотри **Console**. В норме увидишь:

```
Club rating bot started. Username: @club_rating_bot, admins: [7455560238]
```

Теперь открой бота в Telegram и напиши `/start`.

### Где база данных

Файл `club_rating.mv.db` появится сам в `/home/container` при первом запуске. Он переживает перезапуски и хранит всех участников. **Бэкап** — это просто копия этого файла. Сохрани её куда-нибудь раз в неделю.

### Как обновлять бота

1. Залей новый `club-rating-bot-1.0.0.jar` в `/home/container`, заменив старый.
2. Нажми **Restart**.
3. `config.properties` и `club_rating.mv.db` не трогай, данные на месте.

---

## Часть 2. Обычный VDS с Linux

Если хоста с панелью нет, а есть VDS.

### Шаг 1. Java 21

```
sudo apt update
sudo apt install -y openjdk-21-jre-headless
java -version
```

### Шаг 2. Папка и файлы

```
sudo mkdir -p /opt/club-rating-bot
cd /opt/club-rating-bot
```

Залей сюда `club-rating-bot-1.0.0.jar` и `config.properties` (через `scp` или файловый менеджер). Проверь, что `config.properties` заполнен так же, как в части 1.

### Шаг 3. Автозапуск через systemd

Создай файл сервиса:

```
sudo nano /etc/systemd/system/club-rating-bot.service
```

Положи туда:

```
[Unit]
Description=Club rating bot
After=network.target

[Service]
WorkingDirectory=/opt/club-rating-bot
ExecStart=/usr/bin/java -Xms256M -Xmx512M -jar club-rating-bot-1.0.0.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Включи и запусти:

```
sudo systemctl daemon-reload
sudo systemctl enable --now club-rating-bot
```

Смотреть логи:

```
journalctl -u club-rating-bot -f
```

Остановить или перезапустить:

```
sudo systemctl restart club-rating-bot
```

Если не хочешь возиться с сервисом, хватит и такого запуска (но после ребута сервера бот не поднимется сам):

```
nohup java -jar club-rating-bot-1.0.0.jar > bot.log 2>&1 &
```

---

## Часть 3. Если бот не встал

- **`BOT_TOKEN is not set`** — рядом с jar нет `config.properties` либо файл пустой. Залей заново.
- **`UnsupportedClassVersionError`** — образ или система не Java 21. Поменяй на Java 21.
- **`Connection refused` со словом `mysql`** — в `DB_URL` оказалась строка MySQL. Верни строку с `h2`.
- **Server offline сразу после Start** — открой Console, там будет красное исключение. Скопируй его целиком и отдай тому, кто правит код, вместе со строчкой из лога.
- **Ошибок нет, но бот не отвечает** — проверь, что токен верный (без лишних пробелов и переносов) и что у хоста есть доступ к `api.telegram.org`.

Токен никому не показывай. Если он утёк — у `@BotFather` команда `/revoke`.
