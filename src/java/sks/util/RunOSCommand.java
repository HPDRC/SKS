/*
 * RunOSCommand.java
 *
 */

package sks.util;

/**
 * Runs an OS command.
 * @author Ariel Cary
 */
public class RunOSCommand {
  // Creates a new instance of RunOSCommand
  public RunOSCommand() {
  }

  public int run(String[] cmd) {
    int exitVal = 1;
    try {
      Runtime rt = Runtime.getRuntime();
      Process proc = rt.exec(cmd);
      exitVal = proc.waitFor();
    } catch (Throwable t) {
      t.printStackTrace();
    }

    return exitVal;
  }
}