package com.clubrating.repo;

import com.clubrating.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ResultRepository {

    private final Database db;

    public ResultRepository(Database db) {
        this.db = db;
    }

    public void upsert(long eventId, long memberId, int place) {
        String sql = "MERGE INTO results (event_id, member_id, place) " +
                "KEY (event_id, member_id) VALUES (?, ?, ?)";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setLong(2, memberId);
            ps.setInt(3, place);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("upsert result failed", e);
        }
    }

    public void delete(long eventId, long memberId) {
        String sql = "DELETE FROM results WHERE event_id = ? AND member_id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setLong(2, memberId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("delete result failed", e);
        }
    }

    public Optional<Integer> findPlace(long eventId, long memberId) {
        String sql = "SELECT place FROM results WHERE event_id = ? AND member_id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setLong(2, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt(1));
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("findPlace failed", e);
        }
    }

    public Map<Long, Integer> placesForMember(long memberId) {
        String sql = "SELECT event_id, place FROM results WHERE member_id = ?";
        Map<Long, Integer> result = new HashMap<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getLong(1), rs.getInt(2));
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("placesForMember failed", e);
        }
    }

    public Map<Long, Integer> pointsByMember() {
        String sql = "SELECT member_id, SUM(CASE place WHEN 1 THEN 3 WHEN 2 THEN 2 WHEN 3 THEN 1 ELSE 0 END) " +
                "FROM results GROUP BY member_id";
        Map<Long, Integer> result = new HashMap<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getLong(1), rs.getInt(2));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("pointsByMember failed", e);
        }
    }

    public Map<Long, Long> eventCountByMember() {
        String sql = "SELECT member_id, COUNT(*) FROM results GROUP BY member_id";
        Map<Long, Long> result = new HashMap<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getLong(1), rs.getLong(2));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("eventCountByMember failed", e);
        }
    }

    public Map<Long, Integer> placesByMemberForEvent(long eventId) {
        String sql = "SELECT member_id, place FROM results WHERE event_id = ?";
        Map<Long, Integer> result = new HashMap<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getLong(1), rs.getInt(2));
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("placesByMemberForEvent failed", e);
        }
    }

    public List<RawResult> findAllRaw() {
        String sql = "SELECT m.name AS member_name, e.title AS event_title, r.place " +
                "FROM results r JOIN members m ON m.id = r.member_id JOIN events e ON e.id = r.event_id " +
                "ORDER BY e.id DESC, r.place, m.name";
        List<RawResult> result = new java.util.ArrayList<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new RawResult(rs.getString("member_name"), rs.getString("event_title"), rs.getInt("place")));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("findAllRaw failed", e);
        }
    }

    public record RawResult(String memberName, String eventTitle, int place) {
    }
}
