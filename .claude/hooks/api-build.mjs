// Stop hook: when this session edited apps/api (marked by mark-api-edit.mjs), runs the Spotless check and compiles
// main and test code with -Werror in that checkout. The mark is cleared by every build, so a failure is reported
// once and later turns stay quiet until the API is edited again. CI's api job runs the same checks and the tests.
// Any failure of the hook itself also blocks, with its message, so a broken hook is never silent.
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { readInput } from "./repo-path.mjs";

try {
  const input = await readInput();
  if (input.stop_hook_active) process.exit(0);
  const mark = path.join(
    tmpdir(),
    "counthouse-api-edits",
    path.basename(String(input.session_id)),
  );
  if (!existsSync(mark)) process.exit(0);
  const root = readFileSync(mark, "utf8");
  rmSync(mark);

  // Offline: no network wait. The Spotless check is bound to validate, so it runs before compiling.
  const run = spawnSync("./mvnw", ["-B", "-o", "test-compile"], {
    cwd: path.join(root, "apps/api"),
    encoding: "utf8",
  });
  if (run.error) throw run.error;
  if (run.status === 0) process.exit(0);

  // Keep compiler warnings (-Werror hides them in the error line) and errors, up to Maven's generic help trailer.
  const report = [];
  for (const line of run.stdout.split("\n")) {
    if (line.startsWith("[ERROR] -> [Help")) break;
    if (
      line.startsWith("[ERROR]") ||
      (line.startsWith("[WARNING]") && line.includes(".java:["))
    )
      report.push(line);
  }
  // Nothing from Maven means the wrapper failed before the build (no Java, no wrapper download): show its stderr.
  if (report.length === 0)
    report.push(...run.stderr.trimEnd().split("\n").slice(-20));
  console.error(
    [
      "apps/api failed `./mvnw -B -o test-compile`. Fix formatting with `./mvnw spotless:apply`; if offline resolution failed, run `./mvnw -B verify` once.",
      ...report.slice(0, 40),
    ].join("\n"),
  );
  process.exit(2);
} catch (error) {
  console.error(`The apps/api Stop hook failed: ${error.message}`);
  process.exit(2);
}
