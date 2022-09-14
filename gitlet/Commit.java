package gitlet;
import static gitlet.Repository.COMMITS;
import static gitlet.Utils.*;


import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/** Represents a gitlet commit object.
 *  @author beckettq
 */
public class Commit implements Serializable, Comparable<Commit> {
    /**
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The message of this Commit. */
    public String message;

    /** the parent commit*/
    public String parent;

    /** the tree tracking all committed files */
    public HashMap<String, String> map;

    /** Date of the commit */
    public Date date;

    /** the branch that the commit is located in */

    public String branch;

    /** the second parent of the commit. Only exists for commits that result from merges */

    public String parent2;

    public Commit(String message, String parent, Date date, HashMap<String, String> map, String branch) {
        this.message = message;
        this.parent = parent;
        this.map = map;
        this.date = date;
        this.branch = branch;
        parent2 = null;
    }

    public Commit(String message, String parent, String parent2, Date date, HashMap<String, String> map, String branch) {
        this.message = message;
        this.parent = parent;
        this.parent2 = parent2;
        this.date = date;
        this.map = map;
        this.branch = branch;
    }

    public static String getHash(Commit commit) {
        byte[] byteArray = Utils.serialize(commit);
        String hash = sha1(byteArray);
        return hash;
    }
    public String saveCommit(File directory) {
        String hash = getHash(this);
        File commitFile = join(directory, hash);
        try {
            commitFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writeObject(commitFile, this);
        return hash;
    }

    public String realDate() {
        return String.format("Date: %1$ta %1$tb %1$te %1$tT %1$tY %1$tz" , date);
    }

    @Override
    public String toString() {
        String s = String.format("===%ncommit %1$s%n%2$s%n%3$s%n%n", getHash(this), realDate(), message);
        return s;
    }

    public static String findFullID(String shortID) {
        List<String> commitList = Utils.plainFilenamesIn(COMMITS); //ordered
        int min = 0;
        int max = commitList.size() - 1;
        while (max >= min) {
            int middle = (min + max) / 2;
            String midString = commitList.get(middle);
            if (midString.startsWith(shortID)) {
                return commitList.get(middle);
            }
            if (shortID.compareTo(midString) < 0) {
                max = middle - 1;
            } else {
                min = middle + 1;
            }
        }
        return null;
    }

    public Commit getParent() {
        if (parent == null) {
            return null;
        }
        Commit parent = readObject(join(COMMITS, this.parent), Commit.class);
        return parent;
    }

    public Commit getParent2() {
        if (parent2 == null) {
            return null;
        }
        Commit parent2 = readObject(join(COMMITS, this.parent2), Commit.class);
        return parent2;
    }

    public boolean isModified(String filename, Commit split) {
        HashMap<String, String> splitMap = split.map;
        if (!map.containsKey(filename) && !splitMap.containsKey(filename)) {
            return false;
        }
        if (!map.containsKey(filename) && splitMap.containsKey(filename)) {
            return true;
        } else if (!splitMap.containsKey(filename) && map.containsKey(filename)) {
            return true;
        } else if (split.map.get(filename).equals(map.get(filename))) {
            // unmodified
            return false;
        } else {
            return true;
        }
    }
    public int compareTo(Commit other) {
        if (date.compareTo(other.date) < 0) {
            return -1;
        } else if (date.equals(other.date)) {
            return 0;
        } else {
            return 1;
        }
    }
}
