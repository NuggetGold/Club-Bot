package com.clubrating.service;

import com.clubrating.model.ClubEvent;
import com.clubrating.model.Lecture;
import com.clubrating.model.Member;
import com.clubrating.model.MemberStats;
import com.clubrating.model.RatingRow;
import com.clubrating.repo.AttendanceRepository;
import com.clubrating.repo.EventRepository;
import com.clubrating.repo.LectureRepository;
import com.clubrating.repo.MemberRepository;
import com.clubrating.repo.ResultRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RatingService {

    public static final int POINTS_FIRST = 3;
    public static final int POINTS_SECOND = 2;
    public static final int POINTS_THIRD = 1;
    public static final int POINTS_OTHER = 0;

    private final MemberRepository members;
    private final LectureRepository lectures;
    private final EventRepository events;
    private final AttendanceRepository attendance;
    private final ResultRepository results;

    public RatingService(MemberRepository members,
                         LectureRepository lectures,
                         EventRepository events,
                         AttendanceRepository attendance,
                         ResultRepository results) {
        this.members = members;
        this.lectures = lectures;
        this.events = events;
        this.attendance = attendance;
        this.results = results;
    }

    public static int pointsFor(int place) {
        return switch (place) {
            case 1 -> POINTS_FIRST;
            case 2 -> POINTS_SECOND;
            case 3 -> POINTS_THIRD;
            default -> POINTS_OTHER;
        };
    }

    public List<RatingRow> leaderboard() {
        Map<Long, Integer> points = results.pointsByMember();
        Map<Long, Long> eventCounts = results.eventCountByMember();
        return members.findAllOrdered().stream()
                .map(m -> new RatingRow(m, points.getOrDefault(m.id(), 0), eventCounts.getOrDefault(m.id(), 0L)))
                .sorted(Comparator.comparingInt(RatingRow::points).reversed()
                        .thenComparing(r -> r.member().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public MemberStats statsFor(long memberId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + memberId));
        List<Lecture> allLectures = lectures.findAllOrdered();
        Set<Long> attended = attendance.attendedLectureIds(memberId);
        List<ClubEvent> allEvents = events.findAllOrdered();
        Map<Long, Integer> points = results.pointsByMember();
        Map<Long, Integer> myPlaces = results.placesForMember(memberId);
        int myPoints = points.getOrDefault(memberId, 0);

        List<MemberStats.EventResult> eventResults = allEvents.stream()
                .filter(e -> myPlaces.containsKey(e.id()))
                .map(e -> new MemberStats.EventResult(e, myPlaces.get(e.id()), pointsFor(myPlaces.get(e.id()))))
                .toList();

        List<RatingRow> board = leaderboard();
        int rank = -1;
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).member().id() == memberId) {
                rank = i + 1;
                break;
            }
        }
        if (rank == -1) {
            rank = board.size() + 1;
        }

        return new MemberStats(member, allLectures, attended, eventResults, myPoints, rank, Math.max(board.size(), 1));
    }
}
