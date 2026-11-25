// Runs the API's Maven wrapper with the given goals and flags (default: verify) and prints a short summary instead of
// the full log: the result, the test totals, failing tests, compiler warnings and errors. The full log is saved to a
// file whose path is printed, so it can be searched when the summary is not enough.
//   npm run api:verify
//   npm run api:mvn -- test -Dtest=TenantIsolationIT
import { spawnSync } from "node:child_process";
import { writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const args = process.argv.slice(2);
const goals = args.length > 0 ? args : ["verify"];

const run = spawnSync("./mvnw", ["-B", ...goals], {
  cwd: path.join(root, "apps/api"),
  encoding: "utf8",
  maxBuffer: 256 * 1024 * 1024,
});
if (run.error) {
  console.error(`Could not start the Maven wrapper: ${run.error.message}`);
  process.exit(1);
}

const log = path.join(tmpdir(), `counthouse-mvn-${Date.now()}.log`);
writeFileSync(log, `${run.stdout}\n${run.stderr}`);
const lines = run.stdout.split("\n");

const summary = [];
if (run.status === 0) {
  summary.push(`apps/api: mvnw ${goals.join(" ")} passed.`);
  // The last "Tests run:" line is the module total.
  const totals = lines.filter((line) => /^\[\w+\] Tests run: \d+/.test(line));
  if (totals.length > 0) summary.push(totals.at(-1));
} else {
  summary.push(`apps/api: mvnw ${goals.join(" ")} FAILED.`);
  for (const line of lines) {
    if (line.startsWith("[ERROR] -> [Help")) break;
    if (
      line.startsWith("[ERROR]") ||
      (line.startsWith("[WARNING]") && line.includes(".java:["))
    )
      summary.push(line);
  }
  // Nothing from Maven means the wrapper failed before the build: show its stderr.
  if (summary.length === 1)
    summary.push(...run.stderr.trimEnd().split("\n").slice(-20));
}
console.log([...summary.slice(0, 60), `Full log: ${log}`].join("\n"));
process.exit(run.status ?? 1);
