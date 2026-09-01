# java-filmorate

Сервис для работы с фильмами и пользовательскими оценками: пользователи добавляют фильмы,
ставят лайки, дружат между собой и получают список самых популярных фильмов.

## Хранение данных

Данные хранятся в базе H2 и переживают перезапуск приложения. В рабочем режиме база лежит
в файле `./db/filmorate`, в тестах поднимается резидентная база в памяти. Структура
создаётся из `src/main/resources/schema.sql`, справочники жанров, рейтингов и статусов
дружбы заполняются из `src/main/resources/data.sql` (через `MERGE`, поэтому повторный
запуск не плодит дубликаты).

Доступ к данным изолирован в DAO: `UserDbStorage`, `FilmDbStorage`, `GenreDbStorage` и
`MpaDbStorage` реализуют интерфейсы хранилищ поверх `JdbcTemplate`. Сервисы получают нужную
реализацию через `@Qualifier`, а интеграционные тесты (`DbStorageIntegrationTests`,
аннотация `@JdbcTest`) проверяют все публичные методы хранилищ на чистой базе.

## Эндпоинты

| Метод и путь | Назначение |
|---|---|
| `GET /users`, `GET /users/{id}` | все пользователи, пользователь по идентификатору |
| `POST /users`, `PUT /users` | создание и обновление пользователя |
| `PUT /users/{id}/friends/{friendId}` | добавить друга (связь односторонняя) |
| `DELETE /users/{id}/friends/{friendId}` | удалить друга из своего списка |
| `GET /users/{id}/friends` | друзья пользователя |
| `GET /users/{id}/friends/common/{otherId}` | общие друзья двух пользователей |
| `GET /films`, `GET /films/{id}` | все фильмы, фильм по идентификатору |
| `POST /films`, `PUT /films` | создание и обновление фильма |
| `PUT /films/{id}/like/{userId}` | поставить лайк |
| `DELETE /films/{id}/like/{userId}` | снять лайк |
| `GET /films/popular?count=N` | N самых популярных фильмов |
| `GET /genres`, `GET /genres/{id}` | справочник жанров |
| `GET /mpa`, `GET /mpa/{id}` | справочник возрастных рейтингов |

При создании и обновлении фильма достаточно передать идентификаторы: `"mpa": {"id": 3}` и
`"genres": [{"id": 1}, {"id": 2}]`. В ответе возвращаются полные объекты с названиями,
жанры — без дубликатов и упорядоченные по идентификатору. Несуществующий жанр или рейтинг
приводит к ответу `404`.

## Схема базы данных

![ER-диаграмма базы данных Filmorate](docs/er-diagram.png)

[Открыть схему в dbdiagram.io](https://dbdiagram.io/d/6a85df71fd15a881e5b9c19b)

<details>
<summary>Та же схема в виде mermaid-диаграммы</summary>

```mermaid
erDiagram
    users {
        bigint id PK
        varchar email UK
        varchar login UK
        varchar name
        date birthday
    }
    films {
        bigint id PK
        varchar name
        varchar description
        date release_date
        integer duration
        integer mpa_rating_id FK
    }
    mpa_ratings {
        integer id PK
        varchar name UK
        varchar description
    }
    genres {
        integer id PK
        varchar name UK
    }
    friendship_statuses {
        integer id PK
        varchar name UK
    }
    film_genres {
        bigint film_id PK, FK
        integer genre_id PK, FK
    }
    film_likes {
        bigint film_id PK, FK
        bigint user_id PK, FK
    }
    friendships {
        bigint user_id PK, FK
        bigint friend_id PK, FK
        integer status_id FK
    }

    mpa_ratings         ||--o{ films       : "задаёт возрастной рейтинг"
    films               ||--o{ film_genres : "имеет жанры"
    genres              ||--o{ film_genres : "присвоен фильмам"
    films               ||--o{ film_likes  : "получает лайки"
    users               ||--o{ film_likes  : "ставит лайки"
    users               ||--o{ friendships : "отправляет заявку"
    users               ||--o{ friendships : "получает заявку"
    friendship_statuses ||--o{ friendships : "определяет статус"
```

</details>

Исходник диаграммы в формате DBML лежит в
[docs/er-diagram-source.txt](docs/er-diagram-source.txt) — его можно вставить в
[dbdiagram.io](https://dbdiagram.io/d) и получить ту же схему.

## Описание таблиц

### Основные сущности

**`users`** — пользователи сервиса.

| Поле | Тип | Описание |
|---|---|---|
| `id` | `bigint` | PK, автоинкремент |
| `email` | `varchar(255)` | `NOT NULL`, `UNIQUE` |
| `login` | `varchar(255)` | `NOT NULL`, `UNIQUE` |
| `name` | `varchar(255)` | может быть пустым — тогда приложение подставляет `login` |
| `birthday` | `date` | `NOT NULL` |

**`films`** — фильмы.

| Поле | Тип | Описание |
|---|---|---|
| `id` | `bigint` | PK, автоинкремент |
| `name` | `varchar(255)` | `NOT NULL` |
| `description` | `varchar(200)` | ограничение длины из ТЗ |
| `release_date` | `date` | `NOT NULL` |
| `duration` | `integer` | `NOT NULL`, `CHECK (duration > 0)`, минуты |
| `mpa_rating_id` | `integer` | FK → `mpa_ratings.id`, не более одного рейтинга; может быть пустым |

Количество лайков в `films` не хранится: это производное значение, оно вычисляется по
`film_likes`. Отдельная колонка означала бы два источника правды и рассинхронизацию при
любом сбое между вставкой лайка и обновлением счётчика.

### Справочники

**`mpa_ratings`** — возрастные рейтинги MPA: `G`, `PG`, `PG-13`, `R`, `NC-17`.
Поле `description` хранит расшифровку («лицам до 17 лет только в присутствии взрослого»),
поэтому текст ограничений не приходится дублировать в коде.

**`genres`** — жанры: Комедия, Драма, Мультфильм, Триллер, Документальный, Боевик.

**`friendship_statuses`** — статусы дружбы: `UNCONFIRMED` (заявка отправлена, ответа нет)
и `CONFIRMED` (заявка принята).

У всех трёх справочников на `name` стоит `UNIQUE` — двух «Комедий» в базе не появится.

### Связующие таблицы

**`film_genres`** — жанры фильма, связь «многие ко многим». Первичный ключ составной
(`film_id`, `genre_id`), поэтому один жанр нельзя присвоить фильму дважды.

**`film_likes`** — лайки. Первичный ключ составной (`film_id`, `user_id`): один
пользователь ставит фильму не более одного лайка, повторный запрос упадёт на уровне базы,
а не на уровне проверки в сервисе.

**`friendships`** — дружеские связи. Связь односторонняя: `user_id` — тот, кто добавил
друга, `friend_id` — тот, кого добавили, `status_id` — статус связи. В список друзей
пользователя попадают только те, кого он добавил сам; обратная запись при этом не
создаётся.

Ограничения таблицы:

* `PRIMARY KEY (user_id, friend_id)` — повторные заявки от того же пользователя тому же
  невозможны, поэтому добавление в друзья идемпотентно;
* `CHECK (user_id <> friend_id)` — нельзя добавить в друзья самого себя.

Пара встречных строк `A → B` и `B → A` допустима: это и есть подтверждённая дружба. Когда
второй пользователь добавляет первого, обе строки получают статус `CONFIRMED`; при удалении
одной из них оставшаяся возвращается в `UNCONFIRMED`.

Внешние ключи на `films` и `users` объявлены с `ON DELETE CASCADE`: при удалении фильма
или пользователя его лайки, жанровые связи и дружеские связи уходят вместе с ним. Ссылки
на справочники — `ON DELETE RESTRICT`, чтобы жанр или рейтинг нельзя было удалить, пока он
используется.

## Соответствие нормальным формам

* **1NF** — все столбцы атомарны. Жанры фильма вынесены в `film_genres`, а не в массив или
  строку с разделителями.
* **2NF** — таблицы с составным ключом (`film_genres`, `film_likes`) не содержат
  неключевых атрибутов вовсе, поэтому частичных зависимостей нет. В `friendships`
  единственный неключевой атрибут `status_id` зависит от пары целиком.
* **3NF** — названия жанров, рейтингов и статусов хранятся в справочниках, в основных
  таблицах лежат только `id`. Транзитивных зависимостей между неключевыми полями нет,
  вычисляемых полей (вроде счётчика лайков) в схеме тоже нет.

## Примеры запросов

### Все фильмы с возрастным рейтингом

```sql
SELECT f.id,
       f.name,
       f.description,
       f.release_date,
       f.duration,
       m.name AS mpa_rating
FROM films AS f
JOIN mpa_ratings AS m ON m.id = f.mpa_rating_id
ORDER BY f.id;
```

### Фильм по идентификатору вместе с жанрами

```sql
SELECT f.id,
       f.name,
       f.release_date,
       f.duration,
       m.name AS mpa_rating,
       g.name AS genre
FROM films AS f
JOIN mpa_ratings AS m ON m.id = f.mpa_rating_id
LEFT JOIN film_genres AS fg ON fg.film_id = f.id
LEFT JOIN genres AS g ON g.id = fg.genre_id
WHERE f.id = ?;
```

`LEFT JOIN` нужен, чтобы фильм без указанных жанров всё равно попал в результат.

### Топ N популярных фильмов

```sql
SELECT f.id,
       f.name,
       COUNT(fl.user_id) AS likes_count
FROM films AS f
LEFT JOIN film_likes AS fl ON fl.film_id = f.id
GROUP BY f.id, f.name
ORDER BY likes_count DESC, f.name
LIMIT ?;
```

Здесь тоже `LEFT JOIN`: фильмы без лайков должны присутствовать в выдаче, иначе на свежей
базе топ окажется пустым.

### Все пользователи

```sql
SELECT id, email, login, name, birthday
FROM users
ORDER BY id;
```

### Пользователь по идентификатору

```sql
SELECT id, email, login, name, birthday
FROM users
WHERE id = ?;
```

### Друзья пользователя

```sql
SELECT u.id, u.email, u.login, u.name, u.birthday
FROM users AS u
WHERE u.id IN (SELECT friend_id FROM friendships WHERE user_id = ?)
ORDER BY u.id;
```

### Только подтверждённые друзья

```sql
SELECT u.id, u.login, u.name
FROM friendships AS f
JOIN friendship_statuses AS s ON s.id = f.status_id
JOIN users AS u ON u.id = f.friend_id
WHERE f.user_id = ?
  AND s.name = 'CONFIRMED';
```

### Входящие заявки в друзья

```sql
SELECT u.id, u.login, u.name
FROM friendships AS f
JOIN friendship_statuses AS s ON s.id = f.status_id
JOIN users AS u ON u.id = f.user_id
WHERE f.friend_id = ?
  AND s.name = 'UNCONFIRMED';
```

Отдельная таблица для входящих заявок не нужна — это выборка из `friendships` по
получателю и статусу.

### Общие друзья двух пользователей

```sql
SELECT u.id, u.login, u.name
FROM users AS u
WHERE u.id IN (SELECT friend_id FROM friendships WHERE user_id = ?)
  AND u.id IN (SELECT friend_id FROM friendships WHERE user_id = ?)
ORDER BY u.id;
```

Каждый подзапрос — список друзей одного пользователя, а `IN` по обоим оставляет только
пересечение.

### Лайк фильму

```sql
INSERT INTO film_likes (film_id, user_id)
VALUES (?, ?);
```

```sql
DELETE FROM film_likes
WHERE film_id = ? AND user_id = ?;
```

### Добавление в друзья

`MERGE` делает операцию идемпотентной: повторное добавление того же друга не создаёт
дубликат и не меняет уже выставленный статус:

```sql
MERGE INTO friendships (user_id, friend_id, status_id) KEY (user_id, friend_id)
VALUES (?, ?, ?);
```

### Подтверждение дружбы

Если встречная строка уже есть, обе связи переводятся в `CONFIRMED`:

```sql
UPDATE friendships
SET status_id = (SELECT id FROM friendship_statuses WHERE name = 'CONFIRMED')
WHERE user_id = ? AND friend_id = ?;
```

### Удаление из друзей

Удаляется только своя связь, встречная остаётся и возвращается в `UNCONFIRMED`:

```sql
DELETE FROM friendships
WHERE user_id = ? AND friend_id = ?;
```

### Жанры фильма

```sql
SELECT g.id, g.name
FROM genres AS g
JOIN film_genres AS fg ON fg.genre_id = g.id
WHERE fg.film_id = ?
ORDER BY g.id;
```
