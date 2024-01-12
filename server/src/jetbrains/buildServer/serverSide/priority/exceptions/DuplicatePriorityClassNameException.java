

package jetbrains.buildServer.serverSide.priority.exceptions;

/**
 * @author dmitry.neverov
 */
public class DuplicatePriorityClassNameException extends PriorityClassException {
  public DuplicatePriorityClassNameException(String message) {
    super(message);
  }    
}