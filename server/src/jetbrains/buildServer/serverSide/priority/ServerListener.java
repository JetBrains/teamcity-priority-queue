/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
