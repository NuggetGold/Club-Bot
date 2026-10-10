package com.clubrating.repo;

import com.clubrating.db.Database;
import com.clubrating.model.Member;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MemberRepository {

    private final Database db;

    public MemberRepository(Database db) {
        this.db = db;
    }

    public Optional<Member> findByTelegramId(long telegramId) {
        String sql = "SELECT id, telegram_id, name FROM members WHERE telegram_id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("findByTelegramId failed", e);
        }
    }

    public Optional<Member> findById(long id) {
        String sql = "SELECT id, telegram_id, name FROM members WHERE id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("findById failed", e);
        }
    }

    public Optional<Member> findByNameNotLinked(String name) {
        String sql = "SELECT id, telegram_id, name FROM members WHERE telegram_id IS NULL AND LOWER(TRIM(name)) = LOWER(TRIM(?)) ORDER BY id LIMIT 1";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("findByNameNotLinked failed", e);
        }
    }

    public Member create(Long telegramId, String name) {
        String sql = "INSERT INTO members (telegram_id, name) VALUES (?, ?)";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (telegramId == null) {
                ps.setNull(1, java.sql.Types.BIGINT);
            } else {
                ps.setLong(1, telegramId);
            }
            ps.setString(2, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("no generated key for member " + name);
                }
                return new Member(keys.getLong(1), telegramId, name);
            }
        } catch (Exception e) {
            throw new RuntimeException("create member failed", e);
        }
    }

    public boolean linkTelegram(long memberId, long telegramId) {
        String sql = "UPDATE members SET telegram_id = ? WHERE id = ? AND telegram_id IS NULL";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            ps.setLong(2, memberId);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            throw new RuntimeException("linkTelegram failed", e);
        }
    }

    public List<Member> findAllOrdered() {
        String sql = "SELECT id, telegram_id, name FROM members ORDER BY LOWER(name), id";
        List<Member> result = new ArrayList<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("findAllOrdered failed", e);
        }
    }

    private static Member map(ResultSet rs) throws Exception {
        long id = rs.getLong("id");
        long tg = rs.getLong("telegram_id");
        Long telegramId = rs.wasNull() ? null : tg;
        return new Member(id, telegramId, rs.getString("name"));
    }
}
