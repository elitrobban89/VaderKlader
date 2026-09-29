package com.vaderklader.controller;

import com.vaderklader.service.ClaudeService;
import org.springframework.boot.SpringBootVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vad tjänsten faktiskt kör på — till uppstartsskärmens plattforms- och Groq-rad.
 *
 * <p>Java-versionen läses ur den körande JVM:en och modellen ur {@link ClaudeService#models()},
 * inte ur avskrivna strängar: uppgraderas projektet till Java 28 eller byts modellen visar
 * splashen det utan att någon rör den. VäderKläder har ingen databas.
 *
 * @author Robert Andersson Kopler
 */
@RestController
public class SystemController {

    @GetMapping("/api/system")
    public Map<String, String> system() {
        Map<String, String> ut = new LinkedHashMap<>();
        ut.put("java", System.getProperty("java.version", ""));
        ut.put("springBoot", String.valueOf(SpringBootVersion.getVersion()));
        // Render sätter de här i varje deploy — splashens autodeploy-bricka visar vilken commit som kör.
        String commit = System.getenv("RENDER_GIT_COMMIT");
        if (commit != null && !commit.isBlank()) ut.put("deployCommit", commit.substring(0, Math.min(7, commit.length())));
        String branch = System.getenv("RENDER_GIT_BRANCH");
        if (branch != null && !branch.isBlank()) ut.put("deployBranch", branch);
        ut.put("model", ClaudeService.models().get(0));
        return ut;
    }
}
