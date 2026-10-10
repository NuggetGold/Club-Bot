package com.clubrating.repo;

import com.clubrating.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AttendanceRepository {

    private final Database db;

    public AttendanceRepository(Database db) {
        this.db = db;
    }

    public Set<Long> attendedLectureIds(long memberId) {
        String sql = "SELECT lecture_id FROM attendance WHERE member_id = ? AND attended = TRUE";
        Set<Long> result = new HashSet<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong(1));
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("attendedLectureIds failed", e);
        }
    }

    public boolean isAttended(long memberId, long lectureId) {
        String sql = "SELECT attended FROM attendance WHERE member_id = ? AND lecture_id = ?";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            ps.setLong(2, lectureId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("isAttended failed", e);
        }
    }

    public void set(long memberId, long lectureId, boolean attended) {
        String sql = "MERGE INTO attendance (member_id, lecture_id, attended) " +
                "KEY (member_id, lecture_id) VALUES (?, ?, ?)";
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            ps.setLong(2, lectureId);
            ps.setBoolean(3, attended);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("set attendance failed", e);
        }
    }

    public void toggle(long memberId, long lectureId) {
        set(memberId, lectureId, !isAttended(memberId, lectureId));
    }

    public Map<Long, Set<Long>> attendedByMember() {
        String sql = "SELECT member_id, lecture_id FROM attendance WHERE attended = TRUE";
        Map<Long, Set<Long>> result = new HashMap<>();
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.computeIfAbsent(rs.getLong(1), k -> new HashSet<>()).add(rs.getLong(2));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("attendedByMember failed", e);
        }
    }
}
