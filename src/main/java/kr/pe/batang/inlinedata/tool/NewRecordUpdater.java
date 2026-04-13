package kr.pe.batang.inlinedata.tool;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * meet.sports.or.kr의 신기록 명세 페이지에서 대회신기록 정보를 수집하여
 * 기존 event_result의 new_record 컬럼을 업데이트한다.
 *
 * 실행: java -cp "build/classes/java/main:..." kr.pe.batang.inlinedata.tool.NewRecordUpdater
 */
public class NewRecordUpdater {

    private static final String NEWREC_URL = "https://meet.sports.or.kr/history/newrec/newRecList.do";
    private static final String DB_URL = "jdbc:mariadb://localhost:3306/inlinedata?useUnicode=true&characterEncoding=UTF-8";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "parksj11";
    private static final int DELAY_MS = 500;

    record CompInfo(long id, int edition, String shortName, String gubun, String classCd) {}
    record NewRecord(String division, String eventName, String roundName,
                     String athleteName, String record) {}

    public static void main(String[] args) throws Exception {
        disableSslVerification();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // 대회 목록 조회
            List<CompInfo> competitions = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, edition, short_name FROM competition " +
                    "WHERE short_name IN ('전국체육대회','전국소년체육대회') AND edition IS NOT NULL ORDER BY short_name, edition")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String shortName = rs.getString("short_name");
                    String gubun = shortName.equals("전국체육대회") ? "G" : "Y";
                    String classCd = shortName.equals("전국체육대회") ? "30" : "27";
                    competitions.add(new CompInfo(rs.getLong("id"), rs.getInt("edition"), shortName, gubun, classCd));
                }
            }
            System.out.println("대회 수: " + competitions.size());

            int totalUpdated = 0, totalNotFound = 0, totalSkipped = 0;

            for (CompInfo comp : competitions) {
                List<NewRecord> records = fetchNewRecords(comp.gubun, comp.edition, comp.classCd);
                if (records.isEmpty()) {
                    continue;
                }

                int updated = 0, notFound = 0, skipped = 0;
                for (NewRecord nr : records) {
                    // 계주/릴레이 스킵
                    if (nr.eventName.contains("계주") || nr.eventName.contains("릴레이")) {
                        skipped++;
                        continue;
                    }
                    // 선수명 없는 건 스킵 (팀 종목)
                    if (nr.athleteName == null || nr.athleteName.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    int result = updateNewRecord(conn, comp.id, nr);
                    if (result > 0) {
                        updated += result;
                    } else {
                        notFound++;
                    }
                }

                if (updated > 0 || notFound > 0) {
                    System.out.printf("  %s %d회: 신기록 %d건, 업데이트 %d건, 미매칭 %d건, 스킵 %d건%n",
                            comp.shortName, comp.edition, records.size(), updated, notFound, skipped);
                }
                totalUpdated += updated;
                totalNotFound += notFound;
                totalSkipped += skipped;

                Thread.sleep(DELAY_MS);
            }

            System.out.printf("%n완료! 업데이트: %d건, 미매칭: %d건, 스킵: %d건%n",
                    totalUpdated, totalNotFound, totalSkipped);
        }
    }

    private static List<NewRecord> fetchNewRecords(String gubun, int gameno, String classCd) throws IOException {
        String url = NEWREC_URL + "?searchGubun=" + gubun + "&searchGameno=" + gameno
                + "&searchNewRecCd=09&searchClassCd=" + classCd;

        Document doc = Jsoup.connect(url).timeout(15000).get();
        List<NewRecord> records = new ArrayList<>();

        // data-label 기반 파싱
        Elements tds = doc.select("td[data-label]");
        String division = null, eventName = null, roundName = null;
        String athleteName = null, record = null;

        for (Element td : tds) {
            String label = td.attr("data-label");
            String value = td.text().trim();

            switch (label) {
                case "종별" -> { if (!value.isEmpty()) division = value; }
                case "세부종목" -> { if (!value.isEmpty()) eventName = value; }
                case "경기구분" -> { if (!value.isEmpty()) roundName = value; }
                case "선수명/팀명" -> athleteName = value;
                case "기록" -> record = value;
                case "일자" -> {
                    // 마지막 필드 → 하나의 레코드 완성
                    if (division != null && eventName != null && roundName != null) {
                        records.add(new NewRecord(division, eventName, roundName, athleteName, record));
                    }
                    athleteName = null;
                    record = null;
                }
            }
        }

        return records;
    }

    /**
     * 신기록 페이지의 종별명을 DB의 종별명으로 매핑한다.
     * 예: "남자초등부" → ["남초", "남자초등부", "남자12세이하부", "남자13세이하부"]
     */
    private static List<String> divisionVariants(String division) {
        List<String> variants = new ArrayList<>();
        variants.add(division); // 원본

        // "남자초등부" → "남초"
        String shortened = division
                .replace("자초등부", "초")
                .replace("자중학부", "중")
                .replace("자고등부", "고")
                .replace("자일반부", "일")
                .replace("자대학부", "대");
        if (!shortened.equals(division)) variants.add(shortened);

        // 나이 기반 부별 (소년체전)
        if (division.contains("초등") || division.contains("초")) {
            String prefix = division.startsWith("여") ? "여자" : "남자";
            variants.add(prefix + "12세이하부");
            variants.add(prefix + "13세이하부");
        }
        if (division.contains("중학") || division.contains("중")) {
            String prefix = division.startsWith("여") ? "여자" : "남자";
            variants.add(prefix + "15세이하부");
            variants.add(prefix + "16세이하부");
            variants.add(prefix.substring(0, 1) + "16이하");
        }
        if (division.contains("고등") || division.contains("고")) {
            String prefix = division.startsWith("여") ? "여자" : "남자";
            variants.add(prefix + "18세이하부");
            variants.add(prefix + "19세이하부");
        }

        return variants;
    }

    private static int updateNewRecord(Connection conn, long compId, NewRecord nr) throws SQLException {
        // 종목명 정규화: 쉼표 제거
        String eventNameNorm = nr.eventName.replace(",", "");

        // event_result를 종별+종목+라운드+선수명+기록으로 매칭
        String sql = """
            UPDATE event_result er
            JOIN heat_entry he ON he.id = er.heat_entry_id
            JOIN competition_entry ce ON ce.id = he.entry_id
            JOIN event_heat eh ON eh.id = he.heat_id
            JOIN event_round r ON r.id = eh.event_round_id
            JOIN event e ON e.id = r.event_id
            WHERE e.competition_id = ?
              AND e.division_name = ?
              AND REPLACE(e.event_name, ',', '') = ?
              AND r.round = ?
              AND ce.athlete_name = ?
              AND er.record = ?
              AND er.new_record IS NULL
            """;

        // 종별 변형 + 라운드 유연 매칭으로 시도
        List<String> divVariants = divisionVariants(nr.division);
        // 라운드명: "예선2조" → "예선2조"와 "예선" 모두 시도
        List<String> roundVariants = new ArrayList<>();
        roundVariants.add(nr.roundName);
        String roundBase = nr.roundName.replaceAll("[0-9]+조$", "").replaceAll("경기\\d+$", "").trim();
        if (!roundBase.equals(nr.roundName) && !roundBase.isEmpty()) {
            roundVariants.add(roundBase);
        }

        for (String div : divVariants) {
            for (String rnd : roundVariants) {
                String checkSql = """
                    SELECT er.id FROM event_result er
                    JOIN heat_entry he ON he.id = er.heat_entry_id
                    JOIN competition_entry ce ON ce.id = he.entry_id
                    JOIN event_heat eh ON eh.id = he.heat_id
                    JOIN event_round r ON r.id = eh.event_round_id
                    JOIN event e ON e.id = r.event_id
                    WHERE e.competition_id = ?
                      AND e.division_name = ?
                      AND REPLACE(e.event_name, ',', '') = ?
                      AND r.round = ?
                      AND ce.athlete_name = ?
                      AND er.record = ?
                    """;

                try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                    check.setLong(1, compId);
                    check.setString(2, div);
                    check.setString(3, eventNameNorm);
                    check.setString(4, rnd);
                    check.setString(5, nr.athleteName);
                    check.setString(6, nr.record);
                    if (!check.executeQuery().next()) continue;
                }

                String updateSql = """
                    UPDATE event_result er
                    JOIN heat_entry he ON he.id = er.heat_entry_id
                    JOIN competition_entry ce ON ce.id = he.entry_id
                    JOIN event_heat eh ON eh.id = he.heat_id
                    JOIN event_round r ON r.id = eh.event_round_id
                    JOIN event e ON e.id = r.event_id
                    SET er.new_record = '대회신'
                    WHERE e.competition_id = ?
                      AND e.division_name = ?
                      AND REPLACE(e.event_name, ',', '') = ?
                      AND r.round = ?
                      AND ce.athlete_name = ?
                      AND er.record = ?
                    """;

                try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                    upd.setLong(1, compId);
                    upd.setString(2, div);
                    upd.setString(3, eventNameNorm);
                    upd.setString(4, rnd);
                    upd.setString(5, nr.athleteName);
                    upd.setString(6, nr.record);
                    return upd.executeUpdate();
                }
            }
        }
        return 0; // 모든 변형으로도 매칭 실패
    }

    private static void disableSslVerification() {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}