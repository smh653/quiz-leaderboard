import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class QuizLeaderboard {

    static final String REG_NO   = "RA2311026010701";
    static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    static final int TOTAL_POLLS = 10;
    static final int DELAY_MS    = 2000;

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        Set<String> seen = new HashSet<>();
        Map<String, Integer> scores = new LinkedHashMap<>();

        for (int poll = 0; poll < TOTAL_POLLS; poll++) {
            if (poll > 0) {
                System.out.printf(" Waiting %ds before next poll...%n", DELAY_MS / 1000);
                Thread.sleep(DELAY_MS);
            }

            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;
            System.out.printf("%n[Poll %d] GET %s%n", poll, url);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            String body = res.body();
            System.out.println("  Response: " + body);

            int eventsStart = body.indexOf("[");
            int eventsEnd   = body.lastIndexOf("]");
            if (eventsStart == -1 || eventsEnd == -1) {
                System.out.println("  No events array found, skipping.");
                continue;
            }

            String eventsJson = body.substring(eventsStart + 1, eventsEnd).trim();
            if (eventsJson.isEmpty()) {
                System.out.println("  Empty events, skipping.");
                continue;
            }

            String[] eventObjs = eventsJson.split("\\},\\s*\\{");
            int added = 0, dupes = 0;

            for (String obj : eventObjs) {
                String roundId    = extractJsonString(obj, "roundId");
                String participant = extractJsonString(obj, "participant");
                int    score      = extractJsonInt(obj, "score");

                if (roundId == null || participant == null) continue;

                String key = roundId + "||" + participant;
                if (seen.contains(key)) {
                    dupes++;
                    continue;
                }
                seen.add(key);
                scores.merge(participant, score, Integer::sum);
                added++;
            }

            System.out.printf("  +%d new event(s), %d duplicate(s) ignored%n", added, dupes);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(scores.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());

        System.out.println("\n══════════════════════════════");
        System.out.println("         LEADERBOARD          ");
        System.out.println("══════════════════════════════");

        StringBuilder leaderboardJson = new StringBuilder("[");
        int total = 0;
        for (int i = 0; i < entries.size(); i++) {
            String name  = entries.get(i).getKey();
            int    score = entries.get(i).getValue();
            total += score;
            System.out.printf("  %2d. %-20s %d%n", i + 1, name, score);
            if (i > 0) leaderboardJson.append(",");
            leaderboardJson.append(String.format(
                    "{\"participant\":\"%s\",\"totalScore\":%d}", name, score));
        }
        leaderboardJson.append("]");

        System.out.println("──────────────────────────────");
        System.out.printf("  Total score: %d%n", total);
        System.out.println("══════════════════════════════");

        String submitBody = String.format(
                "{\"regNo\":\"%s\",\"leaderboard\":%s}", REG_NO, leaderboardJson);

        System.out.println("\n[Submit] POST " + BASE_URL + "/quiz/submit");
        System.out.println("  Body: " + submitBody);

        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(submitBody))
                .build();

        HttpResponse<String> submitRes = client.send(submitReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("\n[Result] " + submitRes.body());
    }


    static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx + search.length());
        if (colon == -1) return null;
        int q1 = json.indexOf("\"", colon + 1);
        if (q1 == -1) return null;
        int q2 = json.indexOf("\"", q1 + 1);
        if (q2 == -1) return null;
        return json.substring(q1 + 1, q2);
    }

    static int extractJsonInt(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return 0;
        int colon = json.indexOf(":", idx + search.length());
        if (colon == -1) return 0;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try { return Integer.parseInt(json.substring(start, end).trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
