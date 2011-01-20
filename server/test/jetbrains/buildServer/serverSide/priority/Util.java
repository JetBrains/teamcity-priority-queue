/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;

import java.io.File;
import java.util.*;

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
    final Set<Set<String>> combinations = allCombinationsOf(Arrays.asList(btIds));
    context.checking(new Expectations() {{
      for (Map.Entry<String, SBuildType> btEntry : id2buildType.entrySet()) {
        allowing(projectManager).findBuildTypeById(btEntry.getKey()); will(returnValue(btEntry.getValue()));
      }
      allowing(projectManager).getAllBuildTypes(); will(returnValue(new ArrayList<SBuildType>(id2buildType.values())));
      for (Set<String> comb : combinations) {
        allowing(projectManager).findBuildTypes(comb); will(returnValue(getValuesForKeys(id2buildType, comb)));
      }
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

  private static List<SBuildType> getValuesForKeys(Map<String, SBuildType> map, Set<String> keys) {
    List<SBuildType> result = new ArrayList<SBuildType>();
    for (String key : keys) {
      result.add(map.get(key));
    }
    return result;
  }

  private static Set<Set<String>> allCombinationsOf(List<String> l) {
    if (l.isEmpty()) {
      Set<Set<String>> result = new HashSet<Set<String>>();
      result.add(new HashSet<String>());
      return result;
    } else {
      String head = head(l);
      Set<Set<String>> result = allCombinationsOf(tail(l));
      Set<Set<String>> resultCopy = new HashSet<Set<String>>(result);
      for (Set<String> s : resultCopy) {
        Set<String> copy = new HashSet<String>(s);
        copy.add(head);
        result.add(copy);
      }
      return result;
    }
  }

  private static <T> T head(List<T> l) {
    return l.get(0);
  }

  private static <T> List<T> tail(List<T> l) {
    return l.subList(1, l.size());
  }
}
