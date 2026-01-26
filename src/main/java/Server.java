package prereqsolver;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;
import java.util.Set;

public class Server {

    private static final String ORIGIN_URL = "https://wynnshu.github.io";

    // Define the JSON request structure
    private static class RequestData {
        public String targetCourse;
        public Set<String> takenCourses;
    }

    public static void main(String[] args) {
        // Initialize data once at startup (Expensive operation)
        // Ensure the TSV file is in the working directory or provide full path
        PrereqData data;
        try {
            data = new PrereqData("tokenized_prereqs_corrected.tsv"); 
        } catch (Exception e) {
            System.err.println("Failed to load data: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Cloud Run injects the PORT environment variable
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.allowHost(ORIGIN_URL);
                });
            });
        }).start(port);

        app.post("/", ctx -> handleRequest(ctx, data));
    }

    private static void handleRequest(Context ctx, PrereqData data) {
        try {
            // Parse JSON body
            RequestData request = ctx.bodyAsClass(RequestData.class);
            
            // Run your logic
            PlanFinder finder = new PlanFinder(data, request.takenCourses);
            List<Plan> plans = finder.findPlans(request.targetCourse);
            
            // Extract course sets from plans for cleaner output
            ctx.json(plans.stream().map(Plan::getCourseSet).toList());
        } catch (Exception e) {
            ctx.status(500).result("Error processing request: " + e.getMessage());
        }
    }
}