package com.clubrating.service;

import com.clubrating.model.Lecture;
import com.clubrating.model.Member;
import com.clubrating.repo.AttendanceRepository;
import com.clubrating.repo.EventRepository;
import com.clubrating.repo.LectureRepository;
import com.clubrating.repo.MemberRepository;
import com.clubrating.repo.ResultRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExcelService {

    public static final String SHEET_LECTURES = "Лекции";
    public static final String SHEET_RESULTS = "Места";
    public static final String COL_MEMBER = "Участник";
    public static final String COL_EVENT = "Мероприятие";
    public static final String COL_PLACE = "Место";

    private static final int MAX_NAME = 120;
    private static final int MAX_TITLE = 200;

    private final MemberRepository members;
    private final LectureRepository lectures;
    private final EventRepository events;
    private final AttendanceRepository attendance;
    private final ResultRepository results;

    public ExcelService(MemberRepository members,
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

    public record ImportSummary(int membersCreated, int lecturesCreated, int eventsCreated,
                                int attendanceMarked, int resultsSaved) {
    }

    public ImportSummary importFrom(InputStream input) throws Exception {
        int membersCreated = 0;
        int lecturesCreated = 0;
        int eventsCreated = 0;
        int attendanceMarked = 0;
        int resultsSaved = 0;

        try (Workbook wb = WorkbookFactory.create(input)) {
            Sheet lecturesSheet = wb.getSheet(SHEET_LECTURES);
            if (lecturesSheet != null) {
                Row header = lecturesSheet.getRow(lecturesSheet.getFirstRowNum());
                if (header != null && header.getLastCellNum() >= 1) {
                    Map<String, Long> lectureByTitle = new HashMap<>();
                    List<Lecture> existing = lectures.findAllOrdered();
                    for (Lecture l : existing) {
                        lectureByTitle.put(normalize(l.title()), l.id());
                    }
                    Map<String, Long> memberByName = new HashMap<>();
                    for (Member m : members.findAllOrdered()) {
                        memberByName.put(normalize(m.name()), m.id());
                    }

                    for (int c = 1; c < header.getLastCellNum(); c++) {
                        String title = truncate(cellString(header.getCell(c)), MAX_TITLE);
                        if (title.isBlank()) {
                            continue;
                        }
                        String key = normalize(title);
                        if (!lectureByTitle.containsKey(key)) {
                            Lecture created = lectures.create(title);
                            lectureByTitle.put(key, created.id());
                            lecturesCreated++;
                        }
                    }

                    for (int r = lecturesSheet.getFirstRowNum() + 1; r <= lecturesSheet.getLastRowNum(); r++) {
                        Row row = lecturesSheet.getRow(r);
                        if (row == null) {
                            continue;
                        }
                        String memberName = truncate(cellString(row.getCell(0)), MAX_NAME);
                        if (memberName.isBlank()) {
                            continue;
                        }
                        String memberKey = normalize(memberName);
                        Long memberId = memberByName.get(memberKey);
                        if (memberId == null) {
                            Member created = members.create(null, memberName.trim());
                            memberId = created.id();
                            memberByName.put(memberKey, memberId);
                            membersCreated++;
                        }
                        for (int c = 1; c < row.getLastCellNum(); c++) {
                            String lectureTitle = truncate(cellString(header.getCell(c)), MAX_TITLE);
                            if (lectureTitle.isBlank()) {
                                continue;
                            }
                            Long lectureId = lectureByTitle.get(normalize(lectureTitle));
                            if (lectureId == null) {
                                continue;
                            }
                            Boolean attended = parseAttended(cellString(row.getCell(c)));
                            if (attended != null) {
                                attendance.set(memberId, lectureId, attended);
                                attendanceMarked++;
                            }
                        }
                    }
                }
            }

            Sheet resultsSheet = wb.getSheet(SHEET_RESULTS);
            if (resultsSheet != null) {
                int memberCol = -1;
                int eventCol = -1;
                int placeCol = -1;
                Row header = resultsSheet.getRow(resultsSheet.getFirstRowNum());
                if (header != null) {
                    for (int c = 0; c < header.getLastCellNum(); c++) {
                        String value = cellString(header.getCell(c)).toLowerCase(Locale.ROOT);
                        if (value.equals(COL_MEMBER.toLowerCase(Locale.ROOT))) {
                            memberCol = c;
                        } else if (value.equals(COL_EVENT.toLowerCase(Locale.ROOT))) {
                            eventCol = c;
                        } else if (value.equals(COL_PLACE.toLowerCase(Locale.ROOT))) {
                            placeCol = c;
                        }
                    }
                }
                if (memberCol >= 0 && eventCol >= 0 && placeCol >= 0) {
                    Map<String, Long> memberByName = new HashMap<>();
                    for (Member m : members.findAllOrdered()) {
                        memberByName.put(normalize(m.name()), m.id());
                    }
                    Map<String, Long> eventByTitle = new HashMap<>();
                    for (var e : events.findAllOrdered()) {
                        eventByTitle.put(normalize(e.title()), e.id());
                    }

                    for (int r = resultsSheet.getFirstRowNum() + 1; r <= resultsSheet.getLastRowNum(); r++) {
                        Row row = resultsSheet.getRow(r);
                        if (row == null) {
                            continue;
                        }
                        String memberName = truncate(cellString(row.getCell(memberCol)), MAX_NAME);
                        String eventTitle = truncate(cellString(row.getCell(eventCol)), MAX_TITLE);
                        String placeRaw = cellString(row.getCell(placeCol));
                        if (memberName.isBlank() || eventTitle.isBlank() || placeRaw.isBlank()) {
                            continue;
                        }
                        int place;
                        try {
                            place = Integer.parseInt(placeRaw.trim());
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        if (place < 1) {
                            continue;
                        }
                        String memberKey = normalize(memberName);
                        Long memberId = memberByName.get(memberKey);
                        if (memberId == null) {
                            Member created = members.create(null, memberName.trim());
                            memberId = created.id();
                            memberByName.put(memberKey, memberId);
                            membersCreated++;
                        }
                        String eventKey = normalize(eventTitle);
                        Long eventId = eventByTitle.get(eventKey);
                        if (eventId == null) {
                            var created = events.create(eventTitle.trim());
                            eventId = created.id();
                            eventByTitle.put(eventKey, eventId);
                            eventsCreated++;
                        }
                        results.upsert(eventId, memberId, place);
                        resultsSaved++;
                    }
                }
            }
        }

        return new ImportSummary(membersCreated, lecturesCreated, eventsCreated, attendanceMarked, resultsSaved);
    }

    public byte[] exportToBytes() throws Exception {
        List<Member> allMembers = members.findAllOrdered();
        List<Lecture> allLectures = lectures.findAllOrdered();

        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet lecturesSheet = wb.createSheet(SHEET_LECTURES);
            Row header = lecturesSheet.createRow(0);
            header.createCell(0).setCellValue(COL_MEMBER);
            header.getCell(0).setCellStyle(headerStyle);
            for (int i = 0; i < allLectures.size(); i++) {
                Cell cell = header.createCell(i + 1);
                cell.setCellValue(allLectures.get(i).title());
                cell.setCellStyle(headerStyle);
            }
            int rowNum = 1;
            Map<Long, java.util.Set<Long>> attendedByMember = attendance.attendedByMember();
            for (Member m : allMembers) {
                Row row = lecturesSheet.createRow(rowNum++);
                row.createCell(0).setCellValue(m.name());
                java.util.Set<Long> attended = attendedByMember.getOrDefault(m.id(), java.util.Set.of());
                for (int i = 0; i < allLectures.size(); i++) {
                    boolean was = attended.contains(allLectures.get(i).id());
                    row.createCell(i + 1).setCellValue(was ? "✓" : "—");
                }
            }

            Sheet resultsSheet = wb.createSheet(SHEET_RESULTS);
            Row resultsHeader = resultsSheet.createRow(0);
            for (int i = 0; i < 3; i++) {
                Cell cell = resultsHeader.createCell(i);
                cell.setCellStyle(headerStyle);
            }
            resultsHeader.getCell(0).setCellValue(COL_MEMBER);
            resultsHeader.getCell(1).setCellValue(COL_EVENT);
            resultsHeader.getCell(2).setCellValue(COL_PLACE);
            int r = 1;
            for (ResultRepository.RawResult raw : results.findAllRaw()) {
                Row row = resultsSheet.createRow(r++);
                row.createCell(0).setCellValue(raw.memberName());
                row.createCell(1).setCellValue(raw.eventTitle());
                row.createCell(2).setCellValue(raw.place());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> numericToString(cell.getNumericCellValue());
            case BOOLEAN -> cell.getBooleanCellValue() ? "✓" : "—";
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> numericToString(cell.getNumericCellValue());
                case BOOLEAN -> cell.getBooleanCellValue() ? "✓" : "—";
                default -> "";
            };
            default -> "";
        };
    }

    private static String numericToString(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max).trim();
    }

    private static Boolean parseAttended(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return null;
        }
        if (v.equals("✓") || v.equals("да") || v.equals("1") || v.equals("true") || v.equals("+") || v.equals("был")) {
            return true;
        }
        if (v.equals("—") || v.equals("-") || v.equals("нет") || v.equals("0") || v.equals("false") || v.equals("не был")) {
            return false;
        }
        return null;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
