/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.scheduler.Time;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.cleanup.CleanupCannotBeStartedException;
import jetbrains.buildServer.serverSide.cleanup.CleanupProcessState;
import jetbrains.buildServer.serverSide.cleanup.ServerCleanupManager;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;

/**
 * @author dmitry.neverov
 */
final class Util {

  private static int BUILD_TYPE_SEQUENCE = 0;

  static SBuildType createBuildType(Mockery context, final String buildTypeId) {
    return createBuildType(context, buildTypeId, buildTypeId);
  }

  static SBuildType createBuildType(Mockery context, final String buildTypeId, final String externalBuildTypeId) {
    final SBuildType bt = context.mock(SBuildType.class, "bt" + BUILD_TYPE_SEQUENCE++);
    context.checking(new Expectations() {{
      allowing(bt).getBuildTypeId(); will(returnValue(buildTypeId));
      allowing(bt).getExternalId(); will(returnValue(externalBuildTypeId));
    }});
    return bt;
  }

  static Map<String, SBuildType> prepareBuildTypes(Mockery context, final ProjectManager projectManager, @NotNull String... btIds) {
    Map<String, String> internal2externalId = new HashMap<String, String>();
    for (String btId : btIds) {
      internal2externalId.put(btId, btId);
    }
    return prepareBuildTypes(context, projectManager, internal2externalId);
  }

  static Map<String, SBuildType> prepareBuildTypes(Mockery context, final ProjectManager projectManager, @NotNull final Map<String, String> btIds) {
    final Map<String, SBuildType> id2buildType = new HashMap<String, SBuildType>();
    for (Map.Entry<String, String> e : btIds.entrySet()) {
      String btId = e.getKey();
      String externalId = e.getValue();
      SBuildType buildType = createBuildType(context, btId, externalId);
      id2buildType.put(btId, buildType);
    }
    context.checking(new Expectations() {{
      for (Map.Entry<String, SBuildType> btEntry : id2buildType.entrySet()) {
        allowing(projectManager).findBuildTypeById(btEntry.getKey()); will(returnValue(btEntry.getValue()));
        allowing(projectManager).findBuildTypeByExternalId(btIds.get(btEntry.getKey())); will(returnValue(btEntry.getValue()));
      }
      allowing(projectManager).getAllBuildTypes(); will(returnValue(new ArrayList<SBuildType>(id2buildType.values())));
    }});
    return id2buildType;
  }

  static File getTestDataDir() {
    File f = new File("svnrepo/priority-queue/server/testData");
    if (f.isDirectory()) {
      return f;
    }
    return new File("server/testData");
  }

  static ServerPaths getServerPaths(File rootDir) {
    return new ServerPaths(rootDir);
  }

  static class MockServerCleanupManager implements ServerCleanupManager {
    public void setCleanupStartTime(@Nullable final Time time) {throw new UnsupportedOperationException();}

    public void setCleanupEnabled(final boolean enabled) {
    }

    public boolean isCleanupEnabled() {
      return false;
    }

    public boolean isRunningCriticalSection() {
      return false;
    }

    public void setMaxCleanupDuration(final int durationSecs) {}

    public int getMaxCleanupDuration() { return 0; }

    @NotNull
    public Time getCleanupStartTime() {throw new UnsupportedOperationException();}
    @NotNull
    public CleanupProcessState getCleanupState() {throw new UnsupportedOperationException();}
    public void startCleanup() throws CleanupCannotBeStartedException {throw new UnsupportedOperationException();}

    public void stopCleanup(@Nullable final SUser userPerformingAction) {}

    public boolean isCleanupCanBeStarted() {throw new UnsupportedOperationException();}
    public boolean executeWithInactiveCleanup(@NotNull final Runnable runnable, final boolean waitTillCleanupFinished) {
      runnable.run();
      return true;
    }
  }
}
