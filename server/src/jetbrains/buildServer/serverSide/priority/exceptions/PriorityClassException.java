

package jetbrains.buildServer.serverSide.priority.exceptions;

/**
 * @author dmitry.neverov
 */
public class PriorityClassException extends RuntimeException {
  public PriorityClassException(String message) {
    super(message);
  }

  public PriorityClassException(String message, Throwable cause) {
    super(message, cause);
  }

  public PriorityClassException(Throwable cause) {
    super(cause);
  }
}