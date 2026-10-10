CREATE TABLE members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    telegram_id BIGINT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_members_telegram_id UNIQUE (telegram_id)
);

CREATE TABLE lectures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    position INT NOT NULL DEFAULT 0
);

CREATE TABLE events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    event_date DATE NULL
);

CREATE TABLE attendance (
    member_id BIGINT NOT NULL,
    lecture_id BIGINT NOT NULL,
    attended BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (member_id, lecture_id),
    CONSTRAINT fk_attendance_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_lecture FOREIGN KEY (lecture_id) REFERENCES lectures (id) ON DELETE CASCADE
);

CREATE TABLE results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    place INT NOT NULL,
    CONSTRAINT uq_results_event_member UNIQUE (event_id, member_id),
    CONSTRAINT fk_results_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_results_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
);
