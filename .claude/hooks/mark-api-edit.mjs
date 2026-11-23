// PostToolUse hook on Edit and Write: records that this session changed apps/api, and in which checkout, so the
// Stop hook builds only for sessions that touched the API. It keeps the last checkout edited: a session that edits
// the API in two checkouts (main and a worktree) gets only the last one built.
import { mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { locate, readInput } from "./repo-path.mjs";

const input = await readInput();
const target = locate(input);
if (!target || !input.session_id || !target.rel.startsWith("apps/api/"))
  process.exit(0);
const dir = path.join(tmpdir(), "counthouse-api-edits");
mkdirSync(dir, { recursive: true });
writeFileSync(path.join(dir, path.basename(input.session_id)), target.root);
