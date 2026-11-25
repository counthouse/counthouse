// Stop hook: when this session edited apps/site (marked by mark-edit.mjs), runs the site's type check and lint in that
// checkout. Like api-build.mjs, the mark is cleared by every run, so a failure is reported once, and any failure of the
// hook itself blocks with its message. CI's site job runs the same checks plus the build, axe and Lighthouse.
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { readInput } from "./repo-path.mjs";

const CHECKS = ["typecheck", "lint"];

try {
  const input = await readInput();
  if (input.stop_hook_active) process.exit(0);
  const mark = path.join(
    tmpdir(),
    "counthouse-site-edits",
    path.basename(String(input.session_id)),
  );
  if (!existsSync(mark)) process.exit(0);
  const root = readFileSync(mark, "utf8");
  rmSync(mark);

  const report = [];
  for (const script of CHECKS) {
    const run = spawnSync(
      "npm",
      ["run", "--silent", "--workspace", "apps/site", script],
      { cwd: root, encoding: "utf8" },
    );
    if (run.error) throw run.error;
    if (run.status === 0) continue;
    // Keep TypeScript errors, and ESLint's file headers and problem lines; drop npm's own noise.
    const lines = `${run.stdout}\n${run.stderr}`.split("\n");
    const kept = lines.filter(
      (line) =>
        /error TS\d+/.test(line) ||
        /^\s+\d+:\d+\s+(error|warning)\s/.test(line) ||
        /^\/\S+\.(tsx?|mts|jsx?|mjs)$/.test(line.trim()),
    );
    report.push(
      `apps/site failed \`npm run -w apps/site ${script}\`:`,
      ...(kept.length > 0
        ? kept
        : lines.filter((line) => line.trim()).slice(-15)),
    );
  }
  if (report.length === 0) process.exit(0);
  console.error(report.slice(0, 40).join("\n"));
  process.exit(2);
} catch (error) {
  console.error(`The apps/site Stop hook failed: ${error.message}`);
  process.exit(2);
}
