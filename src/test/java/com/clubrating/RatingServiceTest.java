package com.clubrating;

import com.clubrating.service.RatingService;
import com.clubrating.bot.Ui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RatingServiceTest {

    @Test
    void pointsForPlaces() {
        assertEquals(3, RatingService.pointsFor(1));
        assertEquals(2, RatingService.pointsFor(2));
        assertEquals(1, RatingService.pointsFor(3));
        assertEquals(0, RatingService.pointsFor(4));
        assertEquals(0, RatingService.pointsFor(10));
    }

    @Test
    void pluralizePoints() {
        assertEquals("очко", Ui.pluralizePoints(1));
        assertEquals("очка", Ui.pluralizePoints(2));
        assertEquals("очка", Ui.pluralizePoints(4));
        assertEquals("очков", Ui.pluralizePoints(5));
        assertEquals("очков", Ui.pluralizePoints(11));
        assertEquals("очков", Ui.pluralizePoints(12));
        assertEquals("очко", Ui.pluralizePoints(21));
        assertEquals("очков", Ui.pluralizePoints(0));
    }

    @Test
    void pluralizeEvents() {
        assertEquals("мероприятие", Ui.pluralizeEvents(1));
        assertEquals("мероприятия", Ui.pluralizeEvents(3));
        assertEquals("мероприятий", Ui.pluralizeEvents(5));
        assertEquals("мероприятий", Ui.pluralizeEvents(11));
    }

    @Test
    void pluralizeMembers() {
        assertEquals("участник", Ui.pluralizeMembers(1));
        assertEquals("участника", Ui.pluralizeMembers(2));
        assertEquals("участников", Ui.pluralizeMembers(5));
        assertEquals("участников", Ui.pluralizeMembers(12));
    }

    @Test
    void htmlEscape() {
        assertEquals("a &lt;b&gt; &amp; c", Ui.esc("a <b> & c"));
    }
}
