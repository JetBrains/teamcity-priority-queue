

package jetbrains.buildServer.serverSide.priority;

import java.util.ArrayList;
import jetbrains.buildServer.serverSide.BuildQueueEx;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class ServerListener extends BuildServerAdapter {

  private final BuildQueueEx myQueue;
  private final BuildQueuePriorityOrdering myStrategy;
  private final PriorityClassManagerImpl myPriorityClassManager;

  public ServerListener(@NotNull final EventDispatcher<BuildServerListener> dispatcher,
                        @NotNull final BuildQueueEx queue,
                        @NotNull final BuildQueuePriorityOrdering strategy,
                        @NotNull final PriorityClassManagerImpl priorityClassManager) {
    myQueue = queue;
    myStrategy = strategy;
    myPriorityClassManager = priorityClassManager;
    dispatcher.addListener(this);
  }

  @Override
  public void serverStartup() {
    myPriorityClassManager.init();
    myQueue.setOrderingStrategy(myStrategy);
  }

}