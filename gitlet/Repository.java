package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.*;


/** Represents a gitlet repository.
 *
 *
 *  @author beckettq
 */
public class Repository implements Serializable {

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /** A directory for storing commits */
    public static final File COMMITS = join(GITLET_DIR, "commits");

    /** The index directory for staging changes*/
    public static final File INDEX = join(GITLET_DIR, "index");

    /** a file for saving the repo's information */
    public static final File REPO = join(GITLET_DIR, "repo");

    /** a file for storing one's blobs. */
    public static final File BLOBS = join(GITLET_DIR, "blobs");

    /**The file that stores the indexmap.*/
    public static final File INDEXMAP = join(INDEX, "indexMap");

    /** The file that stores what files will be removed */
    public static final File RMSET = join(INDEX, "rmmap");

    /** the unix epoch date */
    public static final Date UNIX_EPOCH =  new Date(0);
    //00:00:00 UTC, Thursday, 1 January 1970

    /** the HEAD branch, which refers to the current branch */
    private String HEAD;

    /** a mapping of branch names to their latest commits */
    private HashMap<String, String> branchMap;

    public Repository() {
        if (GITLET_DIR.exists()) {
            String e = "A Gitlet version-control system already exists in the current directory.";
            System.out.println(e);
            return;
        } else {
            GITLET_DIR.mkdir();
            COMMITS.mkdir();
            HashMap<String, String> initMap = new HashMap<>();
            Commit initial = new Commit("initial commit", null, UNIX_EPOCH, initMap, "master");
            String firstCommit = initial.saveCommit(COMMITS);
            HEAD = firstCommit;
            branchMap = new HashMap<>();
            branchMap.put("master", firstCommit);
            HEAD = "master";
            INDEX.mkdir();
            try {
                INDEXMAP.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                RMSET.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            HashMap<String, String> iMap = new HashMap<>();
            writeObject(INDEXMAP, iMap);
            writeObject(RMSET, new HashSet<String>());
            BLOBS.mkdir();
            try {
                REPO.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeObject(REPO, this);
        }
    }

    public void saveRepo() {
        writeObject(REPO, this);
    }

    public String getHeadCommit() {
        return branchMap.get(HEAD);
    }

    public static Repository getRepo() {
        return readObject(REPO, Repository.class);
    }

    public static void addFile(String name) {
        Repository repo = getRepo();
        File addFile = join(CWD, name);
        if (!addFile.exists()) {
            System.out.println("File does not exist.");
            return;
        }
        // read the removeset and the indexmapping
        HashSet<String> rmMap = readObject(RMSET, HashSet.class);
        HashMap<String, String> iMap = readObject(INDEXMAP, HashMap.class);
        String fileHash = sha1(readContents(addFile)); // get the file's hash.
        Commit parent = readObject(join(COMMITS, repo.getHeadCommit()), Commit.class);
        //check if the added file already exists in the parent's filemap
        //handle the cases where the parent or the parent map is null.
        if (!(parent == null) && !(parent.map == null)) {
            HashMap<String, String> parentMap = parent.map;
            // if the file exists in the parent commit, remove the file from the rmset, if it is there, and then do nothing.
            if (parentMap.containsKey(name) && (parentMap.get(name).equals(fileHash))) {
                if (rmMap.contains(name)) {
                    rmMap.remove(name);
                    writeObject(RMSET, rmMap);
                    return;
                }
                return;
            }
        }
        // now if we aren't in a special case, we copy the file to the index and add it to the iMap.
        File destination = join(INDEX, fileHash);
        if (destination.exists()) {
            if (rmMap.contains(name)) {
                rmMap.remove(name);
                writeObject(RMSET, rmMap);
            }
            iMap.put(name, fileHash);
            writeObject(INDEXMAP, iMap);
            writeContents(destination, readContents(addFile));
            return;
        }
        try {
            destination.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writeContents(destination, readContents(addFile));
        iMap.put(name, fileHash);
        writeObject(INDEXMAP, iMap);
    }

    public static void removeFile(String name)  {
        HashMap<String, String> iMap = readObject(INDEXMAP, HashMap.class);
        if (iMap.containsKey(name)) {
            iMap.remove(name);
            writeObject(INDEXMAP, iMap);
            return;
        } else {
            File parent = join(COMMITS, getRepo().getHeadCommit());
            Commit parentCommit = readObject(parent, Commit.class);
            if(!parentCommit.map.containsKey(name)) {
                System.out.println("No reason to remove the file");
                return;
            }
        }
        HashSet<String> rmset = readObject(RMSET, HashSet.class);
        rmset.add(name);
        writeObject(RMSET, rmset);
        File toRemove = join(CWD, name);
        if (toRemove.exists()) {
            toRemove.delete();
        }
    }

    public static void makeNewCommit(String message) {
        HashMap<String, String> iMap = readObject(INDEXMAP, HashMap.class);
        Repository repo = readObject(REPO, Repository.class);
        HashSet<String> RMMap = readObject(RMSET, HashSet.class);
        if (iMap.isEmpty() && RMMap.isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        }
        //copies every file to be added into blobs.
        for (String fileName : iMap.keySet()) {
            File source = join(INDEX, iMap.get(fileName));
            File dest = join(BLOBS, iMap.get(fileName));
            if (!RMMap.contains(fileName)) {
                if (!dest.exists()) {
                    try {
                        dest.createNewFile(); //nooo
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    writeContents(dest, readContents(source));
                }
            }
            source.delete();
        }
        String parent = repo.getHeadCommit();
        Commit newCommit = new Commit(message, parent, new Date(), repo.newCommitMap(), repo.HEAD);
        String newCommitHash = newCommit.saveCommit(COMMITS);
        repo.branchMap.put(repo.HEAD, newCommitHash);
        writeObject(REPO, repo);
        writeObject(INDEXMAP, new HashMap<String, String>());
        writeObject(RMSET, new HashSet<String>());
    }

    private HashMap newCommitMap() {
        String parent = getHeadCommit();
        Commit parentCommit = readObject(join(COMMITS, parent), Commit.class);
        HashMap<String, String> iMap = readObject(INDEXMAP, HashMap.class);
        HashSet<String> rmMap = readObject(RMSET, HashSet.class);
        HashMap<String, String> parentMap = parentCommit.map;
        if (parentMap == null) {
            return iMap;
        }
        Set<String> keySet = new HashSet<>(parentMap.keySet());
        for (String fileName : keySet) { //remove based on rmset
            if (rmMap.contains(fileName)) {
                parentMap.remove(fileName);
            }
        }
        for (String fileName : iMap.keySet()) { //add based on index map
            parentMap.put(fileName, iMap.get(fileName));
        }
        return parentMap;
    }

    public static void log() {
        Repository repo = readObject(REPO, Repository.class);
        Commit displayCommit = readObject(join(COMMITS, repo.getHeadCommit()), Commit.class);
        while (displayCommit != null) {
            System.out.print(displayCommit);
            if (displayCommit.parent == null) {
                return;
            }
            displayCommit = readObject(join(COMMITS, displayCommit.parent), Commit.class);
        }
    }

    public static void status() {
        Repository repo = getRepo();
        System.out.println("=== Branches ===");
        System.out.println("*" + repo.HEAD);
        for (String branch : repo.branchMap.keySet()) {
            if (!branch.equals(repo.HEAD)) {
                System.out.print(branch + "\n");
            }
        }
        System.out.print("\n");
        System.out.println("=== Staged Files ===");
        for (Object fileName : readObject(INDEXMAP, HashMap.class).keySet()) {
            System.out.println(fileName);
        }
        System.out.print("\n");
        System.out.println("=== Removed Files ===");
        for (Object fileName : readObject(RMSET, HashSet.class)) {
            System.out.println(fileName);
        }
        System.out.print("\n");
        System.out.println("=== Modifications Not Staged For Commit ===\n");
        System.out.println("=== Untracked Files ===\n");

    }

    public static void globalLog() {
        for (String file : Utils.plainFilenamesIn(COMMITS)) {
            File src = join(COMMITS, file);
            Commit commit = readObject(src, Commit.class);
            System.out.print(commit);
        }
    }

    public static void find(String msg) {
        boolean found = false;
        for (String file : Utils.plainFilenamesIn(COMMITS)) {
            File src = join(COMMITS, file);
            Commit commit = readObject(src, Commit.class);
            if (msg.equals(commit.message)) {
                System.out.println(file);
                found = true;
            }
        }
        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    public static void checkoutByCommit(String id, String filename) {
        File commitFile = join(COMMITS, id);
        if (!commitFile.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }
        Commit commit = readObject(commitFile, Commit.class);
        if (!commit.map.containsKey(filename)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        String srcHash = commit.map.get(filename);
        File src = join(BLOBS, srcHash);
        File dest = join(CWD, filename);
        if (!dest.exists()) {
            try {
                dest.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        writeContents(dest, readContents(src));
    }

    public static void checkoutSingle(String filename) {
        File dest = join(CWD, filename);
        if (!dest.exists()) {
            try {
                dest.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        Repository repo = readObject(REPO, Repository.class);
        Commit head = readObject(join(COMMITS, repo.getHeadCommit()), Commit.class);
        String filehash = head.map.get(filename);
        File src = join(BLOBS, filehash);
        if (!src.exists()) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        writeContents(dest, readContents(src));
    }

    public static void branch(String branchName) {
        Repository repo = getRepo();
        if (branchName == repo.HEAD) {
            return;
        }
        if(repo.branchMap.containsKey(branchName)) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        repo.branchMap.put(branchName, repo.getHeadCommit());
        repo.saveRepo();
    }

    public static void checkoutBranch(String branchName) {
        Repository repo = getRepo();
        if (!repo.branchMap.containsKey(branchName)) {
            System.out.println("No such branch exists.");
            return;
        }
        if (branchName.equals(repo.HEAD)) {
            System.out.println("No need to checkout the current branch.");
            return;
        }
        Commit branchCommit = readObject(join(COMMITS, repo.branchMap.get(branchName)), Commit.class);
        copyToCWD(branchCommit);
        repo.HEAD = branchName;
        repo.saveRepo();
    }

    public static void removeBranch(String branchName) {
        Repository repo = getRepo();
        if (!repo.branchMap.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (repo.HEAD.equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        repo.branchMap.remove(branchName);
        repo.saveRepo();
    }

    public static void reset(String commitID) {
        if (commitID.length() < 40) {
            commitID = Commit.findFullID(commitID);
        }
        Repository repo = getRepo();
        File src = join(COMMITS, commitID);
        if (!src.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }
        Commit ourCommit = readObject(src, Commit.class);
        copyToCWD(ourCommit);
        repo.HEAD = ourCommit.branch;
        repo.branchMap.put(ourCommit.branch, commitID);
        writeObject(INDEXMAP, new HashMap<String, String>());
        writeObject(RMSET, new HashSet<String>());
        repo.saveRepo();
    }

    private static void copyToCWD(Commit commit) {
        Repository repo = getRepo();
        List<String> cwdList = plainFilenamesIn(CWD);
        Commit headCommit = readObject(join(COMMITS, repo.getHeadCommit()), Commit.class);
        for (String fileName : cwdList) {
            if (commit.map.containsKey(fileName) && !headCommit.map.containsKey(fileName)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }
        String head = repo.getHeadCommit();
        HashMap<String, String> HEADMap = readObject(join(COMMITS, head), Commit.class).map;
        HashMap<String, String> commitMap = commit.map;
        for (String fileName : HEADMap.keySet()) {
            if (!commitMap.containsKey(fileName)) {
                File toDelete = join(CWD, fileName);
                if (toDelete.exists()) {
                    toDelete.delete();
                }
            }
        }
        for (String fileName : commitMap.keySet()) {
            File dest = join(CWD, fileName);
            File src = join(BLOBS, commitMap.get(fileName));
            if (!dest.exists()) {
                try {
                    dest.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            writeContents(dest, readContents(src));
        }
    }

    public static Commit lastCommonAncestor(Commit commit1, Commit commit2) {
        HashSet<Commit> ancestors1 = new HashSet<>();
        HashSet<Commit> ancestors2 = new HashSet<>();
        ancestors1.add(commit1);
        ancestors2.add(commit2);
        while (!commit1.toString().equals(commit2.toString())) {
            for (Commit curr : ancestors1 ) {
                if (ancestors2.contains(curr)) {
                    return curr;
                }
            }
            commit1 = Collections.max(ancestors1);
            commit2 = Collections.max(ancestors2);
            if (commit1.date.compareTo(commit2.date) < 0) {
                ancestors2.remove(commit2);
                if (commit2.parent2 != null) {
                    ancestors2.add(commit2.getParent2());
                }
                ancestors2.add(commit2.getParent());
            } else {
                ancestors1.remove(commit1);
                if (commit1.parent2 != null) {
                    ancestors1.add(commit1.getParent2());
                }
                ancestors1.add(commit1.getParent());
            }
        }
        return commit1;
    }

    private static int lcaCheck(Commit head, Commit other) {
        boolean headMoved = false;
        boolean otherMoved = false;
        while (!head.toString().equals(other.toString())) {
            if (head.date.compareTo(other.date) < 0) {
                other = other.getParent();
                otherMoved = true;
            } else {
                head = head.getParent();
                headMoved = true;
            }
        }
        if (headMoved && otherMoved) {
            return 0;
        } else if (headMoved && !otherMoved) {
            return 1;
        } else if (!headMoved && otherMoved) {
            return 2;
        } else {
            return 3;
        }
    }

    public static void merge(String otherBranch) throws IOException {
        Repository repo = getRepo();
        if (mergeErrorCheck(otherBranch)) { //order
            return;
        }
        boolean conflict = false;
        Commit headCommit = readObject(join(COMMITS, repo.getHeadCommit()), Commit.class);
        String otherID = repo.branchMap.get(otherBranch);
        Commit otherCommit = readObject(join(COMMITS, otherID), Commit.class);
        HashMap<String, String> headMap = headCommit.map;
        HashMap<String, String> otherMap = otherCommit.map;
        Commit lca = Repository.lastCommonAncestor(headCommit, otherCommit); // update lca
        List<String> fileList = Utils.plainFilenamesIn(CWD);
        for (String fileName : fileList) {
            if (!headMap.containsKey(fileName) && otherCommit.isModified(fileName, lca)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }
        int lcaCheck = lcaCheck(headCommit, otherCommit);
        switch(lcaCheck) {
            case 1:
                System.out.println("Given branch is an ancestor of the current branch.");
                return;
            case 2:
                checkoutBranch(otherBranch);
                System.out.println("Current branch fast-forwarded.");
                return;
            default:
                break;
        }

        for (String file : headMap.keySet()) {
            boolean headModified = headCommit.isModified(file, lca);
            boolean otherModified = otherCommit.isModified(file, lca);
            if (handleMergeFile(file, headCommit, otherID, lca)) {
                conflict = true;
            }
            otherMap.remove(file);
        }
        for (String filename : otherMap.keySet()) {
            if (handleMergeFile(filename, headCommit, otherID, lca)) {
                conflict = true;
            }
        }
        Repository.makeMergeCommit(repo.HEAD, otherBranch);
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    private static boolean handleMergeFile(String fileName, Commit headCommit, String otherID, Commit lca) throws IOException {
        Commit otherCommit = readObject(join(COMMITS, otherID),  Commit.class);
        boolean headModified = headCommit.isModified(fileName, lca);
        boolean otherModified = otherCommit.isModified(fileName, lca);
        boolean conflict = false;
        HashMap<String, String> headMap = headCommit.map;
        HashMap<String, String> otherMap = otherCommit.map;
        if (otherModified && !headModified) {
            if (!otherMap.containsKey(fileName)) {
                Repository.removeFile(fileName);
            } else {
                checkoutByCommit(otherID, fileName);
                addFile(fileName);
            }
        } else if (otherModified && headModified) {
            conflict = true;
            byte[] otherContents;
            byte[] headContents;
            if (!otherMap.containsKey(fileName)) {
                otherContents = new byte[0];
            } else {
                File otherFile = join(BLOBS, otherMap.get(fileName));
                otherContents = readContents(otherFile);
            }
            if (!headMap.containsKey(fileName)) {
                headContents = new byte[0];
            } else {
                File headFile = join(BLOBS, headMap.get(fileName));
                headContents = readContents(headFile);
            }
            File dest = join(CWD, fileName);
            if (!dest.exists()) {
                dest.createNewFile();
            }
            writeContents(dest, "<<<<<<< HEAD\n", headContents, "=======\n", otherContents, ">>>>>>>\n");
            addFile(dest.getName());
        }
        return conflict;
    }

    private static void makeMergeCommit(String head, String other) {
        String message = "Merged " + other + " into " + head + ".";
        HashMap<String, String> iMap = readObject(INDEXMAP, HashMap.class);
        Repository repo = readObject(REPO, Repository.class);
        HashSet<String> RMMap = readObject(RMSET, HashSet.class);
        if (iMap.isEmpty() && RMMap.isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        }
        //copies every file to be added into blobs.
        for (String fileName : iMap.keySet()) {
            File source = join(INDEX, iMap.get(fileName));
            File dest = join(BLOBS, iMap.get(fileName));
            if (!RMMap.contains(fileName)) {
                if (!dest.exists()) {
                    try {
                        dest.createNewFile(); //nooo
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    writeContents(dest, readContents(source));
                }
            }
            source.delete();
        }
        String parent = repo.getHeadCommit();
        String parent2 = repo.branchMap.get(other);
        Commit newCommit = new Commit(message, parent, parent2, new Date(), repo.newCommitMap(), repo.HEAD);
        String newCommitHash = newCommit.saveCommit(COMMITS);
        repo.branchMap.put(repo.HEAD, newCommitHash);
        writeObject(REPO, repo);
        writeObject(INDEXMAP, new HashMap<String, String>());
        writeObject(RMSET, new HashSet<String>());
    }

    private static boolean mergeErrorCheck(String branchName) {
        Repository repo = getRepo();
        HashMap<String, String> iMap = readObject(INDEXMAP, HashMap.class);
        HashSet<String> rmSet = readObject(RMSET, HashSet.class);
        if (!iMap.isEmpty() || !rmSet.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return true;
        }
        if (!repo.branchMap.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return true;
        }
        if (repo.HEAD.equals(branchName)) {
            System.out.println("Cannot merge a branch with itself.");
            return true;
        }
        return false;
    }

}
