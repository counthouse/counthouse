// Shared by the hooks: reads the hook input and finds the repository that contains the edited file. Using the file's
// own repository, not the folder Claude Code started in, keeps the hooks right in a parent folder and in a worktree.
import { spawnSync } from "node:child_process";
import { existsSync, realpathSync } from "node:fs";
import path from "node:path";

export async function readInput() {
  let raw = "";
  for await (const chunk of process.stdin) raw += chunk;
  return JSON.parse(raw);
}

// Returns { root, rel } for the edited file, or null when it is not inside a git repository.
export function locate(input) {
  const file = input.tool_input?.file_path;
  if (!file) return null;
  const absolute = real(path.resolve(input.cwd, file));
  // A Write may create new folders, so git runs in the nearest folder that already exists.
  const top = spawnSync("git", ["rev-parse", "--show-toplevel"], {
    cwd: nearestExisting(path.dirname(absolute)),
    encoding: "utf8",
  });
  if (top.status !== 0) return null;
  const root = top.stdout.trim();
  return { root, rel: path.relative(root, absolute).split(path.sep).join("/") };
}

// Git reports the repository root with symlinks resolved, so the edited path is resolved the same way. The file may
// not exist yet (Write), so the nearest existing folder is resolved and the rest of the path kept.
function real(absolute) {
  const existing = nearestExisting(absolute);
  return path.join(realpathSync(existing), path.relative(existing, absolute));
}

function nearestExisting(target) {
  let existing = target;
  while (!existsSync(existing)) existing = path.dirname(existing);
  return existing;
}
