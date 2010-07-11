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

import java.io.File;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
final class Util {

  private static int BUILD_TYPE_SEQUENCE = 0;

  static SBuildType createBuildType(Mockery context, final String buildTypeId) {
    final SBuildType bt = context.mock(SBuildType.class, "bt" + BUILD_TYPE_SEQUENCE++);
    context.checking(new Expectations() {{
      allowing(bt).getBuildTypeId(); will(returnValue(buildTypeId));      
    }});
    return bt;
  }

  static Map<String, SBuildType> prepareBuildTypes(Mockery context, final ProjectManager projectManager, @NotNull String... btIds) {
    final Map<String, SBuildType> id2buildType = new HashMap<String, SBuildType>();
    for (String btId : btIds) {
      SBuildType buildType = createBuildType(context, btId);
      id2buildType.put(btId, buildType);
    }
    context.checking(new Expectations() {{
      for (Map.Entry<String, SBuildType> btEntry : id2buildType.entrySet()) {
        allowing(projectManager).findBuildTypeById(btEntry.getKey()); will(returnValue(btEntry.getValue()));
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
}