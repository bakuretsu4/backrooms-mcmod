package com.rickr.backrooms.escape;

import com.rickr.backrooms.world.BackroomsLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Levels 369 and 490 belong to the owner's friend, who maintains them himself. They get the same
 * arrival-to-exit check as every other level, but the findings only go into a report
 * (build/reports/friend-levels.txt) and never fail the build: the owner's build stays green while
 * the friend still gets a list of anything that looks stuck.
 *
 * <p>What the check can't see on these two: Level 369's forgetting (doorways that wall themselves
 * over while you're there) and Level 490's looping stairs, crumbling bridge, falls back to the
 * last checkpoint and the creature are all live behaviour, not generated blocks.
 */
class FriendLevelsReportTest {
    @Test
    void reportLevels369And490() throws IOException {
        StringBuilder report = new StringBuilder("Escapability report for Level 369 and Level 490 (report only, never fails the build)\n")
                .append("Generated ").append(LocalDateTime.now()).append(" by FriendLevelsReportTest\n")
                .append("Each line: arrival as Travel places it coming from the previous level's exit, then a search on foot\n")
                .append("through the generated blocks for the exit trigger (369: walking into the veil of an arch of vines;\n")
                .append("490: leaping off the upper collar, a fall that starts at y>=506 and passes y505).\n")
                .append("Movement model: walk, half steps and stairs, ladders, water; no jumping on land except on 490\n")
                .append("(where a jump clears up to a block or a gap of two columns). It only sees generated blocks: 369's\n")
                .append("forgetting doorways, 490's loops, crumbling bridge, falls back to the last radio and the creature\n")
                .append("are live behaviour it can't see. A CHECK is a lead to look at, not proof: Level 490's docs mention\n")
                .append("a scripted climb of both flights, which is the better authority for the spire.\n\n");
        int problems = 0;
        BackroomsLevel[][] steps = {{BackroomsLevel.LEVEL_369, BackroomsLevel.LEVEL_94}, {BackroomsLevel.LEVEL_490, BackroomsLevel.LEVEL_369}};
        for (BackroomsLevel[] step : steps) {
            report.append("== ").append(step[0].getSerializedName()).append(" (arriving from ").append(step[1].getSerializedName())
                    .append("; exit leads to ").append(step[0].next() == null ? "reality" : step[0].next().getSerializedName()).append(")\n");
            for (long worldSeed : Escape.SEEDS) {
                String line;
                boolean ok;
                try {
                    Escape.Outcome outcome = LevelEscapabilityTest.run(step[0], step[1], worldSeed);
                    ok = outcome.ok();
                    line = outcome.describe();
                } catch (RuntimeException | OutOfMemoryError e) {
                    ok = false;
                    line = "the check itself crashed: " + e;
                }
                if (!ok) {
                    problems++;
                }
                report.append(ok ? "  ok    " : "  CHECK ").append("seed ").append(worldSeed).append(": ").append(line).append('\n');
            }
            report.append('\n');
        }
        report.append(problems == 0 ? "No problems found.\n" : problems + " case(s) worth a look (marked CHECK).\n");
        Path out = projectDir().resolve("build").resolve("reports").resolve("friend-levels.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
        System.out.println(report);
        System.out.println("Friend levels report written to " + out.toAbsolutePath());
    }

    /** The project folder: tests run inside build/minecraft-junit, so look up for build.gradle. */
    private static Path projectDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("build.gradle"))) {
                return p;
            }
        }
        return dir;
    }
}
