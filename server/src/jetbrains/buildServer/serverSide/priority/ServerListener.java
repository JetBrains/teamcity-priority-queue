/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author dmitry.neverov
 */
public class ServerListener extends BuildServerAdapter {

  private SBuildServer myServer;
  private BuildQueuePriorityOrdering myStrategy;
  private PriorityClassManagerImpl myPriorityClassManager;

  public ServerListener(@NotNull final EventDispatcher<BuildServerListener> dispatcher,
                        @NotNull final SBuildServer server,
                        @NotNull final BuildQueuePriorityOrdering strategy,
                        @NotNull final PriorityClassManagerImpl priorityClassManager) {
    dispatcher.addListener(this);
    myServer = server;
    myStrategy = strategy;
    myPriorityClassManager = priorityClassManager;
  }

  @Override
  public void serverStartup() {
    myPriorityClassManager.init();
    myStrategy.addBuilds(myServer.getQueue().getItems(), new ArrayList<SQueuedBuild>());
    ((BuildQueueEx) myServer.getQueue()).setOrderingStrategy(myStrategy);
  }

}
