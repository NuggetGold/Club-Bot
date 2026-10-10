package com.clubrating.repo;

import com.clubrating.db.Database;
import com.clubrating.model.Lecture;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LectureRepository {

    private final Database db;

    public LectureRepository(Database db) {
        this.db = db;
    }

    public List<Lecture> findAllOrdered() {
        String sql = "SELECT id, title, position FROM lectures ORDER BY position, id";
        List<Lecture> result = new ArrayList<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Lecture(rs.getLong("id"), rs.getString("title"), rs.getInt("position")));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("findAllOrdered failed", e);
        }
    }

    public Optional<Lecture> findById(long id) {
        String sql = "SELECT id, title, position FROM lectures WHERE id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Lecture(rs.getLong("id"), rs.getString("title"), rs.getInt("position")));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("findById failed", e);
        }
    }

    public Optional<Lecture> findByTitle(String title) {
        String sql = "SELECT id, title, position FROM lectures WHERE LOWER(TRIM(title)) = LOWER(TRIM(?)) ORDER BY id LIMIT 1";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Lecture(rs.getLong("id"), rs.getString("title"), rs.getInt("position")));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("findByTitle failed", e);
        }
    }

    public Lecture create(String title) {
        String insert = "INSERT INTO lectures (title, position) SELECT ?, COALESCE(MAX(position), 0) + 1 FROM lectures";
        String select = "SELECT position FROM lectures WHERE id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("no generated key for lecture " + title);
                }
                id = keys.getLong(1);
            }
            try (PreparedStatement ps2 = conn.prepareStatement(select)) {
                ps2.setLong(1, id);
                try (ResultSet rs = ps2.executeQuery()) {
                    int position = rs.next() ? rs.getInt(1) : 0;
                    return new Lecture(id, title, position);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("create lecture failed", e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM lectures WHERE id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("delete lecture failed", e);
        }
    }
}
