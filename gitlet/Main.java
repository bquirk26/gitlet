package gitlet;

import java.io.IOException;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
 */
public class Main {

    public static boolean checkOperandCount(int correct,  int given) {
        if (given != correct) {
            System.out.println("Incorrect operands.");
            return false;
        }
        if (!Repository.REPO.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            return false;
        }
        return true;
    }

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        // TODO: what if args is empty?
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }
        String firstArg = args[0];
        switch(firstArg) {
            case "init":
                // TODO: handle the `init` command
                if (args.length > 1) {
                    System.out.println("Incorrect operands.");
                    break;
                }
                new Repository();
                break;
            case "add":
                // TODO: handle the `add [filename]` command
                if (!checkOperandCount(2, args.length)) {
                    break;
                }
                String filename = args[1];
                Repository.addFile(filename);
                break;
            // TODO: FILL THE REST IN
            case "commit":
                if (!checkOperandCount(2, args.length)) {
                    break;
                }
                if (args[1].equals("")) {
                    System.out.println("Please enter a commit message.");
                    break;
                }
                String message = args[1];
                Repository.makeNewCommit(message);
                break;
            case "log":
                if (!checkOperandCount(1, args.length)) {
                    break;
                }
                Repository.log();
                break;
            case "global-log":
                if (!checkOperandCount(1, args.length)) {
                    break;
                }
                Repository.globalLog();
                break;
            case "find":
                if (!checkOperandCount(2, args.length)) {
                    break;
                }
                Repository.find(args[1]);
                break;
            case "checkout":
                //TODO: implement for not just filenames
                if (args.length == 1 || args.length > 4) {
                    checkOperandCount(1, 2);
                    break;
                }
                if (args.length == 2) {
                    Repository.checkoutBranch(args[1]);
                    break;
                } else if (args.length == 3) {
                    if (!args[1].equals("--")) {
                        checkOperandCount(1, 2);
                        break;
                    }
                    String fileName = args[2];
                    Repository.checkoutSingle(fileName);
                    break;
                } else if (args.length == 4) {
                    if (!args[2].equals("--")) {
                        checkOperandCount(1, 2);
                        break;
                    }
                    String id = args[1];
                    if (id.length() < 40) {
                        id = Commit.findFullID(id);
                    }
                    Repository.checkoutByCommit(id, args[3]);
                    break;
                }
                break;
            case "rm":
                if (!checkOperandCount(2, args.length)) {
                    break;
                }
                Repository.removeFile(args[1]);
                break;
            case "status":
                if (!checkOperandCount(1, args.length)) {
                    break;
                }
                Repository.status();
                break;
            case "branch":
                if (!checkOperandCount(2, args.length)) {
                    break;
                }
                Repository.branch(args[1]);
                break;
            case "reset":
                if (!checkOperandCount(2, args.length)) {
                    break;
                }
                Repository.reset(args[1]);
                break;
            case "rm-branch":
                if (!checkOperandCount(2, args.length)) {
                    break;
                }
                Repository.removeBranch(args[1]);
                break;
            case "merge":
                if(!checkOperandCount(2, args.length)) {
                    break;
                }
                try {
                    Repository.merge(args[1]);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                System.out.println("No command with that name exists.");
                break;
        }
    }
}
