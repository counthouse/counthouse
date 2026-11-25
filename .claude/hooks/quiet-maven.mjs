// PreToolUse hook on Bash: blocks a raw Maven run of a goal that runs tests or builds (verify, test, package,
// install, integration-test) and points to `npm run api:mvn`, which prints only the failures and keeps the full log in
// a file. A full Maven log costs thousands of tokens; the summary costs a few lines. Quick goals such as
// spotless:apply or test-compile, and runs with -q, are allowed.
import { readInput } from "./repo-path.mjs";

const HEAVY = new Set([
  "verify",
  "test",
  "package",
  "install",
  "integration-test",
]);

const input = await readInput();
const command = String(input.tool_input?.command ?? "");
// Each Maven invocation in the command: the wrapper or mvn, then its arguments up to the next shell separator.
for (const match of command.matchAll(
  /(?:^|[\s;&|(`])(?:\.\/|\S*\/)?(?:mvnw|mvn)(?=\s|$)([^;&|`)\n]*)/g,
)) {
  const args = match[1].trim().split(/\s+/).filter(Boolean);
  if (args.includes("-q") || args.includes("--quiet")) continue;
  const goals = args.filter((arg) => !arg.startsWith("-"));
  if (!goals.some((goal) => HEAVY.has(goal))) continue;
  console.error(
    "Run Maven through `npm run api:mvn -- <goals and flags>` (or `npm run api:verify` for the full build) from the repository root. It prints only the failures and the path of the full log.",
  );
  process.exit(2);
}
