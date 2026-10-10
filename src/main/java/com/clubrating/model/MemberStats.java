package com.clubrating.model;

import java.util.List;
import java.util.Set;

public record MemberStats(
        Member member,
        List<Lecture> lectures,
        Set<Long> attendedLectureIds,
        List<EventResult> results,
        int points,
        int rank,
        int totalMembers) {

    public record EventResult(ClubEvent event, int place, int points) {
    }

    public int attendedCount() {
        return (int) attendedLectureIds.stream().filter(id -> lectures.stream().anyMatch(l -> l.id() == id)).count();
    }
}
