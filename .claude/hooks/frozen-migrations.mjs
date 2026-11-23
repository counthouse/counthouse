// PreToolUse hook on Edit and Write: blocks an edit to a migration that exists on origin/main.
// CI's migrations job is the guard; this gives the agent the same answer before the edit.
import { spawnSync } from "node:child_process";
import { locate, readInput } from "./repo-path.mjs";

const MIGRATIONS = "apps/api/src/main/resources/db/migration/";
const target = locate(await readInput());
if (!target || !target.rel.startsWith(MIGRATIONS)) process.exit(0);
const onMain = spawnSync(
  "git",
  ["cat-file", "-e", `origin/main:${target.rel}`],
  { cwd: target.root },
);
if (onMain.status !== 0) process.exit(0);
console.error(
  `${target.rel} is on main and frozen. Change the schema with a new migration whose version is higher than every existing one (CONTRIBUTING.md, Database).`,
);
process.exit(2);
