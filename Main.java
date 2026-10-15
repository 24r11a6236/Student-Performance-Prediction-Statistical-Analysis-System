package src;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

public class Main {
    static int PORT = 8080;
    static final Path DATA_DIR = Paths.get("data");
    static final Path CSV = DATA_DIR.resolve("students.csv");
    static final Map<String, Student> students = new LinkedHashMap<>();
    static final Set<String> sessions = new HashSet<>();

    public static class Student {
        public String id, name, rollNo, department, createdAt;
        public int year, attendance, internalMarks, studyHours;
        public Double finalMarks;

        public Student(String id, String name, String rollNo, String department, int year,
                       int attendance, int internalMarks, int studyHours, Double finalMarks, String createdAt) {
            this.id = id;
            this.name = name;
            this.rollNo = rollNo;
            this.department = department;
            this.year = year;
            this.attendance = attendance;
            this.internalMarks = internalMarks;
            this.studyHours = studyHours;
            this.finalMarks = finalMarks;
            this.createdAt = createdAt;
        }

        public double prediction() {
            return clamp(0.50 * internalMarks + 0.25 * attendance + 2.5 * studyHours, 0, 100);
        }

        public String risk() {
            double p = prediction();
            return p >= 75 ? "Low" : p >= 50 ? "Medium" : "High";
        }

        public String toJson() {
            return String.format("{\"id\":\"%s\",\"name\":%s,\"rollNo\":%s,\"department\":%s,\"year\":%d,\"attendance\":%d,\"internalMarks\":%d,\"studyHours\":%d,\"finalMarks\":%s,\"createdAt\":\"%s\",\"predictedScore\":%.2f,\"risk\":\"%s\"}",
                    id, q(name), q(rollNo), q(department), year, attendance, internalMarks, studyHours,
                    finalMarks == null ? "null" : finalMarks, createdAt, round(prediction()), risk());
        }

        public String toCsvRow() {
            return String.join(",", csvq(id), csvq(name), csvq(rollNo), csvq(department),
                    String.valueOf(year), String.valueOf(attendance), String.valueOf(internalMarks),
                    String.valueOf(studyHours), finalMarks == null ? "" : String.valueOf(finalMarks), csvq(createdAt));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) PORT = Integer.parseInt(args[0]);
        Files.createDirectories(DATA_DIR);
        loadData();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Student Performance System running at http://localhost:" + PORT);
        System.out.println("Demo login: admin / admin123");
    }

    static void loadData() throws IOException {
        if (!Files.exists(CSV)) { seed(); saveData(); return; }
        List<String> lines = Files.readAllLines(CSV, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> c = parseCsv(lines.get(i));
            if (c.size() < 10) continue;
            try {
                Student s = new Student(c.get(0), c.get(1), c.get(2), c.get(3),
                        Integer.parseInt(c.get(4)), Integer.parseInt(c.get(5)),
                        Integer.parseInt(c.get(6)), Integer.parseInt(c.get(7)),
                        c.get(8).isBlank() ? null : Double.parseDouble(c.get(8)), c.get(9));
                students.put(s.id, s);
            } catch (Exception ignored) {}
        }
    }

    static void saveData() throws IOException {
        StringBuilder sb = new StringBuilder("id,name,rollNo,department,year,attendance,internalMarks,studyHours,finalMarks,createdAt\n");
        for (Student s : students.values()) {
            sb.append(s.toCsvRow()).append("\n");
        }
        Files.writeString(CSV, sb.toString(), StandardCharsets.UTF_8);
    }

    static void seed() {
        String now = java.time.Instant.now().toString();
        students.put("1", new Student("1", "Aarav Sharma", "CSE001", "CSE", 2, 92, 85, 3, 88.0, now));
        students.put("2", new Student("2", "Diya Reddy", "CSE002", "CSE", 2, 78, 72, 2, 76.0, now));
        students.put("3", new Student("3", "Rahul Kumar", "ECE001", "ECE", 2, 65, 58, 1, 61.0, now));
        students.put("4", new Student("4", "Meera Patel", "CSE003", "CSE", 2, 96, 91, 4, 94.0, now));
        students.put("5", new Student("5", "Arjun Rao", "IT001", "IT", 2, 70, 64, 2, 68.0, now));
        students.put("6", new Student("6", "Sana Khan", "ECE002", "ECE", 2, 83, 79, 3, 82.0, now));
    }

    static void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        try {
            if (path.equals("/api/login") && method.equals("POST")) {
                Map<String, String> b = body(ex);
                if ("admin".equals(b.get("username")) && "admin123".equals(b.get("password"))) {
                    String token = UUID.randomUUID().toString();
                    sessions.add(token);
                    ex.getResponseHeaders().add("Set-Cookie", "SPSID=" + token + "; Path=/; HttpOnly; SameSite=Strict");
                    send(ex, 200, "{\"ok\":true,\"message\":\"Welcome back\"}");
                    return;
                }
                send(ex, 401, "{\"error\":\"Invalid username or password\"}");
                return;
            }

            if (path.equals("/api/logout") && method.equals("POST")) {
                sessions.remove(getCookie(ex, "SPSID"));
                send(ex, 200, "{\"ok\":true}");
                return;
            }

            if (path.startsWith("/api/")) {
                if (!authenticated(ex)) {
                    send(ex, 401, "{\"error\":\"Please log in\"}");
                    return;
                }

                if (path.equals("/api/students") && method.equals("GET")) {
                    send(ex, 200, jsonList());
                } else if (path.equals("/api/students") && method.equals("POST")) {
                    Map<String, String> b = body(ex);
                    Student s = parseStudent(b, UUID.randomUUID().toString(), java.time.Instant.now().toString());
                    validate(s);
                    for (Student existing : students.values()) {
                        if (existing.rollNo.equalsIgnoreCase(s.rollNo)) {
                            send(ex, 409, "{\"error\":\"Roll number already exists\"}");
                            return;
                        }
                    }
                    students.put(s.id, s);
                    saveData();
                    send(ex, 201, s.toJson());
                } else if (path.startsWith("/api/students/") && (method.equals("PUT") || method.equals("DELETE"))) {
                    String id = path.substring("/api/students/".length());
                    if (!students.containsKey(id)) {
                        send(ex, 404, "{\"error\":\"Student not found\"}");
                        return;
                    }
                    if (method.equals("DELETE")) {
                        students.remove(id);
                        saveData();
                        send(ex, 200, "{\"ok\":true}");
                    } else {
                        Map<String, String> b = body(ex);
                        Student old = students.get(id);
                        Student s = parseStudent(b, id, old.createdAt);
                        validate(s);
                        for (Student existing : students.values()) {
                            if (!existing.id.equals(id) && existing.rollNo.equalsIgnoreCase(s.rollNo)) {
                                send(ex, 409, "{\"error\":\"Roll number already exists\"}");
                                return;
                            }
                        }
                        students.put(id, s);
                        saveData();
                        send(ex, 200, s.toJson());
                    }
                } else if (path.equals("/api/dashboard") && method.equals("GET")) {
                    send(ex, 200, dashboardJson());
                } else if (path.equals("/api/stats") && method.equals("GET")) {
                    send(ex, 200, statsJson());
                } else if (path.equals("/api/export") && method.equals("GET")) {
                    ex.getResponseHeaders().add("Content-Type", "text/csv");
                    ex.getResponseHeaders().add("Content-Disposition", "attachment; filename=students.csv");
                    byte[] bytes = Files.readAllBytes(CSV);
                    ex.sendResponseHeaders(200, bytes.length);
                    OutputStream os = ex.getResponseBody();
                    os.write(bytes);
                    os.close();
                } else {
                    send(ex, 404, "{\"error\":\"Not found\"}");
                }
                return;
            }

            // Serve static files from frontend folder
            serveStatic(ex, path);

        } catch (IllegalArgumentException e) {
            send(ex, 400, "{\"error\":" + q(e.getMessage()) + "}");
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "{\"error\":\"Server error\"}");
        }
    }

    static boolean authenticated(HttpExchange ex) {
        String token = getCookie(ex, "SPSID");
        return token != null && sessions.contains(token);
    }

    static String getCookie(HttpExchange ex, String name) {
        String cookieHeader = ex.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) return parts[1];
        }
        return null;
    }

    static void validate(Student s) {
        if (s.name.length() < 2 || s.name.length() > 80) throw new IllegalArgumentException("Name must be 2–80 characters");
        if (s.rollNo.length() > 30) throw new IllegalArgumentException("Roll number is too long");
        if (s.department.length() < 2 || s.department.length() > 60) throw new IllegalArgumentException("Enter a valid department");
        if (s.year < 1 || s.year > 6) throw new IllegalArgumentException("Year must be between 1 and 6");
        if (s.attendance < 0 || s.attendance > 100) throw new IllegalArgumentException("Attendance must be 0–100");
        if (s.internalMarks < 0 || s.internalMarks > 100) throw new IllegalArgumentException("Internal marks must be 0–100");
        if (s.studyHours < 0 || s.studyHours > 24) throw new IllegalArgumentException("Study hours must be 0–24");
        if (s.finalMarks != null && (s.finalMarks < 0 || s.finalMarks > 100)) throw new IllegalArgumentException("Final marks must be 0–100");
    }

    static Student parseStudent(Map<String, String> b, String id, String createdAt) {
        return new Student(
                id,
                b.getOrDefault("name", "").trim(),
                b.getOrDefault("rollNo", "").trim(),
                b.getOrDefault("department", "").trim(),
                Integer.parseInt(b.getOrDefault("year", "1")),
                Integer.parseInt(b.getOrDefault("attendance", "0")),
                Integer.parseInt(b.getOrDefault("internalMarks", "0")),
                Integer.parseInt(b.getOrDefault("studyHours", "0")),
                b.get("finalMarks") == null || b.get("finalMarks").isBlank() ? null : Double.parseDouble(b.get("finalMarks")),
                createdAt
        );
    }

    static String jsonList() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Student s : students.values()) {
            if (!first) sb.append(",");
            sb.append(s.toJson());
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    static String dashboardJson() {
        int total = students.size();
        double avgPred = students.values().stream().mapToDouble(Student::prediction).average().orElse(0);
        double avgAtt = students.values().stream().mapToInt(s -> s.attendance).average().orElse(0);
        long atRisk = students.values().stream().filter(s -> s.risk().equals("High")).count();
        double avgFinal = students.values().stream().filter(s -> s.finalMarks != null).mapToDouble(s -> s.finalMarks).average().orElse(0);
        
        Map<String, Integer> depts = new HashMap<>();
        for (Student s : students.values()) {
            depts.put(s.department, depts.getOrDefault(s.department, 0) + 1);
        }

        StringBuilder deptJson = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : depts.entrySet()) {
            if (!first) deptJson.append(",");
            deptJson.append(q(e.getKey())).append(":").append(e.getValue());
            first = false;
        }
        deptJson.append("}");

        return String.format("{\"totalStudents\":%d,\"averagePrediction\":%.2f,\"averageAttendance\":%.2f,\"atRisk\":%d,\"averageFinalMarks\":%.2f,\"departments\":%s}",
                total, round(avgPred), round(avgAtt), atRisk, round(avgFinal), deptJson.toString());
    }

    static String statsJson() {
        List<Double> preds = new ArrayList<>();
        List<Double> finals = new ArrayList<>();
        List<Double> hours = new ArrayList<>();
        for (Student s : students.values()) {
            preds.add(s.prediction());
            if (s.finalMarks != null) finals.add(s.finalMarks);
            hours.add((double) s.studyHours);
        }
        return String.format("{\"pred\":%s,\"final\":%s,\"hours\":%s,\"correlation\":%.3f}",
                calcStatObj(preds), calcStatObj(finals), calcStatObj(hours), correlation(students.values()));
    }

    static String calcStatObj(List<Double> a) {
        if (a.isEmpty()) return "{\"count\":0,\"mean\":0,\"median\":0,\"mode\":null,\"variance\":0,\"stdDev\":0}";
        List<Double> s = new ArrayList<>(a);
        Collections.sort(s);
        double mean = s.stream().mapToDouble(x -> x).average().orElse(0);
        double var = 0;
        for (double x : s) var += (x - mean) * (x - mean);
        var /= s.size();

        Map<Double, Integer> freq = new HashMap<>();
        for (double x : s) freq.put(x, freq.getOrDefault(x, 0) + 1);
        int max = 0;
        for (int count : freq.values()) max = Math.max(max, count);
        Double mode = null;
        int modes = 0;
        if (max > 1) {
            for (Map.Entry<Double, Integer> e : freq.entrySet()) {
                if (e.getValue() == max) { mode = e.getKey(); modes++; }
            }
            if (modes != 1) mode = null;
        }
        double med = s.size() % 2 == 1 ? s.get(s.size() / 2) : (s.get(s.size() / 2 - 1) + s.get(s.size() / 2)) / 2.0;

        return String.format("{\"count\":%d,\"mean\":%.2f,\"median\":%.2f,\"mode\":%s,\"variance\":%.2f,\"stdDev\":%.2f}",
                s.size(), round(mean), round(med), mode == null ? "null" : String.format("%.2f", mode), round(var), round(Math.sqrt(var)));
    }

    static double correlation(Collection<Student> ss) {
        List<Student> a = new ArrayList<>();
        for (Student s : ss) if (s.finalMarks != null) a.add(s);
        if (a.size() < 2) return 0;
        double x = 0, y = 0;
        for (Student s : a) { x += s.studyHours; y += s.finalMarks; }
        x /= a.size(); y /= a.size();
        double num = 0, dx = 0, dy = 0;
        for (Student s : a) {
            num += (s.studyHours - x) * (s.finalMarks - y);
            dx += Math.pow(s.studyHours - x, 2);
            dy += Math.pow(s.finalMarks - y, 2);
        }
        return dx == 0 || dy == 0 ? 0 : num / Math.sqrt(dx * dy);
    }

    static void serveStatic(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        Path file = Paths.get("frontend", path);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            send(ex, 404, "Not found");
            return;
        }
        String mime = "text/plain";
        if (path.endsWith(".html")) mime = "text/html";
        else if (path.endsWith(".css")) mime = "text/css";
        else if (path.endsWith(".js")) mime = "application/javascript";

        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().add("Content-Type", mime);
        ex.sendResponseHeaders(200, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static Map<String, String> body(HttpExchange ex) throws IOException {
        String bodyStr = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        if (bodyStr.isBlank()) return map;
        for (String pair : bodyStr.replace("{", "").replace("}", "").replace("\"", "").split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }

    static void send(HttpExchange ex, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static double round(double d) { return Math.round(d * 100.0) / 100.0; }
    static String q(String s) { return "\"" + s.replace("\"", "\\\"") + "\""; }
    static String csvq(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }

    static List<String> parseCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean in = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (in && i + 1 < line.length() && line.charAt(i + 1) == '"') { b.append('"'); i++; }
                else in = !in;
            } else if (c == ',' && !in) {
                out.add(b.toString());
                b.setLength(0);
            } else b.append(c);
        }
        out.add(b.toString());
        return out;
    }
}