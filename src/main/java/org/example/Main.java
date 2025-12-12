package org.example;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Minimal Git-like VCS
 *
 * Usage:
 *   mvn exec:java -Dexec.args="init"
 *   mvn exec:java -Dexec.args="add README.md"
 *   mvn exec:java -Dexec.args="commit -m \"initial commit\""
 *   mvn exec:java -Dexec.args="status"
 *   mvn exec:java -Dexec.args="log"
 *   mvn exec:java -Dexec.args="branch feature"
 *   mvn exec:java -Dexec.args="switch feature"
 *   mvn exec:java -Dexec.args="checkout <commit-hash>"
 */
public class Main {

    private static final String VCS_DIR = ".myvcs";
    private static final String OBJECTS_DIR = VCS_DIR + File.separator + "objects";
    private static final String REFS_DIR = VCS_DIR + File.separator + "refs";
    private static final String HEADS_DIR = REFS_DIR + File.separator + "heads";
    private static final String HEAD_FILE = VCS_DIR + File.separator + "HEAD";
    private static final String INDEX_FILE = VCS_DIR + File.separator + "index";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return;
        }

        String cmd = args[0];
        switch (cmd) {
            case "init":
                initRepository();
                break;
            case "add":
                if (args.length < 2) {
                    System.err.println("Usage: add <file>");
                } else {
                    addFile(args[1]);
                }
                break;
            case "commit":
                commitCommand(args);
                break;
            case "status":
                status();
                break;
            case "log":
                log();
                break;
            case "checkout":
                if (args.length < 2) {
                    System.err.println("Usage: checkout <commit-hash>");
                } else {
                    // user checkout should detach HEAD to that commit
                    checkout(args[1], true);
                }
                break;
            case "branch":
                if (args.length < 2) {
                    System.err.println("Usage: branch <name>");
                } else {
                    branch(args[1]);
                }
                break;
            case "branches":
                listBranches();
                break;
            case "switch":
                if (args.length < 2) {
                    System.err.println("Usage: switch <branch>");
                } else {
                    switchBranch(args[1]);
                }
                break;
            case "show":
                if (args.length < 2) {
                    System.err.println("Usage: show <hash|HEAD>");
                } else {
                    show(args[1]);
                }
                break;
            default:
                System.err.println("Unknown command: " + cmd);
                usage();
        }
    }

    private static void usage() {
        System.out.println("MiniVCS - commands:");
        System.out.println("  init");
        System.out.println("  add <file>");
        System.out.println("  commit -m \"message\"");
        System.out.println("  status");
        System.out.println("  log");
        System.out.println("  checkout <commit-hash>");
        System.out.println("  branch <name>");
        System.out.println("  branches");
        System.out.println("  switch <branch>");
        System.out.println("  show <hash|HEAD>");
    }

    // ---------- Repo init ----------
    private static void initRepository() throws IOException {
        File root = new File(VCS_DIR);
        if (root.exists()) {
            System.out.println("Repository already exists at " + root.getAbsolutePath());
            return;
        }
        new File(OBJECTS_DIR).mkdirs();
        new File(HEADS_DIR).mkdirs();

        // create master (main) branch with no commits yet
        // Set HEAD to refs/heads/main
        File head = new File(HEAD_FILE);
        FileUtils.writeStringToFile(head, "refs/heads/main", StandardCharsets.UTF_8);

        // create an empty main ref
        File mainRef = new File(HEADS_DIR + File.separator + "main");
        mainRef.createNewFile(); // empty file = no commit yet

        // empty index
        new File(INDEX_FILE).createNewFile();

        System.out.println("Initialized empty MiniVCS repo in " + root.getAbsolutePath());
    }

    // ---------- Index and add ----------
    private static void addFile(String path) throws IOException {
        ensureRepoExists();

        File f = new File(path);
        if (!f.exists()) {
            System.err.println("File not found: " + path);
            return;
        }
        // read content
        String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
        // create blob object
        String header = "blob " + content.length() + "\0";
        String storeContent = header + content;
        String hash = DigestUtils.sha1Hex(storeContent);

        File objectFile = new File(OBJECTS_DIR + File.separator + hash);
        if (!objectFile.exists()) {
            FileUtils.writeStringToFile(objectFile, storeContent, StandardCharsets.UTF_8);
            System.out.println("Stored blob: " + hash);
        } else {
            System.out.println("Blob exists: " + hash);
        }

        // add to index: store lines of "filename\thash"
        List<String> index = new ArrayList<>();
        File idx = new File(INDEX_FILE);
        if (idx.exists()) {
            index.addAll(FileUtils.readLines(idx, StandardCharsets.UTF_8));
            // remove any existing entry for same filename
            String prefix = path + "\t";
            index = index.stream().filter(line -> !line.startsWith(prefix)).collect(Collectors.toList());
        }
        index.add(path + "\t" + hash);
        FileUtils.writeLines(idx, index);
        System.out.println("Added to index: " + path);
    }

    // ---------- Commit ----------
    private static void commitCommand(String[] args) throws IOException {
        ensureRepoExists();
        String message = null;
        for (int i = 1; i < args.length; i++) {
            if ("-m".equals(args[i]) && i + 1 < args.length) {
                message = args[i + 1];
                i++;
            }
        }
        if (message == null) {
            System.err.println("Usage: commit -m \"message\"");
            return;
        }

        // read index
        File idx = new File(INDEX_FILE);
        if (!idx.exists()) {
            System.err.println("Nothing to commit (index missing)");
            return;
        }
        List<String> entries = FileUtils.readLines(idx, StandardCharsets.UTF_8)
                .stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
        if (entries.isEmpty()) {
            System.out.println("Nothing staged to commit.");
            return;
        }

        // Build a "tree" representation as lines: filename\tblobhash
        StringBuilder treeBuilder = new StringBuilder();
        entries.forEach(line -> treeBuilder.append(line).append("\n"));
        String treeContent = treeBuilder.toString();

        // commit metadata
        String parent = getCurrentCommitHash(); // may be empty
        String author = System.getProperty("user.name", "unknown");
        String timestamp = Instant.now().toString();

        StringBuilder commitBuilder = new StringBuilder();
        commitBuilder.append("tree ").append(DigestUtils.sha1Hex("tree " + treeContent.length() + "\0" + treeContent)).append("\n");
        if (!parent.isBlank()) commitBuilder.append("parent ").append(parent).append("\n");
        commitBuilder.append("author ").append(author).append(" ").append(timestamp).append("\n");
        commitBuilder.append("\n");
        commitBuilder.append(message).append("\n");

        String commitBody = commitBuilder.toString();
        String header = "commit " + commitBody.length() + "\0";
        String store = header + commitBody;
        String commitHash = DigestUtils.sha1Hex(store);

        File commitFile = new File(OBJECTS_DIR + File.separator + commitHash);
        if (!commitFile.exists()) {
            FileUtils.writeStringToFile(commitFile, store, StandardCharsets.UTF_8);
            System.out.println("Created commit: " + commitHash);
        } else {
            System.out.println("Commit already exists: " + commitHash);
        }

        // Save tree object separately for convenience
        String treeHeader = "tree " + treeContent.length() + "\0";
        String treeStore = treeHeader + treeContent;
        String treeHash = DigestUtils.sha1Hex(treeStore);
        File treeFile = new File(OBJECTS_DIR + File.separator + treeHash);
        if (!treeFile.exists()) {
            FileUtils.writeStringToFile(treeFile, treeStore, StandardCharsets.UTF_8);
        }

        // Update current branch ref
        String headRef = readHeadRef();
        if (headRef.startsWith("refs/heads/")) {
            String branch = headRef.substring("refs/heads/".length());
            File branchRef = new File(HEADS_DIR + File.separator + branch);
            FileUtils.writeStringToFile(branchRef, commitHash, StandardCharsets.UTF_8);
            System.out.println("Updated branch " + branch + " -> " + commitHash);
        } else {
            // if HEAD points directly to a hash (detached), write nothing
            System.out.println("HEAD is detached; not updating a branch ref.");
        }

        // clear index
        FileUtils.writeStringToFile(idx, "", StandardCharsets.UTF_8);
    }

    // ---------- Status ----------
    private static void status() throws IOException {
        ensureRepoExists();
        Map<String, String> indexEntries = readIndexMap();
        if (indexEntries.isEmpty()) {
            System.out.println("No files staged.");
        } else {
            System.out.println("Staged files:");
            indexEntries.forEach((k, v) -> System.out.println("  " + k + " (" + v + ")"));
        }

        // list unstaged modified files (simple check by comparing blob hash of current working file)
        List<String> modified = new ArrayList<>();
        for (String filename : getWorkingFiles()) {
            File f = new File(filename);
            if (!f.isFile()) continue;
            String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
            String storeContent = "blob " + content.length() + "\0" + content;
            String hash = DigestUtils.sha1Hex(storeContent);
            String stagedHash = indexEntries.get(filename);
            if (stagedHash == null && !getCommitTreeMap(getCurrentCommitHash()).containsKey(filename)) {
                // untracked
            } else if (stagedHash != null && !stagedHash.equals(hash)) {
                modified.add(filename);
            } else if (stagedHash == null) {
                // compare to HEAD commit
                String headHash = getCommitTreeMap(getCurrentCommitHash()).get(filename);
                if (headHash != null && !headHash.equals(hash)) {
                    modified.add(filename);
                }
            }
        }
        if (!modified.isEmpty()) {
            System.out.println("\nModified but not staged:");
            modified.forEach(s -> System.out.println("  " + s));
        }
    }

    // ---------- Log ----------
    private static void log() throws IOException {
        ensureRepoExists();
        String cur = getCurrentCommitHash();
        if (cur.isBlank()) {
            System.out.println("No commits yet.");
            return;
        }
        while (!cur.isBlank()) {
            File commitObj = new File(OBJECTS_DIR + File.separator + cur);
            if (!commitObj.exists()) break;
            String stored = FileUtils.readFileToString(commitObj, StandardCharsets.UTF_8);
            // stored has "commit <len>\0<commit-body>"
            int idx = stored.indexOf("\0");
            if (idx == -1) break;
            String body = stored.substring(idx + 1);
            // parse fields
            String[] parts = body.split("\n\n", 2); // header and message
            String header = parts[0];
            String message = parts.length > 1 ? parts[1].trim() : "";
            String authorLine = Arrays.stream(header.split("\n"))
                    .filter(l -> l.startsWith("author "))
                    .findFirst().orElse("author unknown");
            System.out.println("commit " + cur);
            System.out.println(authorLine);
            System.out.println();
            System.out.println("    " + message);
            System.out.println();

            // find parent
            Optional<String> parent = Arrays.stream(header.split("\n"))
                    .filter(l -> l.startsWith("parent "))
                    .map(l -> l.substring("parent ".length()))
                    .findFirst();
            cur = parent.orElse("");
        }
    }

    // ---------- Checkout (updated: detachHead flag) ----------
    private static void checkout(String commitHashOrRef, boolean detachHead) throws IOException {
        ensureRepoExists();
        String target = resolveHashOrRef(commitHashOrRef);
        if (target.isBlank()) {
            System.err.println("Commit/ref not found: " + commitHashOrRef);
            return;
        }
        Map<String, String> tree = getCommitTreeMap(target);
        if (tree.isEmpty()) {
            System.out.println("Nothing to checkout (commit empty).");
            return;
        }
        // overwrite working files with blob content
        for (Map.Entry<String, String> e : tree.entrySet()) {
            String filename = e.getKey();
            String blobHash = e.getValue();
            String blobStore = readObjectContent(blobHash);
            int idx = blobStore.indexOf("\0");
            String content = blobStore.substring(idx + 1);
            File targetFile = new File(filename);

            // ensure parent directories exist
            File parent = targetFile.getParentFile();
            if (parent != null) parent.mkdirs();

            FileUtils.writeStringToFile(targetFile, content, StandardCharsets.UTF_8);
            System.out.println("Restored: " + filename);
        }

        if (detachHead) {
            // detach HEAD to this commit
            FileUtils.writeStringToFile(new File(HEAD_FILE), target, StandardCharsets.UTF_8);
            System.out.println("HEAD now at " + target + " (detached)");
        }
    }

    // ---------- Branches ----------
    private static void branch(String name) throws IOException {
        ensureRepoExists();
        String headCommit = getCurrentCommitHash();
        if (headCommit.isBlank()) {
            System.err.println("No commits yet; create a commit first.");
            return;
        }
        File branchRef = new File(HEADS_DIR + File.separator + name);
        if (branchRef.exists()) {
            System.err.println("Branch already exists: " + name);
            return;
        }
        FileUtils.writeStringToFile(branchRef, headCommit, StandardCharsets.UTF_8);
        System.out.println("Created branch " + name + " at " + headCommit);
    }

    private static void listBranches() throws IOException {
        ensureRepoExists();
        File heads = new File(HEADS_DIR);
        String currentHeadRef = readHeadRef();
        String currentBranch = null;
        if (currentHeadRef.startsWith("refs/heads/")) currentBranch = currentHeadRef.substring("refs/heads/".length());
        File[] files = heads.listFiles();
        if (files == null) {
            System.out.println("No branches.");
            return;
        }
        for (File f : files) {
            String name = f.getName();
            String marker = name.equals(currentBranch) ? "*" : " ";
            System.out.println(marker + " " + name + " -> " + FileUtils.readFileToString(f, StandardCharsets.UTF_8).trim());
        }
    }

    private static void switchBranch(String name) throws IOException {
        ensureRepoExists();
        File branchRef = new File(HEADS_DIR + File.separator + name);
        if (!branchRef.exists()) {
            System.err.println("Branch does not exist: " + name);
            return;
        }

        // read the tip commit first
        String commitHash = FileUtils.readFileToString(branchRef, StandardCharsets.UTF_8).trim();

        if (!commitHash.isBlank()) {
            // restore files from the tip commit without detaching HEAD
            checkout(commitHash, false);

            // now point HEAD to the branch ref
            FileUtils.writeStringToFile(new File(HEAD_FILE), "refs/heads/" + name, StandardCharsets.UTF_8);
            System.out.println("Switched to branch " + name);
        } else {
            // no commits on this branch yet — still point HEAD to branch
            FileUtils.writeStringToFile(new File(HEAD_FILE), "refs/heads/" + name, StandardCharsets.UTF_8);
            System.out.println("Switched to branch " + name + " (no commits yet)");
        }
    }

    // ---------- Show commit contents ----------
    private static void show(String hashOrRef) throws IOException {
        ensureRepoExists();
        String resolved = resolveHashOrRef(hashOrRef);
        if (resolved.isBlank()) {
            System.err.println("Not found: " + hashOrRef);
            return;
        }
        String stored = readObjectContent(resolved);
        if (stored == null) {
            System.err.println("Object not found: " + resolved);
            return;
        }
        int idx = stored.indexOf("\0");
        String header = stored.substring(0, idx);
        String body = stored.substring(idx + 1);
        System.out.println(header);
        System.out.println("----");
        System.out.println(body);
    }

    // ---------- Utilities ----------
    private static void ensureRepoExists() {
        File root = new File(VCS_DIR);
        if (!root.exists()) {
            throw new RuntimeException("Not a MiniVCS repository (or any parent): run 'init' first");
        }
    }

    private static String readHeadRef() throws IOException {
        File head = new File(HEAD_FILE);
        if (!head.exists()) {
            throw new RuntimeException("HEAD missing; repo corrupted");
        }
        return FileUtils.readFileToString(head, StandardCharsets.UTF_8).trim();
    }

    private static String getCurrentCommitHash() throws IOException {
        String headRef = readHeadRef();
        if (headRef.startsWith("refs/heads/")) {
            String branch = headRef.substring("refs/heads/".length());
            File branchRef = new File(HEADS_DIR + File.separator + branch);
            if (!branchRef.exists()) return "";
            return FileUtils.readFileToString(branchRef, StandardCharsets.UTF_8).trim();
        } else {
            // HEAD directly contains a hash (detached)
            return headRef;
        }
    }

    private static Map<String, String> readIndexMap() throws IOException {
        File idx = new File(INDEX_FILE);
        Map<String, String> map = new HashMap<>();
        if (!idx.exists()) return map;
        List<String> lines = FileUtils.readLines(idx, StandardCharsets.UTF_8);
        for (String l : lines) {
            if (l.isBlank()) continue;
            String[] parts = l.split("\t", 2);
            if (parts.length == 2) map.put(parts[0], parts[1]);
        }
        return map;
    }

    private static List<String> getWorkingFiles() throws IOException {
        // list files in working dir (non-recursive for simplicity) except .myvcs
        File cwd = new File(".");
        List<String> names = new ArrayList<>();
        Files.walk(Paths.get("."))
                .filter(p -> Files.isRegularFile(p))
                .forEach(p -> {
                    String s = p.toString();
                    if (s.startsWith("./")) s = s.substring(2);
                    if (s.startsWith(VCS_DIR + File.separator) || s.equals(VCS_DIR)) return;
                    // skip target/ if present
                    if (s.startsWith("target" + File.separator)) return;
                    names.add(s);
                });
        return names;
    }

    private static String readObjectContent(String hash) throws IOException {
        if (hash == null || hash.isBlank()) return null;
        File obj = new File(OBJECTS_DIR + File.separator + hash);
        if (!obj.exists()) return null;
        return FileUtils.readFileToString(obj, StandardCharsets.UTF_8);
    }

    private static Map<String, String> getCommitTreeMap(String commitHash) throws IOException {
        Map<String, String> map = new HashMap<>();
        if (commitHash == null || commitHash.isBlank()) return map;
        String commitStore = readObjectContent(commitHash);
        if (commitStore == null) return map;
        int idx = commitStore.indexOf("\0");
        if (idx == -1) return map;
        String body = commitStore.substring(idx + 1);
        // header lines before blank line
        String[] parts = body.split("\n\n", 2);
        String header = parts[0];
        // find tree line: "tree <treehash>"
        Optional<String> treeLine = Arrays.stream(header.split("\n")).filter(l -> l.startsWith("tree ")).findFirst();
        if (treeLine.isEmpty()) return map;
        String treeHash = treeLine.get().substring("tree ".length()).trim();
        String treeStore = readObjectContent(treeHash);
        if (treeStore == null) return map;
        int tidx = treeStore.indexOf("\0");
        if (tidx == -1) return map;
        String tbody = treeStore.substring(tidx + 1);
        String[] lines = tbody.split("\n");
        for (String l : lines) {
            if (l.isBlank()) continue;
            String[] parts2 = l.split("\t", 2);
            if (parts2.length == 2) map.put(parts2[0], parts2[1]);
        }
        return map;
    }

    private static String resolveHashOrRef(String maybe) throws IOException {
        // if HEAD or refs/heads/<branch>
        if ("HEAD".equals(maybe)) {
            return getCurrentCommitHash();
        }
        if (maybe.startsWith("refs/heads/")) {
            String branch = maybe.substring("refs/heads/".length());
            File b = new File(HEADS_DIR + File.separator + branch);
            if (!b.exists()) return "";
            return FileUtils.readFileToString(b, StandardCharsets.UTF_8).trim();
        }
        // if it's a branch name
        File b = new File(HEADS_DIR + File.separator + maybe);
        if (b.exists()) {
            return FileUtils.readFileToString(b, StandardCharsets.UTF_8).trim();
        }
        // if it's an object hash
        File obj = new File(OBJECTS_DIR + File.separator + maybe);
        if (obj.exists()) return maybe;
        return "";
    }
}
