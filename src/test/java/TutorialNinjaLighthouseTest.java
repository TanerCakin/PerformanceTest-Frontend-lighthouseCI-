import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class TutorialNinjaLighthouseTest {

    // ✅ STATIC BASE (write once, reuse everywhere)
    private static final String BASE_ROUTE_URL =
            "https://tutorialsninja.com/demo/index.php?route=";

    // Login URL built from base
    private static final String LOGIN_URL = BASE_ROUTE_URL + "account/login";

    // ===== Login Selectors =====
    private static final By EMAIL_INPUT = By.id("input-email");
    private static final By PASSWORD_INPUT = By.id("input-password");
    private static final By LOGIN_BUTTON =
            By.cssSelector("input[type='submit'][value='Login']");

    // ==========================================================
    // Page model (NO predefined pages)
    // ==========================================================
    static class PageConfig {
        final String name;          // label only (report file name)
        final String url;           // real URL Lighthouse will test
        final boolean requiresLogin;

        PageConfig(String name, String url, boolean requiresLogin) {
            this.name = name;
            this.url = url;
            this.requiresLogin = requiresLogin;
        }
    }

    public static void main(String[] args) throws Exception {
        String username = "qatester1987@gmail.com";
        String password = "pswd123";

        // ✅ USER ENTERS ONLY ROUTE PART (NO pageName, NO full URL)
        runByRoute(
                "product/category&path=24",
                false,
                username,
                password
        );

        // ✅ Example: login required page
        // runByRoute("account/wishlist", true, username, password);
    }

    // ==========================================================
    // ✅ MAIN ENTRY: user enters ONLY route part
    // Example:
    //   "product/category&path=33"
    //   "account/wishlist"
    // ==========================================================
    public static void runByRoute(
            String routePart,
            boolean requiresLogin,
            String username,
            String password
    ) throws Exception {

        if (routePart == null || routePart.trim().isEmpty()) {
            throw new IllegalArgumentException("routePart must not be empty. Example: product/category&path=33");
        }

        // Auto pageName from route (safe for filenames)
        String pageName = makeSafeName(routePart);

        // Build full URL
        String fullUrl = buildUrlFromRoute(routePart);

        runFrontendPerformanceTest(pageName, fullUrl, requiresLogin, username, password);
    }

    // ==========================================================
    // Build full URL from route part
    // Accepts:
    //   product/category&path=33
    //   account/wishlist
    // Also tolerates:
    //   route=product/category&path=33
    //   ?route=product/category&path=33
    //   https://tutorialsninja.com/demo/index.php?route=product/category&path=33
    // ==========================================================
    private static String buildUrlFromRoute(String routePart) {
        String rp = routePart.trim();

        // If full URL is passed, return as-is
        if (rp.startsWith("http://") || rp.startsWith("https://")) return rp;

        // Cleanup common pasted prefixes
        if (rp.startsWith("route=")) rp = rp.substring("route=".length());
        if (rp.startsWith("?route=")) rp = rp.substring("?route=".length());
        if (rp.startsWith("/")) rp = rp.substring(1);

        return BASE_ROUTE_URL + rp;
    }

    // Safe filename/page label generator
    private static String makeSafeName(String routePart) {
        String s = routePart.trim();

        // remove URL prefixes if pasted
        s = s.replace("https://tutorialsninja.com/demo/index.php?", "");
        s = s.replace("route=", "");
        s = s.replace("?route=", "");

        // safe characters for file names
        s = s.replace("/", "-")
                .replace("&", "_")
                .replace("=", "-")
                .replace("?", "")
                .replace(":", "")
                .replace("\\", "-");

        // keep it readable
        return s;
    }

    // ==========================================================
    // Core runner (builds profile, optionally logs in, runs Lighthouse)
    // ==========================================================
    public static void runFrontendPerformanceTest(
            String pageName,
            String pageUrl,
            boolean requiresLogin,
            String username,
            String password
    ) throws Exception {

        if (pageName == null || pageName.trim().isEmpty())
            throw new IllegalArgumentException("pageName must not be empty");

        if (pageUrl == null || pageUrl.trim().isEmpty())
            throw new IllegalArgumentException("pageUrl must not be empty");

        PageConfig page = new PageConfig(pageName.trim(), pageUrl.trim(), requiresLogin);

        System.out.println("\n========================================");
        System.out.println("Running Lighthouse for:");
        System.out.println("  pageName = " + page.name);
        System.out.println("  pageUrl  = " + page.url);
        System.out.println("  login    = " + page.requiresLogin);
        System.out.println("========================================");

        Path profileDir = Files.createTempDirectory("lh-profile-");
        System.out.println("Chrome profile: " + profileDir);

        Path extraHeadersFile = null;
        if (page.requiresLogin) {
            extraHeadersFile = loginAndCreateExtraHeadersFile(profileDir, username, password);
            System.out.println("Extra headers file: " + extraHeadersFile.toAbsolutePath());
        }

        MedianResult median = runLighthouse3Times(profileDir, page, extraHeadersFile);

        System.out.println("Median Result (" + page.name + "): " + median);

        assertThresholds(median);

        System.out.println("✅ FRONTEND PERFORMANCE TEST PASSED (" + page.name + ")");
    }

    // ==========================================================
    // Models
    // ==========================================================
    static class MedianResult {
        double perfScore;
        double a11yScore;
        double bpScore;
        double seoScore;

        double fcpMs;
        double siMs;
        double lcpMs;
        double tbtMs;
        double cls;

        @Override
        public String toString() {
            return "MedianResult{" +
                    "perf=" + (perfScore * 100.0) +
                    ", a11y=" + (a11yScore * 100.0) +
                    ", bp=" + (bpScore * 100.0) +
                    ", seo=" + (seoScore * 100.0) +
                    ", fcpMs=" + fcpMs +
                    ", siMs=" + siMs +
                    ", lcpMs=" + lcpMs +
                    ", tbtMs=" + tbtMs +
                    ", cls=" + cls +
                    '}';
        }
    }

    static class Result {
        double perfScore;
        double a11yScore;
        double bpScore;
        double seoScore;

        double fcpMs;
        double siMs;
        double lcpMs;
        double tbtMs;
        double cls;

        @Override
        public String toString() {
            return "Result{" +
                    "perf=" + (perfScore * 100.0) +
                    ", a11y=" + (a11yScore * 100.0) +
                    ", bp=" + (bpScore * 100.0) +
                    ", seo=" + (seoScore * 100.0) +
                    ", fcpMs=" + fcpMs +
                    ", siMs=" + siMs +
                    ", lcpMs=" + lcpMs +
                    ", tbtMs=" + tbtMs +
                    ", cls=" + cls +
                    '}';
        }
    }

    static class RunFiles {
        Path html;
        Path json;
        RunFiles(Path html, Path json) { this.html = html; this.json = json; }
    }

    // ==========================================================
    // Selenium Login -> build Lighthouse extra-headers JSON (Cookie header)
    // ==========================================================
    private static Path loginAndCreateExtraHeadersFile(Path profileDir, String username, String password) throws Exception {

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--user-data-dir=" + profileDir.toAbsolutePath());
        options.addArguments("--profile-directory=Default");

        WebDriverManager.chromedriver()
                .clearDriverCache()
                .driverVersion("142.0.7444.176")
                .setup();

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(LOGIN_URL);

            WebDriverWait wait = new WebDriverWait(driver, 30);
            wait.until(ExpectedConditions.visibilityOfElementLocated(EMAIL_INPUT)).sendKeys(username);
            driver.findElement(PASSWORD_INPUT).sendKeys(password);
            driver.findElement(LOGIN_BUTTON).click();

            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("route=account/account"),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("h2"))
            ));

            Set<org.openqa.selenium.Cookie> cookies = driver.manage().getCookies();
            String cookieHeader = cookies.stream()
                    .filter(c -> c.getName() != null && c.getValue() != null)
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining("; "));

            if (cookieHeader.trim().isEmpty()) {
                throw new RuntimeException("Login succeeded but no cookies found to authenticate Lighthouse.");
            }

            String json = "{\n" +
                    "  \"Cookie\": " + toJsonString(cookieHeader) + ",\n" +
                    "  \"Cache-Control\": \"no-cache\"\n" +
                    "}";

            Path headersFile = Files.createTempFile("lh-extra-headers-", ".json");
            Files.write(headersFile, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);

            return headersFile;

        } finally {
            driver.quit();
        }
    }

    private static String toJsonString(String s) {
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    // ==========================================================
    // Lighthouse Runs (3 times -> median)
    // ==========================================================
    private static MedianResult runLighthouse3Times(Path profileDir, PageConfig page, Path extraHeadersFile) throws Exception {

        List<Result> runs = new ArrayList<>();
        List<Path> htmlReports = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            RunFiles files = runOnce(profileDir, page, extraHeadersFile, i);
            Result r = parse(files.json);

            runs.add(r);
            htmlReports.add(files.html);

            System.out.println("Run " + i + " (" + page.name + "): " + r);
        }

        MedianResult m = new MedianResult();
        m.perfScore = median(runs.stream().map(r -> r.perfScore).collect(Collectors.toList()));
        m.a11yScore = median(runs.stream().map(r -> r.a11yScore).collect(Collectors.toList()));
        m.bpScore   = median(runs.stream().map(r -> r.bpScore).collect(Collectors.toList()));
        m.seoScore  = median(runs.stream().map(r -> r.seoScore).collect(Collectors.toList()));

        m.fcpMs = median(runs.stream().map(r -> r.fcpMs).collect(Collectors.toList()));
        m.siMs  = median(runs.stream().map(r -> r.siMs).collect(Collectors.toList()));
        m.lcpMs = median(runs.stream().map(r -> r.lcpMs).collect(Collectors.toList()));
        m.tbtMs = median(runs.stream().map(r -> r.tbtMs).collect(Collectors.toList()));
        m.cls   = median(runs.stream().map(r -> r.cls).collect(Collectors.toList()));

        System.out.println("HTML Reports generated (" + page.name + "):");
        htmlReports.forEach(p -> System.out.println(" - " + p.toAbsolutePath()));

        return m;
    }

    private static RunFiles runOnce(Path profileDir, PageConfig page, Path extraHeadersFile, int run) throws Exception {

        Path reportsDir = Paths.get("reports");
        Files.createDirectories(reportsDir);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path base = reportsDir.resolve(page.name + "-run" + run + "-" + ts);

        Path html = Paths.get(base.toString() + ".report.html");
        Path json = Paths.get(base.toString() + ".report.json");

        runLighthouseBoth(profileDir, page.url, base, extraHeadersFile);

        return new RunFiles(html, json);
    }

    // ==========================================================
    // Lighthouse command (fixed for '&' in URL + robust report check)
    // ==========================================================
    private static void runLighthouseBoth(Path profileDir, String url, Path base, Path extraHeadersFile) throws Exception {
        String profilePath = profileDir.toAbsolutePath().toString();

        String chromeFlags =
                "--headless=new --disable-gpu --no-sandbox --disable-dev-shm-usage " +
                        "--window-size=1920,1080 --no-first-run --no-default-browser-check " +
                        "--user-data-dir=\"" + profilePath + "\" --profile-directory=Default";

        String lighthouseCmd = System.getenv("APPDATA") + "\\npm\\lighthouse.cmd";

        // ✅ IMPORTANT: wrap URL in quotes so '&path=33' is not lost on Windows
        String quotedUrl = "\"" + url + "\"";

        List<String> cmd = new ArrayList<>(Arrays.asList(
                lighthouseCmd,
                quotedUrl,
                "--only-categories=performance,accessibility,best-practices,seo",
                "--output=html",
                "--output=json",
                "--output-path=" + base.toString(),
                "--throttling-method=provided",
                "--disable-storage-reset",
                "--disable-full-page-screenshot",
                "--chrome-flags=" + chromeFlags
        ));

        if (extraHeadersFile != null) {
            cmd.add("--extra-headers=" + extraHeadersFile.toAbsolutePath());
        }

        System.out.println("\nLighthouse CMD:");
        System.out.println(String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);

        // discard stdout + stderr completely
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process p = pb.start();
        int exit = p.waitFor();


        Path htmlReport = Paths.get(base.toString() + ".report.html");
        Path jsonReport = Paths.get(base.toString() + ".report.json");

        boolean ok = Files.exists(htmlReport) && Files.size(htmlReport) > 0
                && Files.exists(jsonReport) && Files.size(jsonReport) > 0;

        if (!ok) {
            throw new RuntimeException(
                    "Lighthouse did not generate reports. exit=" + exit +
                            ", htmlExists=" + Files.exists(htmlReport) +
                            ", jsonExists=" + Files.exists(jsonReport) +
                            ", base=" + base
            );
        }
    }

    // ==========================================================
    // JSON Parsing
    // ==========================================================
    private static Result parse(Path json) throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(json.toFile());

        Result r = new Result();

        r.perfScore = root.path("categories").path("performance").path("score").asDouble();
        r.a11yScore = root.path("categories").path("accessibility").path("score").asDouble();
        r.bpScore   = root.path("categories").path("best-practices").path("score").asDouble();
        r.seoScore  = root.path("categories").path("seo").path("score").asDouble();

        JsonNode a = root.path("audits");

        r.fcpMs = a.path("first-contentful-paint").path("numericValue").asDouble();
        r.siMs  = a.path("speed-index").path("numericValue").asDouble();
        r.lcpMs = a.path("largest-contentful-paint").path("numericValue").asDouble();
        r.tbtMs = a.path("total-blocking-time").path("numericValue").asDouble();
        r.cls   = a.path("cumulative-layout-shift").path("numericValue").asDouble();

        return r;
    }

    // ==========================================================
    // Assertions
    // ==========================================================
    private static void assertThresholds(MedianResult r) {
        double perfPct = r.perfScore * 100.0;
        if (perfPct < 90)
            throw new AssertionError("Performance score must be >= 90 (median): " + perfPct);
        if (r.lcpMs > 2500)
            throw new AssertionError("LCP too high (median): " + r.lcpMs + " ms");
        if (r.cls > 0.1)
            throw new AssertionError("CLS too high (median): " + r.cls);
        if (r.tbtMs > 200)
            throw new AssertionError("TBT too high (median): " + r.tbtMs + " ms");
    }


    // ==========================================================
    // Helpers
    // ==========================================================
    private static double median(List<Double> v) {
        Collections.sort(v);
        return v.get(v.size() / 2); // median of 3 = middle value
    }
}
