// Runs both hooks as Claude Code does: JSON on stdin, CLAUDE_PROJECT_DIR set, exit code and stderr checked.
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import {
  chmodSync,
  copyFileSync,
  symlinkSync,
  existsSync,
  readFileSync,
  mkdtempSync,
  writeFileSync,
  mkdirSync,
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";

const HOOKS = import.meta.dirname;
const MIGRATIONS = "apps/api/src/main/resources/db/migration";

function repo() {
  const base = mkdtempSync(path.join(tmpdir(), "hooks-"));
  const git = (cwd, ...args) =>
    execFileSync("git", args, { cwd, stdio: "pipe" });
  const origin = path.join(base, "origin");
  mkdirSync(path.join(origin, MIGRATIONS), { recursive: true });
  writeFileSync(
    path.join(origin, MIGRATIONS, "V1__create_tenants.sql"),
    "create table tenants ();\n",
  );
  writeFileSync(path.join(origin, "README.md"), "# Test\n");
  git(origin, "init", "-q", "-b", "main");
  git(origin, "add", ".");
  git(
    origin,
    "-c",
    "user.name=t",
    "-c",
    "user.email=t@t",
    "-c",
    "commit.gpgsign=false",
    "commit",
    "-qm",
    "init",
  );
  const root = path.join(base, "clone");
  git(base, "clone", "-q", origin, root);
  // The hooks find the repository from their own location, so each test repository gets its own copy.
  mkdirSync(path.join(root, ".claude/hooks"), { recursive: true });
  for (const name of [
    "repo-path.mjs",
    "frozen-migrations.mjs",
    "mark-edit.mjs",
    "site-check.mjs",
    "quiet-maven.mjs",
    "api-build.mjs",
  ]) {
    copyFileSync(
      path.join(HOOKS, name),
      path.join(root, ".claude/hooks", name),
    );
  }
  return {
    root,
    tmp: base,
    write(file, text) {
      mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
      writeFileSync(path.join(root, file), text);
    },
    commit() {
      git(root, "add", ".");
      git(
        root,
        "-c",
        "user.name=t",
        "-c",
        "user.email=t@t",
        "-c",
        "commit.gpgsign=false",
        "commit",
        "-qm",
        "c",
      );
    },
    // Stands in for the Maven wrapper: records the call, prints the given output and exits with the given status.
    mvnw(stdout, status) {
      this.write(
        "apps/api/mvnw",
        `#!/bin/sh\ntouch ran\ncat <<'OUT'\n${stdout}\nOUT\nexit ${status}\n`,
      );
      chmodSync(path.join(root, "apps/api/mvnw"), 0o755);
    },
    ran: () => existsSync(path.join(root, "apps/api/ran")),
  };
}

// Claude Code may start above the repository, so the hook gets a cwd outside it.
function hook(name, root, input) {
  return spawnSync("node", [path.join(root, ".claude/hooks", name)], {
    input: JSON.stringify({
      cwd: path.dirname(root),
      session_id: "s1",
      ...input,
    }),
    env: { ...process.env, TMPDIR: path.dirname(root) },
    encoding: "utf8",
  });
}

const edit = (root, file, cwd = path.dirname(root)) =>
  hook("frozen-migrations.mjs", root, { cwd, tool_input: { file_path: file } });

test("blocks an edit to a migration on origin/main, in any path form", () => {
  const { root } = repo();
  for (const [file, cwd] of [
    [path.join(root, MIGRATIONS, "V1__create_tenants.sql"), root],
    [
      path.join(root, MIGRATIONS, "..", "migration", "V1__create_tenants.sql"),
      root,
    ],
    [
      "migration/V1__create_tenants.sql",
      path.join(root, "apps/api/src/main/resources/db"),
    ],
  ]) {
    const result = edit(root, file, cwd);
    assert.equal(result.status, 2, file);
    assert.match(result.stderr, /is on main and frozen/);
  }
});

test("allows a new migration and other files on origin/main", () => {
  const { root } = repo();
  assert.equal(
    edit(root, path.join(root, MIGRATIONS, "V2__add_users.sql")).status,
    0,
  );
  assert.equal(edit(root, path.join(root, "README.md")).status, 0);
});

test("allows a migration committed on the branch but not on origin/main", () => {
  const r = repo();
  r.write(`${MIGRATIONS}/V2__add_users.sql`, "create table users ();\n");
  r.commit();
  assert.equal(
    edit(r.root, path.join(r.root, MIGRATIONS, "V2__add_users.sql")).status,
    0,
  );
});

test("blocks a frozen migration reached through a symlink", () => {
  const r = repo();
  const link = path.join(r.tmp, "link");
  symlinkSync(r.root, link);
  assert.equal(
    edit(r.root, path.join(link, MIGRATIONS, "V1__create_tenants.sql")).status,
    2,
  );
});

const markEdit = (r, file) =>
  hook("mark-edit.mjs", r.root, {
    tool_input: { file_path: path.join(r.root, file) },
  });
const marked = (r) =>
  existsSync(path.join(r.tmp, "counthouse-api-edits", "s1"));
const stop = (r, input = {}) => hook("api-build.mjs", r.root, input);

test("Stop: does not build for a session that did not edit apps/api, even with a broken change on disk", () => {
  const r = repo();
  r.mvnw("[ERROR] broken", 1);
  markEdit(r, "README.md");
  assert.equal(marked(r), false);
  assert.equal(stop(r).status, 0);
  assert.equal(r.ran(), false);
});

test("Stop: skips when already continuing", () => {
  const r = repo();
  r.mvnw("[ERROR] broken", 1);
  markEdit(r, "apps/api/A.java");
  assert.equal(stop(r, { stop_hook_active: true }).status, 0);
  assert.equal(r.ran(), false);
});

test("Stop: reports warnings and errors without Maven's help trailer, once", () => {
  const r = repo();
  r.mvnw(
    [
      "[INFO] Compiling 3 source files",
      "[WARNING] /x/A.java:[19,5] found raw type: java.util.List",
      "[WARNING] some plugin notice",
      "[ERROR] /x/A.java: warnings found and -Werror specified",
      "[ERROR] -> [Help 1]",
      "[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.",
    ].join("\n"),
    1,
  );
  markEdit(r, "apps/api/A.java");
  const result = stop(r);
  assert.equal(result.status, 2);
  assert.match(result.stderr, /found raw type/);
  assert.match(result.stderr, /-Werror specified/);
  assert.doesNotMatch(
    result.stderr,
    /plugin notice|Help 1|stack trace|Compiling/,
  );
  // The next turn does not edit the API, so it stops without building again.
  assert.equal(marked(r), false);
  assert.equal(stop(r).status, 0);
});

test("Stop: a passing build clears the mark", () => {
  const r = repo();
  r.mvnw("", 0);
  markEdit(r, "apps/api/A.java");
  assert.equal(stop(r).status, 0);
  assert.equal(r.ran(), true);
  assert.equal(marked(r), false);
});

test("Stop: a hook failure blocks with its message", () => {
  const r = repo();
  markEdit(r, "apps/api/A.java");
  const result = stop(r);
  assert.equal(result.status, 2);
  assert.match(result.stderr, /Stop hook failed/);
});

test("works in a worktree: blocks the frozen migration and builds the worktree", () => {
  const r = repo();
  const wt = path.join(r.root, ".claude/worktrees/w");
  execFileSync("git", ["worktree", "add", "-q", wt], {
    cwd: r.root,
    stdio: "pipe",
  });
  assert.equal(
    edit(r.root, path.join(wt, MIGRATIONS, "V1__create_tenants.sql")).status,
    2,
  );
  r.mvnw("", 0);
  writeFileSync(
    path.join(wt, "apps/api/mvnw"),
    "#!/bin/sh\ntouch ran-in-worktree\n",
  );
  chmodSync(path.join(wt, "apps/api/mvnw"), 0o755);
  hook("mark-edit.mjs", r.root, {
    tool_input: { file_path: path.join(wt, "apps/api/A.java") },
  });
  assert.equal(stop(r).status, 0);
  assert.equal(existsSync(path.join(wt, "apps/api/ran-in-worktree")), true);
  assert.equal(r.ran(), false);
});

test("Stop: shows the wrapper's own error when Maven never started", () => {
  const r = repo();
  r.write(
    "apps/api/mvnw",
    "#!/bin/sh\necho 'JAVA_HOME is not set' >&2\nexit 1\n",
  );
  chmodSync(path.join(r.root, "apps/api/mvnw"), 0o755);
  markEdit(r, "apps/api/A.java");
  const result = stop(r);
  assert.equal(result.status, 2);
  assert.match(result.stderr, /JAVA_HOME is not set/);
});

// Formatting is applied before the check, so it never costs a turn.
test("Stop: applies Spotless before compiling", () => {
  const r = repo();
  r.write("apps/api/mvnw", '#!/bin/sh\necho "$*" >> calls\nexit 0\n');
  chmodSync(path.join(r.root, "apps/api/mvnw"), 0o755);
  markEdit(r, "apps/api/A.java");
  assert.equal(stop(r).status, 0);
  const calls = readFileSync(path.join(r.root, "apps/api/calls"), "utf8");
  assert.match(calls, /spotless:apply[\s\S]*test-compile/);
});

const siteStop = (r, input = {}) => hook("site-check.mjs", r.root, input);
// Stands in for npm: records the script it ran, prints the given output and exits with the given status.
function fakeNpm(r, stdout, status) {
  const bin = path.join(r.tmp, "bin");
  mkdirSync(bin, { recursive: true });
  writeFileSync(
    path.join(bin, "npm"),
    `#!/bin/sh\necho "$*" >> "${r.tmp}/npm-calls"\ncat <<'OUT'\n${stdout}\nOUT\nexit ${status}\n`,
  );
  chmodSync(path.join(bin, "npm"), 0o755);
  return { ...process.env, PATH: `${bin}:${process.env.PATH}` };
}
const siteHook = (r, env, input = {}) =>
  spawnSync("node", [path.join(r.root, ".claude/hooks/site-check.mjs")], {
    input: JSON.stringify({
      cwd: path.dirname(r.root),
      session_id: "s1",
      ...input,
    }),
    env: { ...env, TMPDIR: path.dirname(r.root) },
    encoding: "utf8",
  });

test("site Stop: checks only sessions that edited apps/site", () => {
  const r = repo();
  const env = fakeNpm(r, "src/a.tsx(1,1): error TS2322: nope", 1);
  markEdit(r, "apps/api/A.java");
  assert.equal(siteHook(r, env).status, 0);
  assert.equal(existsSync(path.join(r.tmp, "npm-calls")), false);
});

test("site Stop: reports type errors once, then stays quiet", () => {
  const r = repo();
  const env = fakeNpm(
    r,
    [
      "> next typegen",
      "src/a.tsx(1,1): error TS2322: Type 'x' is not assignable",
      "npm notice",
    ].join("\n"),
    1,
  );
  markEdit(r, "apps/site/src/a.tsx");
  const result = siteHook(r, env);
  assert.equal(result.status, 2);
  assert.match(result.stderr, /error TS2322/);
  assert.doesNotMatch(result.stderr, /npm notice|next typegen/);
  assert.equal(siteHook(r, env).status, 0);
});

test("site Stop: a passing check clears the mark", () => {
  const r = repo();
  const env = fakeNpm(r, "", 0);
  markEdit(r, "apps/site/src/a.tsx");
  assert.equal(siteHook(r, env).status, 0);
  assert.match(
    readFileSync(path.join(r.tmp, "npm-calls"), "utf8"),
    /typecheck[\s\S]*lint/,
  );
});

const bash = (r, command) =>
  hook("quiet-maven.mjs", r.root, { tool_input: { command } });

test("blocks raw Maven runs of heavy goals in any form", () => {
  const r = repo();
  for (const command of [
    "./mvnw verify",
    "cd apps/api && ./mvnw -B verify",
    "./mvnw -Dtest=FooIT test",
    "mvn clean install",
    "apps/api/mvnw -B package",
    "(cd apps/api; ./mvnw verify)",
  ]) {
    const result = bash(r, command);
    assert.equal(result.status, 2, command);
    assert.match(result.stderr, /npm run api:mvn/);
  }
});

test("allows quick goals, quiet runs and the npm scripts", () => {
  const r = repo();
  for (const command of [
    "./mvnw spotless:apply",
    "./mvnw -B -o test-compile",
    "./mvnw -q verify",
    "npm run api:verify",
    "npm run api:mvn -- test -Dtest=FooIT",
    "grep -r mvnw .github",
  ]) {
    assert.equal(bash(r, command).status, 0, command);
  }
});

test("marks a new file in a folder that does not exist yet", () => {
  const r = repo();
  markEdit(r, "apps/api/src/main/java/new/pkg/A.java");
  assert.equal(marked(r), true);
});
