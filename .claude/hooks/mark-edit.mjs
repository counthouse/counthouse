// PostToolUse hook on Edit and Write: records that this session changed apps/api or apps/site, and in
// which checkout, so each Stop hook checks only the apps the session touched. It keeps the last checkout edited per
// app: a session that edits the same app in two checkouts (main and a worktree) gets only the last one checked.
import { mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { locate, readInput } from "./repo-path.mjs";

const APPS = {
  "apps/api/": "counthouse-api-edits",
  "apps/site/": "counthouse-site-edits",
};

const input = await readInput();
const target = locate(input);
if (!target || !input.session_id) process.exit(0);
for (const [prefix, folder] of Object.entries(APPS)) {
  if (!target.rel.startsWith(prefix)) continue;
  const dir = path.join(tmpdir(), folder);
  mkdirSync(dir, { recursive: true });
  writeFileSync(path.join(dir, path.basename(input.session_id)), target.root);
}
