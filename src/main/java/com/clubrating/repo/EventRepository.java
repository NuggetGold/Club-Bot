package com.clubrating.repo;

import com.clubrating.db.Database;
import com.clubrating.model.ClubEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EventRepository {

    private final Database db;

    public EventRepository(Database db) {
        this.db = db;
    }

    public List<ClubEvent> findAllOrdered() {
        String sql = "SELECT id, title FROM events ORDER BY id DESC";
        List<ClubEvent> result = new ArrayList<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ClubEvent(rs.getLong("id"), rs.getString("title")));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("findAllOrdered failed", e);
        }
    }

    public Optional<ClubEvent> findById(long id) {
        String sql = "SELECT id, title FROM events WHERE id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ClubEvent(rs.getLong("id"), rs.getString("title")));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("findById failed", e);
        }
    }

    public Optional<ClubEvent> findByTitle(String title) {
        String sql = "SELECT id, title FROM events WHERE LOWER(TRIM(title)) = LOWER(TRIM(?)) ORDER BY id DESC LIMIT 1";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ClubEvent(rs.getLong("id"), rs.getString("title")));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("findByTitle failed", e);
        }
    }

    public ClubEvent create(String title) {
        String sql = "INSERT INTO events (title) VALUES (?)";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("no generated key for event " + title);
                }
                return new ClubEvent(keys.getLong(1), title);
            }
        } catch (Exception e) {
            throw new RuntimeException("create event failed", e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM events WHERE id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("delete event failed", e);
        }
    }
}
